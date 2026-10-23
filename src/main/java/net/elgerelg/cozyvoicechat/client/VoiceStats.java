package net.elgerelg.cozyvoicechat.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Счётчики голосового чата для отладочного оверлея (F3 + V) и команды
 * /cmdvoice debug. Всё складывается тут, чтобы не тащить статистику
 * через сам транспорт.
 */
public final class VoiceStats {

    public static final AtomicLong sentPackets = new AtomicLong();
    public static final AtomicLong sentBytes = new AtomicLong();
    public static final AtomicLong recvPackets = new AtomicLong();
    public static final AtomicLong recvBytes = new AtomicLong();
    public static final AtomicLong lostPackets = new AtomicLong();
    public static final AtomicLong decodeErrors = new AtomicLong();
    public static final AtomicLong monitorGaps = new AtomicLong();
    public static final AtomicLong underruns = new AtomicLong();

    private static volatile long rttNanos = -1;
    private static volatile int jitterQueue = 0;
    private static volatile int chosenPort = 0;

    /** sender -> последний seq, для подсчёта пропусков. */
    private static final Map<UUID, Integer> lastSeq = new ConcurrentHashMap<>();

    private VoiceStats() {}

    public static void onSent(int bytes) {
        sentPackets.incrementAndGet();
        sentBytes.addAndGet(bytes);
    }

    public static void onReceived(UUID sender, int seq, int bytes) {
        recvPackets.incrementAndGet();
        recvBytes.addAndGet(bytes);
        Integer prev = lastSeq.put(sender, seq);
        if (prev != null && seq > prev + 1) lostPackets.addAndGet(seq - prev - 1);
    }

    public static void onPong(long clientNanos) {
        rttNanos = System.nanoTime() - clientNanos;
    }

    public static void setJitterQueue(int frames) { jitterQueue = frames; }
    public static void setChosenPort(int port) { chosenPort = port; }

    public static int chosenPort() { return chosenPort; }
    public static int jitterQueue() { return jitterQueue; }

    public static double rttMs() {
        long r = rttNanos;
        return r < 0 ? -1 : r / 1_000_000.0;
    }

    public static double lossPercent() {
        long recv = recvPackets.get();
        long lost = lostPackets.get();
        long total = recv + lost;
        return total == 0 ? 0 : (lost * 100.0) / total;
    }

    public static void reset() {
        sentPackets.set(0);
        sentBytes.set(0);
        recvPackets.set(0);
        recvBytes.set(0);
        lostPackets.set(0);
        decodeErrors.set(0);
        monitorGaps.set(0);
        underruns.set(0);
        rttNanos = -1;
        lastSeq.clear();
    }
}
