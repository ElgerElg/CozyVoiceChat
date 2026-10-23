package net.elgerelg.cozyvoicechat.server;

import net.elgerelg.cozyvoicechat.CozyVoiceChat;
import net.elgerelg.cozyvoicechat.CozyVoiceNetwork;
import net.elgerelg.cozyvoicechat.CozyVoiceProtocol;
import net.elgerelg.cozyvoicechat.VoiceHelloPacket;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP-ретранслятор голоса на стороне сервера (работает и на dedicated/Mohist,
 * и на встроенном сервере одиночной игры).
 *
 * Порты, битрейт, дальность и затухание берутся из config/cozyvoicechat-server.json.
 * Сокетов столько же, сколько портов: каждый со своим потоком приёма, клиенты
 * распределяются по ним — так очередь одного сокета не становится узким местом.
 */
public final class CozyVoiceServer {

    private static volatile boolean running = false;
    private static MinecraftServer server;

    private static final List<DatagramSocket> sockets = new ArrayList<>();
    private static final List<Thread> threads = new ArrayList<>();

    /** player UUID -> секретный токен для UDP-handshake */
    private static final Map<UUID, UUID> tokens = new ConcurrentHashMap<>();
    /** токен -> player UUID (обратный индекс, чтобы не сканировать карту на каждый пакет) */
    private static final Map<UUID, UUID> byToken = new ConcurrentHashMap<>();
    /** UDP-адрес -> сессия */
    private static final Map<InetSocketAddress, Session> sessions = new ConcurrentHashMap<>();
    /** player UUID -> сессия */
    private static final Map<UUID, Session> byPlayer = new ConcurrentHashMap<>();

    private static long lastSweep = 0;

    /** Исходящие датаграммы: отправку делает отдельный поток, не тик сервера. */
    private record Outgoing(DatagramSocket socket, InetSocketAddress addr, byte[] data) {}

    private static final java.util.concurrent.BlockingQueue<Outgoing> outgoing =
            new java.util.concurrent.ArrayBlockingQueue<>(4096);
    private static Thread senderThread;

    /** Состояние одного подключённого клиента. */
    public static final class Session {
        final UUID playerId;
        final InetSocketAddress addr;
        final DatagramSocket socket;
        volatile long lastSeen;
        volatile long lastVoice;
        volatile int lastSeq = -1;
        volatile long received;
        volatile long lost;
        volatile long relayed;
        volatile boolean lossWarned;
        /** Маршрут, посчитанный серверным потоком; рассылка идёт по нему. */
        volatile CozyVoiceRouter.Route route;
        /** Последний кадр — нужен серверному потоку для индикации громкости. */
        volatile byte[] lastFrame;

        Session(UUID playerId, InetSocketAddress addr, DatagramSocket socket) {
            this.playerId = playerId;
            this.addr = addr;
            this.socket = socket;
            this.lastSeen = System.currentTimeMillis();
        }

        public double lossPercent() {
            long total = received + lost;
            return total == 0 ? 0 : (lost * 100.0) / total;
        }

        public UUID playerId() { return playerId; }
        public long received() { return received; }
        public long lost() { return lost; }
        public long relayed() { return relayed; }
        public long lastVoice() { return lastVoice; }
        public int port() { return socket.getLocalPort(); }
    }

    private CozyVoiceServer() {}

    public static boolean isRunning() {
        return running;
    }

    public static int[] ports() {
        return CozyVoiceServerConfig.get().portArray();
    }

    public static void start(MinecraftServer srv) {
        if (running) return;
        if (srv.isSingleplayer() && !srv.isPublished()) {
            // Мир не открыт для сети: UDP-порты занимать не за чем и не для кого.
            // Откроют по локальной сети — поднимемся из tick().
            return;
        }
        server = srv;
        CozyVoiceServerConfig cfg = CozyVoiceServerConfig.get();

        InetAddress bind;
        try {
            bind = InetAddress.getByName(cfg.bindAddress);
        } catch (Exception e) {
            CozyVoiceChat.LOGGER.warn("[CozyVoice] Неверный bindAddress '{}', слушаю все интерфейсы", cfg.bindAddress);
            bind = null;
        }

        for (int port : cfg.ports) {
            try {
                DatagramSocket s = bind == null ? new DatagramSocket(port) : new DatagramSocket(port, bind);
                s.setReceiveBufferSize(1 << 20);
                sockets.add(s);
            } catch (Exception e) {
                CozyVoiceChat.LOGGER.error("[CozyVoice] Не удалось открыть UDP-порт {}: {}", port, e.toString());
            }
        }

        if (sockets.isEmpty()) {
            CozyVoiceChat.LOGGER.error("[CozyVoice] Ни один UDP-порт не открылся — голосовой чат выключен");
            server = null;
            return;
        }

        running = true;

        senderThread = new Thread(CozyVoiceServer::sendLoop, "UMC-Voice-Send");
        senderThread.setDaemon(true);
        senderThread.start();

        for (DatagramSocket s : sockets) {
            Thread t = new Thread(() -> loop(s), "UMC-Voice-Server-" + s.getLocalPort());
            t.setDaemon(true);
            t.start();
            threads.add(t);
        }

        CozyVoiceChat.LOGGER.info("[CozyVoice] Голосовой сервер запущен: порты {}, битрейт {} бит/с, дальность {} блоков",
                cfg.ports, cfg.bitrate, cfg.maxDistance);
    }

    public static void stop() {
        running = false;
        for (DatagramSocket s : sockets) {
            try { s.close(); } catch (Exception ignored) {}
        }
        for (Thread t : threads) {
            try { t.join(1000); } catch (InterruptedException ignored) {}
        }
        if (senderThread != null) {
            senderThread.interrupt();
            senderThread = null;
        }
        outgoing.clear();
        sockets.clear();
        threads.clear();
        tokens.clear();
        byToken.clear();
        sessions.clear();
        byPlayer.clear();
        server = null;
        CozyVoiceChat.LOGGER.info("[CozyVoice] Голосовой сервер остановлен");
    }

    // -------------------- приём --------------------

    private static void loop(DatagramSocket socket) {
        // Буфер и пакет переиспользуем: на 50 кадрах в секунду с игрока
        // выделять по килобайту на каждый пакет незачем.
        byte[] buf = new byte[CozyVoiceProtocol.MAX_PACKET];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        while (running) {
            p.setData(buf, 0, buf.length);
            try {
                socket.receive(p);
            } catch (SocketException se) {
                return; // сокет закрыли — выходим
            } catch (Exception e) {
                continue;
            }
            try {
                handle(socket, p);
            } catch (Throwable t) {
                // битый пакет — игнорим
            }
        }
    }

    private static void handle(DatagramSocket socket, DatagramPacket p) {
        byte[] d = p.getData();
        int len = p.getLength();
        int type = CozyVoiceProtocol.type(d, len);
        if (type < 0) return;

        InetSocketAddress addr = new InetSocketAddress(p.getAddress(), p.getPort());

        switch (type) {
            case CozyVoiceProtocol.T_HANDSHAKE -> {
                if (len < CozyVoiceProtocol.HEADER + 16) return;
                UUID token = CozyVoiceProtocol.readUuid(d, 5);
                UUID playerId = byToken.get(token);
                if (playerId == null) return;
                Session s = sessions.get(addr);
                if (s == null || !s.playerId.equals(playerId)) {
                    s = new Session(playerId, addr, socket);
                    sessions.put(addr, s);
                    Session old = byPlayer.put(playerId, s);
                    if (old != null) sessions.remove(old.addr);
                    CozyVoiceChat.LOGGER.info("[CozyVoice] {} подключился к голосу ({}, порт {})",
                            nameOf(playerId), addr.getAddress().getHostAddress(), socket.getLocalPort());
                }
                s.lastSeen = System.currentTimeMillis();
                send(socket, addr, CozyVoiceProtocol.authOk());
            }
            case CozyVoiceProtocol.T_VOICE -> {
                Session s = sessions.get(addr);
                if (s == null || len <= CozyVoiceProtocol.HEADER + 4) return;
                long now = System.currentTimeMillis();
                s.lastSeen = now;
                s.lastVoice = now;
                int seq = CozyVoiceProtocol.readInt(d, 5);
                countSeq(s, seq);

                int opusLen = len - 9;
                byte[] opus = new byte[opusLen];
                System.arraycopy(d, 9, opus, 0, opusLen);
                s.lastFrame = opus;
                // Кадр расходится по уже посчитанному маршруту, здесь же, в
                // сетевом потоке. Серверный тик в этом не участвует вообще:
                // раньше каждый кадр уходил в главный поток задачей.
                dispatch(s, seq, opus);
            }
            case CozyVoiceProtocol.T_PING -> {
                if (len < CozyVoiceProtocol.HEADER + 8) return;
                send(socket, addr, CozyVoiceProtocol.pong(CozyVoiceProtocol.readLong(d, 5)));
            }
            case CozyVoiceProtocol.T_BYE -> {
                Session s = sessions.remove(addr);
                if (s != null) {
                    byPlayer.remove(s.playerId, s);
                    CozyVoiceChat.LOGGER.info("[CozyVoice] {} отключился от голоса (клиент закрыл сессию)", nameOf(s.playerId));
                }
            }
            default -> {}
        }
    }

    /** Считает потери по разрывам в номерах пакетов. */
    private static void countSeq(Session s, int seq) {
        s.received++;
        int prev = s.lastSeq;
        if (prev >= 0 && seq > prev + 1) {
            s.lost += seq - prev - 1;
        }
        if (seq > prev) s.lastSeq = seq;

        double loss = s.lossPercent();
        double warnAt = CozyVoiceServerConfig.get().packetLossWarnPercent;
        if (warnAt > 0 && !s.lossWarned && s.received > 200 && loss >= warnAt) {
            s.lossWarned = true;
            CozyVoiceChat.LOGGER.warn("[CozyVoice] Потери пакетов у {}: {}% ({} из {})",
                    nameOf(s.playerId), String.format("%.1f", loss), s.lost, s.received + s.lost);
        }
    }

    /** Таймауты сессий. Статистику в консоль не пишем: она только шумит. */
    private static void housekeeping() {
        long now = System.currentTimeMillis();
        CozyVoiceServerConfig cfg = CozyVoiceServerConfig.get();

        if (now - lastSweep > 2000) {
            lastSweep = now;
            long timeout = cfg.sessionTimeoutSeconds * 1000L;
            sessions.entrySet().removeIf(e -> {
                Session s = e.getValue();
                if (now - s.lastSeen > timeout) {
                    byPlayer.remove(s.playerId, s);
                    CozyVoiceChat.LOGGER.warn("[CozyVoice] {} отвалился от голоса: timed out ({} с без пакетов)",
                            nameOf(s.playerId), cfg.sessionTimeoutSeconds);
                    return true;
                }
                return false;
            });
        }

    }

    /**
     * Серверный тик: пересчёт маршрутов для тех, кто сейчас говорит, уборка
     * сессий и слежение за открытием мира для локальной сети.
     *
     * Это единственное место, где голос вообще трогает главный поток, и цена
     * тут — несколько проверок на говорящего раз в пару тиков.
     */
    public static void tick(MinecraftServer srv) {
        if (!running) {
            if (srv.isSingleplayer() && srv.isPublished()) {
                start(srv);
                if (running) {
                    for (ServerPlayer p : srv.getPlayerList().getPlayers()) onPlayerJoin(p);
                }
            }
            return;
        }

        long now = System.currentTimeMillis();
        long gt = srv.overworld().getGameTime();

        for (Session s : byPlayer.values()) {
            if (now - s.lastVoice > 400) {   // молчит — маршрут не нужен
                s.route = null;
                continue;
            }
            CozyVoiceRouter.Route r = s.route;
            if (r != null && !r.expired(gt)) continue;
            ServerPlayer sender = srv.getPlayerList().getPlayer(s.playerId);
            if (sender == null) {
                s.route = null;
                continue;
            }
            try {
                s.route = CozyVoiceRouter.compute(srv, sender, s.lastFrame);
            } catch (Throwable t) {
                s.route = CozyVoiceRouter.empty();
                if (CozyVoiceServerConfig.get().debugLog) {
                    CozyVoiceChat.LOGGER.warn("[CozyVoice] Ошибка маршрутизации: {}", t.toString());
                }
            }
        }

        housekeeping();
    }

    /** Рассылка кадра по готовому маршруту. Идёт в сетевом потоке. */
    private static void dispatch(Session sender, int seq, byte[] opus) {
        CozyVoiceRouter.Route route = sender.route;
        if (route == null || route.targets().isEmpty()) return;

        byte[] audio = opus;
        if (route.radioFx() || route.echoFx()) {
            audio = VoiceEffectsServer.apply(sender.playerId, opus,
                    CozyVoiceServerConfig.get().bitrate, route.radioFx(), route.echoFx());
        }

        // Пакет зависит только от категории и громкости, но не от получателя,
        // поэтому собираем его один раз на категорию, а не на каждого слушателя.
        byte[][] built = new byte[4][];
        for (CozyVoiceRouter.Target t : route.targets()) {
            Session s = byPlayer.get(t.id());
            if (s == null) continue;
            int cat = t.category() & 0xFF;
            boolean cacheable = t.gain() == 1f && cat < built.length;
            byte[] pkt = cacheable ? built[cat] : null;
            if (pkt == null) {
                pkt = CozyVoiceProtocol.relayVoice(sender.playerId, seq, t.category(), t.gain(),
                        audio, 0, audio.length);
                if (cacheable) built[cat] = pkt;
            }
            send(s.socket, s.addr, pkt);
            sender.relayed++;
        }
    }

    /** Отправка датаграмм: syscall'ы не должны попадать в серверный тик. */
    private static void sendLoop() {
        while (running) {
            Outgoing o;
            try {
                o = outgoing.take();
            } catch (InterruptedException e) {
                return;
            }
            if (o.socket() == null || o.socket().isClosed()) continue;
            try {
                o.socket().send(new DatagramPacket(o.data(), o.data().length, o.addr()));
            } catch (Exception e) {
                if (CozyVoiceServerConfig.get().debugLog) {
                    CozyVoiceChat.LOGGER.warn("[CozyVoice] Ошибка отправки на {}: {}", o.addr(), e.toString());
                }
            }
        }
    }

    /**
     * Ставим в очередь, а не отправляем на месте: голос маршрутизируется в
     * серверном потоке, и блокирующий send() там стоил бы тика. Переполнение
     * очереди означает, что сеть не успевает — для realtime-звука правильнее
     * потерять кадр, чем копить задержку.
     */
    private static void send(DatagramSocket socket, InetSocketAddress addr, byte[] data) {
        if (socket == null || socket.isClosed()) return;
        outgoing.offer(new Outgoing(socket, addr, data));
    }

    private static String nameOf(UUID id) {
        MinecraftServer srv = server;
        if (srv != null) {
            ServerPlayer p = srv.getPlayerList().getPlayer(id);
            if (p != null) return p.getGameProfile().getName();
        }
        return id.toString();
    }

    // -------------------- события игроков --------------------

    public static void onPlayerJoin(ServerPlayer player) {
        if (!running) return;
        CozyVoiceServerConfig cfg = CozyVoiceServerConfig.get();
        UUID token = UUID.randomUUID();
        UUID old = tokens.put(player.getUUID(), token);
        if (old != null) byToken.remove(old);
        byToken.put(token, player.getUUID());

        int[] open = new int[sockets.size()];
        for (int i = 0; i < sockets.size(); i++) open[i] = sockets.get(i).getLocalPort();

        CozyVoiceNetwork.sendTo(player, new VoiceHelloPacket(
                token, open, cfg.bitrate, cfg.maxDistance, (float) cfg.fadeStart, cfg.publicHost));
    }

    public static void onPlayerLeave(UUID playerId) {
        UUID token = tokens.remove(playerId);
        if (token != null) byToken.remove(token);
        CozyVoiceRouter.clear(playerId);
        Session s = byPlayer.remove(playerId);
        if (s != null) {
            sessions.remove(s.addr);
            CozyVoiceChat.LOGGER.info("[CozyVoice] {} отключился от голоса (вышел с сервера)", nameOf(playerId));
        }
    }

    // -------------------- отладка --------------------

    public static Map<InetSocketAddress, Session> sessionsView() {
        return sessions;
    }

    public static Session sessionOf(UUID playerId) {
        return byPlayer.get(playerId);
    }
}
