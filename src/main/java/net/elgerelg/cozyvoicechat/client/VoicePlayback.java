package net.elgerelg.cozyvoicechat.client;

import net.elgerelg.cozyvoicechat.CozyVoiceProtocol;
import net.elgerelg.cozyvoicechat.OpusCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import io.github.jaredmdobson.concentus.OpusDecoder;

import javax.sound.sampled.SourceDataLine;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Движок воспроизведения: jitter-буферы на каждого говорящего -> Opus decode ->
 * микширование с громкостью по расстоянию и стерео-панорамой -> SourceDataLine.
 */
public final class VoicePlayback implements Runnable {

    private static final int MAX_QUEUE = 8;      // ~160 мс джиттер-буфер
    /**
     * Сколько кадров копим, прежде чем начать играть канал. Без запаса очередь
     * висит около нуля, и любое дрожание сети даёт разрыв на каждом кадре.
     */
    private static final int PREBUFFER = 3;      // ~60 мс
    /** Сколько подряд пропавших кадров достраиваем PLC, прежде чем замолчать. */
    private static final int MAX_CONCEAL = 5;    // ~100 мс
    private static final long CHAN_TTL_MS = 2000;
    private static final int RAMP = 48;          // ~1 мс сглаживания на стыке

    private static VoicePlayback instance;

    /** Пиковый уровень последнего декодированного кадра каждого говорящего (для иконок над головой). */
    private record Level(float value, long atMs) {}
    private static final Map<UUID, Level> LEVELS = new ConcurrentHashMap<>();
    private static final long LEVEL_TTL_MS = 250L;

    private static void noteLevel(UUID sender, short[] pcm, int n) {
        int peak = 0;
        for (int i = 0; i < n; i++) {
            int v = Math.abs(pcm[i]);
            if (v > peak) peak = v;
        }
        LEVELS.put(sender, new Level(Math.min(1f, peak / 32768f), System.currentTimeMillis()));
    }

    /** Громкость говорящего 0..1 по последнему проигранному кадру; 0, если давно молчит. */
    public static float levelOf(UUID sender) {
        if (sender == null) return 0f;
        Level l = LEVELS.get(sender);
        if (l == null) return 0f;
        if (System.currentTimeMillis() - l.atMs() > LEVEL_TTL_MS) {
            LEVELS.remove(sender, l);
            return 0f;
        }
        return l.value();
    }

    private Thread thread;
    private volatile boolean running = false;
    private volatile String error = null;
    private SourceDataLine line;

    /**
     * Ключ потока: один игрок может одновременно звучать в разных категориях
     * (например, сам по себе и через камеру), а смешивать их в один декодер
     * нельзя — Opus держит состояние потока.
     */
    private record ChanKey(UUID sender, byte category) {}

    private final Map<ChanKey, Chan> channels = new ConcurrentHashMap<>();
    private final MonitorRing monitor = new MonitorRing(CozyVoiceProtocol.FRAME_SAMPLES * 8);

    private static final class Chan {
        final OpusDecoder dec;
        final ArrayDeque<byte[]> queue = new ArrayDeque<>();
        volatile long last = System.currentTimeMillis();
        volatile float gain = 1f;
        boolean flowing = false;
        int concealed = 0;

        Chan(OpusDecoder dec) { this.dec = dec; }
    }

    /**
     * Кольцевой буфер монитора микрофона.
     *
     * Раньше монитор шёл кадрами по 20 мс, и когда часы микрофона и динамиков
     * расходились, кадр просто пропадал — получался щелчок. Здесь поток
     * непрерывный по сэмплам, а дрейф гасится добавлением/выбрасыванием
     * одного сэмпла за кадр: на слух это незаметно, разрывов не возникает.
     */
    private static final class MonitorRing {
        private final short[] buf;
        private int read, write, size;

        MonitorRing(int capacity) {
            buf = new short[capacity];
        }

        synchronized void write(short[] pcm, int n) {
            for (int i = 0; i < n; i++) {
                if (size == buf.length) {   // переполнение: выбрасываем самое старое
                    read = (read + 1) % buf.length;
                    size--;
                }
                buf[write] = pcm[i];
                write = (write + 1) % buf.length;
                size++;
            }
        }

        synchronized int size() { return size; }

        synchronized void clear() { read = write = size = 0; }

        /**
         * Читает n сэмплов, подстраивая темп под накопленную задержку.
         * Возвращает, сколько сэмплов реально прочитано (0 = тишина).
         */
        synchronized int read(short[] out, int n, int targetFill) {
            if (size < n) return 0;

            // Дрейф: очередь растёт — проглатываем лишний сэмпл, тает — дублируем.
            int drift = 0;
            if (size > targetFill + n) drift = 1;
            else if (size < targetFill - n / 2) drift = -1;

            for (int i = 0; i < n; i++) {
                if (drift > 0 && i == n / 2 && size > 1) {
                    read = (read + 1) % buf.length;
                    size--;
                    drift = 0;
                }
                if (drift < 0 && i == n / 2) {
                    out[i] = i > 0 ? out[i - 1] : buf[read];
                    drift = 0;
                    continue;
                }
                out[i] = buf[read];
                read = (read + 1) % buf.length;
                size--;
            }
            return n;
        }
    }

    private VoicePlayback() {}

    public static synchronized void ensureStarted() {
        if (instance == null || !instance.running) {
            instance = new VoicePlayback();
            instance.running = true;
            instance.thread = new Thread(instance, "UMC-Voice-Playback");
            instance.thread.setDaemon(true);
            instance.thread.start();
        }
    }

    public static synchronized void stop() {
        if (instance != null) {
            instance.running = false;
            if (instance.line != null) {
                try { instance.line.close(); } catch (Exception ignored) {}
            }
            instance = null;
        }
    }

    /** Перезапуск (смена устройства вывода). */
    public static synchronized void restart() {
        boolean was = instance != null && instance.running;
        stop();
        if (was || CozyVoiceClient.isActive() || CozyVoiceSettings.get().monitor) ensureStarted();
    }

    public static void addVoice(UUID sender, byte category, float gain, byte[] data, int off, int len) {
        VoicePlayback i = instance;
        if (i == null || !i.running) return;
        // Глушилка: пакет уже учтён в списке говорящих (голограммы над головами
        // остаются), но в звук он не попадает и декодер зря не гоняем.
        if (CozyVoiceSettings.get().deafened) return;
        Chan c = i.channels.computeIfAbsent(new ChanKey(sender, category), k -> {
            OpusDecoder d = OpusCodec.newDecoder();
            return d == null ? null : new Chan(d);
        });
        if (c == null) return;
        c.gain = gain;
        byte[] copy = new byte[len];
        System.arraycopy(data, off, copy, 0, len);
        synchronized (c.queue) {
            if (c.queue.size() >= MAX_QUEUE) c.queue.poll();
            c.queue.offer(copy);
        }
        c.last = System.currentTimeMillis();
    }

    /** Сбросить накопленный монитор: остаток буфера иначе доиграет после выключения. */
    public static void stopMonitor() {
        VoicePlayback i = instance;
        if (i != null) i.monitor.clear();
    }

    /** Локальный монитор микрофона (тест). */
    public static void feedMonitor(short[] pcm, int n) {
        VoicePlayback i = instance;
        if (i == null || !i.running) return;
        i.monitor.write(pcm, n);
    }

    public static String error() {
        return instance == null ? null : instance.error;
    }

    @Override
    public void run() {
        try {
            line = AudioDevices.openOutput(CozyVoiceSettings.get().outputDevice);
            line.start();
            error = null;
        } catch (Throwable t) {
            error = String.valueOf(t);
            running = false;
            return;
        }

        int frames = CozyVoiceProtocol.FRAME_SAMPLES;
        int[] acc = new int[frames * 2];
        short[] mono = new short[frames];
        short[] mon = new short[frames];
        byte[] out = new byte[frames * 4];
        // Держим ~2 кадра запаса: меньше — рвётся на дрейфе, больше — заметная задержка.
        int monitorTarget = frames * 2;
        boolean monitorPrimed = false;
        boolean monitorFlowing = false;

        while (running) {
            Arrays.fill(acc, 0);
            double master = CozyVoiceSettings.get().masterVolume;

            int queued = 0;
            for (Map.Entry<ChanKey, Chan> e : channels.entrySet()) {
                Chan c = e.getValue();
                byte[] pkt;
                synchronized (c.queue) {
                    int depth = c.queue.size();
                    queued += depth;
                    // Канал стартует только с набранным запасом, иначе он будет
                    // рваться на каждом кадре: приходит ровно столько, сколько играем.
                    pkt = (c.flowing || depth >= PREBUFFER) ? c.queue.poll() : null;
                }

                boolean conceal = false;
                if (pkt == null) {
                    if (!c.flowing) continue;           // ждём набора буфера
                    if (c.concealed >= MAX_CONCEAL) {   // говорящий замолчал
                        c.flowing = false;
                        c.concealed = 0;
                        VoiceStats.underruns.incrementAndGet();
                        continue;
                    }
                    conceal = true;
                    c.concealed++;
                    VoiceStats.underruns.incrementAndGet();
                } else {
                    c.concealed = 0;
                }

                int n;
                try {
                    // Opus сам достраивает пропавший кадр, если отдать ему null:
                    // это куда лучше тишины, щелчка на стыке не слышно.
                    n = conceal
                            ? c.dec.decode(null, 0, 0, mono, 0, frames, false)
                            : c.dec.decode(pkt, 0, pkt.length, mono, 0, frames, false);
                } catch (Throwable t) {
                    VoiceStats.decodeErrors.incrementAndGet();
                    n = 0;
                }
                if (n <= 0) continue;

                noteLevel(e.getKey().sender(), mono, n);

                float[] gains = gains(e.getKey(), master * c.gain);
                if (gains == null) continue; // игрок не виден — пропускаем
                float gl = gains[0], gr = gains[1];

                // Затухание только на реальном старте и остановке канала.
                // Раньше оно срабатывало каждый раз, когда очередь пустела после
                // выборки, то есть почти на каждом кадре — это и был треск.
                boolean fadeIn = !c.flowing;
                for (int i = 0; i < n; i++) {
                    float g = fadeIn && i < RAMP ? i / (float) RAMP : 1f;
                    acc[i * 2] += (int) (mono[i] * gl * g);
                    acc[i * 2 + 1] += (int) (mono[i] * gr * g);
                }
                c.flowing = true;
            }
            VoiceStats.setJitterQueue(queued);

            // Монитор микрофона — непрерывным потоком из кольцевого буфера.
            if (!monitorPrimed && monitor.size() >= monitorTarget) monitorPrimed = true;
            int got = monitorPrimed ? monitor.read(mon, frames, monitorTarget) : 0;
            if (got == 0) {
                if (monitorFlowing) {
                    monitorFlowing = false;
                    monitorPrimed = false;
                    VoiceStats.monitorGaps.incrementAndGet();
                }
            } else {
                boolean fadeIn = !monitorFlowing;
                for (int i = 0; i < got; i++) {
                    float g = fadeIn && i < RAMP ? i / (float) RAMP : 1f;
                    int v = (int) (mon[i] * master * g);
                    acc[i * 2] += v;
                    acc[i * 2 + 1] += v;
                }
                monitorFlowing = true;
            }

            for (int i = 0; i < acc.length; i++) {
                int v = acc[i];
                if (v > 32767) v = 32767; else if (v < -32768) v = -32768;
                out[i * 2] = (byte) v;
                out[i * 2 + 1] = (byte) (v >> 8);
            }
            try {
                line.write(out, 0, out.length); // блокируется на ~20 мс — задаёт темп
            } catch (Throwable t) {
                break;
            }

            long now = System.currentTimeMillis();
            channels.entrySet().removeIf(en -> now - en.getValue().last > CHAN_TTL_MS);
            if (!CozyVoiceSettings.get().monitor && monitor.size() > 0) {
                monitor.clear();
                monitorPrimed = false;
                monitorFlowing = false;
            }
        }

        try { line.stop(); } catch (Exception ignored) {}
        try { line.close(); } catch (Exception ignored) {}
        running = false;
    }

    /**
     * Громкость левого/правого канала для отправителя.
     * null — если игрок не найден в мире (не рендерится).
     */
    private static float[] gains(ChanKey key, double masterGain) {
        // Индивидуальная громкость действует на все категории: игрок хочет
        // приглушить конкретного человека, а не конкретный канал.
        double master = masterGain * CozyVoiceSettings.playerVolume(key.sender());
        if (master <= 0.0001) return null;

        // Рация, глобальный микрофон и звук с камеры звучат «в голове»:
        // расстояние до говорящего к ним неприменимо.
        if (key.category() != CozyVoiceProtocol.CAT_PROXIMITY) {
            return new float[]{(float) master, (float) master};
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        Player p = mc.level.getPlayerByUUID(key.sender());
        if (p == null) return null;

        double max = Math.max(1, CozyVoiceClient.maxDistance());
        double dist = mc.player.distanceTo(p);
        double atten = attenuation(dist, max, CozyVoiceClient.fadeStart());
        if (atten <= 0.001) return null;

        // панорама по относительному направлению
        double dx = p.getX() - mc.player.getX();
        double dz = p.getZ() - mc.player.getZ();
        double len = Math.hypot(dx, dz);
        double pan = 0;
        if (len > 0.05) {
            double yaw = Math.toRadians(mc.player.getYRot());
            double rx = -Math.cos(yaw), rz = -Math.sin(yaw); // вектор "вправо"
            pan = (dx * rx + dz * rz) / len;
            if (pan > 1) pan = 1; else if (pan < -1) pan = -1;
        }

        double vol = master * atten;
        float gl = (float) (vol * (1.0 - Math.max(0, pan)));
        float gr = (float) (vol * (1.0 + Math.min(0, pan)));
        return new float[]{gl, gr};
    }

    /**
     * Плавное приглушение по дистанции: до fadeStart громкость полная, дальше
     * косинусная кривая до нуля на границе слышимости. Линейное затухание на
     * слух обрывается, косинусное уходит мягко.
     */
    public static double attenuation(double dist, double max, double fadeStart) {
        if (dist >= max) return 0;
        double fs = max * fadeStart;
        if (dist <= fs) return 1;
        double t = (dist - fs) / (max - fs);
        return 0.5 * (1 + Math.cos(Math.PI * t));
    }
}
