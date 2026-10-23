package net.elgerelg.cozyvoicechat.client;

import net.elgerelg.cozyvoicechat.CozyVoiceProtocol;
import net.elgerelg.cozyvoicechat.OpusCodec;

import io.github.jaredmdobson.concentus.OpusEncoder;
import net.elgerelg.cozyvoicechat.api.event.client.ClientMicrophoneEvent;
import net.minecraftforge.common.MinecraftForge;

import javax.sound.sampled.TargetDataLine;
import java.util.Arrays;

/**
 * Поток захвата микрофона: 20 мс кадры -> усиление -> гейт (PTT/VAD) -> Opus -> UDP.
 * Работает, когда нужен захват: подключён войс, открыт экран настроек или включён тест микрофона.
 */
public final class MicCapture implements Runnable {

    private static MicCapture instance;
    private static volatile boolean screenDemand = false;

    private Thread thread;
    private volatile boolean running = false;
    private volatile float level = 0f;          // сглаженный уровень 0..1 для метра
    private volatile boolean gateOpen = false;  // сейчас идёт передача
    private volatile String error = null;
    private TargetDataLine line;

    private MicCapture() {}

    /** Пересчитать, нужен ли захват, и запустить/остановить поток. */
    public static synchronized void updateDemand() {
        boolean need = CozyVoiceClient.isActive() || CozyVoiceSettings.get().monitor || screenDemand;
        if (need && (instance == null || !instance.running)) {
            instance = new MicCapture();
            instance.running = true;
            instance.thread = new Thread(instance, "UMC-Voice-Mic");
            instance.thread.setDaemon(true);
            instance.thread.start();
        } else if (!need && instance != null) {
            instance.stopInternal();
            instance = null;
        }
    }

    /** Перезапуск (смена устройства ввода). */
    public static synchronized void restart() {
        if (instance != null) {
            instance.stopInternal();
            instance = null;
        }
        updateDemand();
    }

    public static void setScreenDemand(boolean b) {
        screenDemand = b;
        updateDemand();
    }

    public static float level() {
        return instance == null ? 0f : instance.level;
    }

    public static boolean isGateOpen() {
        return instance != null && instance.gateOpen;
    }

    public static String error() {
        return instance == null ? null : instance.error;
    }

    /**
     * Ждать завершения потока здесь нельзя: метод вызывается с главного потока
     * (закрытие меню, смена устройства, вход на сервер), и join замораживал
     * игру почти на секунду. Поток сам выйдет из цикла, как только закроется
     * линия — держать его не за что.
     */
    private void stopInternal() {
        running = false;
        if (line != null) {
            try { line.stop(); } catch (Exception ignored) {}
            try { line.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void run() {
        try {
            line = AudioDevices.openInput(CozyVoiceSettings.get().inputDevice);
            line.start();
            error = null;
        } catch (Throwable t) {
            error = String.valueOf(t);
            running = false;
            return;
        }

        OpusEncoder encoder = OpusCodec.newEncoder(CozyVoiceClient.bitrate());
        byte[] raw = new byte[CozyVoiceProtocol.FRAME_SAMPLES * 2];
        short[] pcm = new short[CozyVoiceProtocol.FRAME_SAMPLES];
        byte[] opusBuf = new byte[1024];
        float smooth = 0f;
        NoiseSuppressor denoise = new NoiseSuppressor();

        while (running) {
            int off = 0;
            while (off < raw.length && running) {
                int r = line.read(raw, off, raw.length - off);
                if (r > 0) off += r; else break;
            }
            if (!running) break;
            if (off < raw.length) Arrays.fill(raw, off, raw.length, (byte) 0);

            double gain = CozyVoiceSettings.get().micGain;
            int peak = 0;
            for (int i = 0; i < CozyVoiceProtocol.FRAME_SAMPLES; i++) {
                int s = (raw[i * 2] & 0xFF) | (raw[i * 2 + 1] << 8);
                s = (int) (s * gain);
                if (s > 32767) s = 32767; else if (s < -32768) s = -32768;
                pcm[i] = (short) s;
                int a = s < 0 ? -s : s;
                if (a > peak) peak = a;
            }
            CozyVoiceSettings cfg = CozyVoiceSettings.get();
            if (cfg.noiseSuppression) {
                denoise.process(pcm, CozyVoiceProtocol.FRAME_SAMPLES);
                // Уровень считаем уже после обработки: иначе индикатор и порог
                // активации реагировали бы на шум, который мы только что убрали.
                peak = 0;
                for (int i = 0; i < CozyVoiceProtocol.FRAME_SAMPLES; i++) {
                    int a = pcm[i] < 0 ? -pcm[i] : pcm[i];
                    if (a > peak) peak = a;
                }
            } else {
                denoise.reset();
            }

            float lvl = Math.min(1f, peak / 32768f);
            smooth = Math.max(lvl, smooth * 0.85f);
            level = smooth;

            boolean allowed = !cfg.micMuted && !cfg.deafened;
            boolean send = allowed && CozyVoiceClient.status() == CozyVoiceClient.Status.CONNECTED && encoder != null
                    && (cfg.vadMode ? lvl >= cfg.vadThreshold : CozyVoiceClient.isPtt());
            if (send) {
                // Публичный API: аддон может изменить кадр или не отправлять его.
                try {
                    if (MinecraftForge.EVENT_BUS.post(new ClientMicrophoneEvent(pcm))) send = false;
                } catch (Throwable ignored) {
                    // чужой обработчик не должен ронять микрофон
                }
            }
            gateOpen = send;

            if (send) {
                try {
                    int n = encoder.encode(pcm, 0, CozyVoiceProtocol.FRAME_SAMPLES, opusBuf, 0, opusBuf.length);
                    if (n > 0) CozyVoiceClient.sendVoice(opusBuf, n);
                } catch (Throwable ignored) {}
            }
            if (cfg.monitor) {
                VoicePlayback.feedMonitor(pcm, CozyVoiceProtocol.FRAME_SAMPLES);
            }
        }

        if (line != null) {
            try { line.stop(); } catch (Exception ignored) {}
            try { line.close(); } catch (Exception ignored) {}
        }
        running = false;
    }
}
