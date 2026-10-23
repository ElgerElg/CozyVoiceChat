package net.elgerelg.cozyvoicechat.client;

import net.elgerelg.cozyvoicechat.CozyVoiceChat;
import net.elgerelg.cozyvoicechat.CozyVoiceProtocol;
import net.elgerelg.cozyvoicechat.VoiceHelloPacket;

import net.minecraft.client.Minecraft;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Клиентская голосовая сессия: UDP-сокет, handshake/keepalive, приём голоса.
 * onHello() вызывается из VoiceHelloPacket (сервер прислал токен и порты).
 */
public final class CozyVoiceClient {

    public enum Status { OFF, CONNECTING, CONNECTED }

    private static volatile Status status = Status.OFF;
    private static DatagramSocket socket;
    private static InetSocketAddress serverAddr;
    private static UUID token;
    private static int seq = 0;
    private static volatile int maxDistance = CozyVoiceProtocol.DEFAULT_MAX_DISTANCE;
    private static volatile float fadeStart = 0.5f;
    private static volatile int bitrate = 32000;
    private static volatile boolean running = false;
    private static volatile boolean ptt = false;

    private static Thread recvThread;
    private static Thread helloThread;

    /** uuid -> время последнего голосового пакета (для HUD). */
    private static final Map<UUID, Long> talkers = new ConcurrentHashMap<>();
    private static final long TALKER_TTL_MS = 400;
    /** id аудиоканала аддона -> время последнего пакета. */
    private static final Map<UUID, Long> sources = new ConcurrentHashMap<>();

    private CozyVoiceClient() {}

    /** Вызывается на клиентском потоке из пакета VoiceHelloPacket. */
    public static void onHello(VoiceHelloPacket hello) {
        disconnect();
        Minecraft mc = Minecraft.getInstance();
        try {
            String host = hello.host.isEmpty() ? "127.0.0.1" : hello.host;
            if (hello.host.isEmpty() && mc.getConnection() != null) {
                SocketAddress ra = mc.getConnection().getConnection().getRemoteAddress();
                if (ra instanceof InetSocketAddress isa && isa.getAddress() != null) {
                    host = isa.getAddress().getHostAddress();
                }
            }

            int port = pickPort(hello.ports, hello.token);
            token = hello.token;
            maxDistance = hello.maxDistance;
            fadeStart = Math.max(0f, Math.min(0.95f, hello.fadeStart));
            bitrate = hello.bitrate;
            seq = 0;
            serverAddr = new InetSocketAddress(host, port);
            socket = new DatagramSocket();
            socket.setReceiveBufferSize(1 << 20);
            running = true;
            status = Status.CONNECTING;

            VoiceStats.reset();
            VoiceStats.setChosenPort(port);

            recvThread = new Thread(CozyVoiceClient::recvLoop, "UMC-Voice-Recv");
            recvThread.setDaemon(true);
            recvThread.start();

            helloThread = new Thread(CozyVoiceClient::helloLoop, "UMC-Voice-Hello");
            helloThread.setDaemon(true);
            helloThread.start();

            VoicePlayback.ensureStarted();
            MicCapture.restart();
            CozyVoiceChat.LOGGER.info("[CozyVoice] Подключаемся к voice-серверу {}:{} (битрейт {}, дальность {})",
                    host, port, bitrate, maxDistance);
        } catch (Throwable t2) {
            status = Status.OFF;
            CozyVoiceChat.LOGGER.warn("[CozyVoice] Ошибка подключения: {}", t2.toString());
        }
    }

    /**
     * Портов у сервера может быть несколько — раскидываем клиентов по ним
     * детерминированно, по токену сессии.
     */
    private static int pickPort(int[] ports, UUID token) {
        if (ports == null || ports.length == 0) return CozyVoiceProtocol.DEFAULT_PORT;
        int idx = Math.floorMod(Long.hashCode(token.getLeastSignificantBits()), ports.length);
        return ports[idx];
    }

    public static void disconnect() {
        running = false;
        if (socket != null) {
            try { socket.send(new DatagramPacket(CozyVoiceProtocol.bye(), CozyVoiceProtocol.HEADER, serverAddr)); }
            catch (Exception ignored) {}
            try { socket.close(); } catch (Exception ignored) {}
            socket = null;
        }
        status = Status.OFF;
        talkers.clear();
        sources.clear();
        MicCapture.updateDemand();
    }

    // -------------------- потоки --------------------

    private static void helloLoop() {
        long lastPing = 0;
        while (running) {
            byte[] pkt = CozyVoiceProtocol.handshake(token);
            try {
                socket.send(new DatagramPacket(pkt, pkt.length, serverAddr));
            } catch (Exception ignored) {}

            long now = System.currentTimeMillis();
            if (now - lastPing > 2000) {
                lastPing = now;
                try {
                    byte[] ping = CozyVoiceProtocol.ping(System.nanoTime());
                    socket.send(new DatagramPacket(ping, ping.length, serverAddr));
                } catch (Exception ignored) {}
            }
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        }
    }

    private static void recvLoop() {
        while (running) {
            byte[] buf = new byte[CozyVoiceProtocol.MAX_PACKET];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(p);
            } catch (SocketException se) {
                return;
            } catch (Exception e) {
                continue;
            }
            byte[] d = p.getData();
            int len = p.getLength();
            int type = CozyVoiceProtocol.type(d, len);
            switch (type) {
                case CozyVoiceProtocol.T_AUTH_OK -> status = Status.CONNECTED;
                case CozyVoiceProtocol.T_PONG -> {
                    if (len >= CozyVoiceProtocol.HEADER + 8) VoiceStats.onPong(CozyVoiceProtocol.readLong(d, 5));
                }
                case CozyVoiceProtocol.T_VOICE -> {
                    if (len <= CozyVoiceProtocol.RELAY_OPUS_OFFSET) break;
                    UUID sender = CozyVoiceProtocol.readUuid(d, 5);
                    int s = CozyVoiceProtocol.readInt(d, 21);
                    byte category = d[25];
                    float gain = (d[26] & 0xFF) / 255f;
                    int opusLen = len - CozyVoiceProtocol.RELAY_OPUS_OFFSET;
                    VoiceStats.onReceived(sender, s, len);
                    VoicePlayback.addVoice(sender, category, gain, d, CozyVoiceProtocol.RELAY_OPUS_OFFSET, opusLen);
                    talkers.put(sender, System.currentTimeMillis());
                }
                case CozyVoiceProtocol.T_SOURCE -> {
                    if (len <= CozyVoiceProtocol.SOURCE_KIND_DATA_OFFSET) break;
                    byte kind = d[25];
                    int head = CozyVoiceProtocol.sourceHeaderLength(kind);
                    if (head < 0 || len <= head) break;
                    UUID id = CozyVoiceProtocol.readUuid(d, 5);
                    int s = CozyVoiceProtocol.readInt(d, 21);
                    float gain = (d[26] & 0xFF) / 255f;
                    float distance = Float.intBitsToFloat(CozyVoiceProtocol.readInt(d, 27));
                    int o = CozyVoiceProtocol.SOURCE_KIND_DATA_OFFSET;
                    double x = 0, y = 0, z = 0;
                    int entityId = 0;
                    if (kind == CozyVoiceProtocol.SRC_LOCATIONAL) {
                        x = Double.longBitsToDouble(CozyVoiceProtocol.readLong(d, o));
                        y = Double.longBitsToDouble(CozyVoiceProtocol.readLong(d, o + 8));
                        z = Double.longBitsToDouble(CozyVoiceProtocol.readLong(d, o + 16));
                    } else if (kind == CozyVoiceProtocol.SRC_ENTITY) {
                        entityId = CozyVoiceProtocol.readInt(d, o);
                    }
                    if (!Float.isFinite(distance) || distance < 1f) distance = maxDistance;
                    VoiceStats.onReceived(id, s, len);
                    VoicePlayback.addSource(id, kind, gain, distance, x, y, z, entityId, d, head, len - head);
                    sources.put(id, System.currentTimeMillis());
                }
                default -> {}
            }
        }
    }

    // -------------------- API для остального кода --------------------

    public static void sendVoice(byte[] opus, int len) {
        if (status != Status.CONNECTED || socket == null) return;
        byte[] pkt = CozyVoiceProtocol.voice(seq++, opus, len);
        try {
            socket.send(new DatagramPacket(pkt, pkt.length, serverAddr));
            VoiceStats.onSent(pkt.length);
        } catch (Exception ignored) {}
    }

    public static void setPtt(boolean b) { ptt = b; }
    public static boolean isPtt() { return ptt; }
    public static Status status() { return status; }
    public static boolean isActive() { return status != Status.OFF; }
    public static int maxDistance() { return maxDistance; }
    public static float fadeStart() { return fadeStart; }
    public static int bitrate() { return bitrate; }
    public static String serverAddress() { return serverAddr == null ? "—" : serverAddr.toString(); }

    /**
     * Говорит ли конкретный игрок. Отдельно от talkingNow(), потому что
     * голограммы спрашивают это на каждого игрока в кадре, а там нельзя
     * позволить себе собирать множество заново.
     */
    public static boolean isTalking(UUID id) {
        Long t = talkers.get(id);
        return t != null && System.currentTimeMillis() - t <= TALKER_TTL_MS;
    }

    /** Звучит ли сейчас аудиоканал аддона с таким id. */
    public static boolean isSourceActive(UUID id) {
        Long t = id == null ? null : sources.get(id);
        return t != null && System.currentTimeMillis() - t <= TALKER_TTL_MS;
    }

    /** UUID игроков, которые говорят прямо сейчас (для HUD). */
    public static Set<UUID> talkingNow() {
        long now = System.currentTimeMillis();
        talkers.entrySet().removeIf(e -> now - e.getValue() > TALKER_TTL_MS);
        return talkers.keySet().stream().collect(Collectors.toSet());
    }
}
