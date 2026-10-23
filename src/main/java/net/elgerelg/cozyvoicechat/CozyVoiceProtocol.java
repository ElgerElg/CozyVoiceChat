package net.elgerelg.cozyvoicechat;

import java.util.UUID;

/**
 * Бинарный протокол UDP-голоса.
 *
 * Общий вид пакета: [4B magic 'CZVC'][1B type][payload]
 *
 * C2S:
 *   HANDSHAKE(1): [16B token UUID]            — также служит keepalive (раз в 2 сек)
 *   VOICE(3):     [4B seq u32][opus data]
 *   BYE(4):       —
 *   PING(5):      [8B clientNanos]            — замер RTT для отладки
 * S2C:
 *   AUTH_OK(10):  —
 *   VOICE(3):     [16B sender UUID][4B seq u32][1B category][1B gain 0..255][opus data]
 *   PONG(11):     [8B clientNanos эхом]
 *
 * Категория говорит клиенту, как проигрывать кадр: позиционный голос он
 * ослабляет по расстоянию и панорамирует, рация/глобал/CCTV звучат плоско.
 */
public final class CozyVoiceProtocol {

    public static final int MAGIC = 0x435A5643; // "CZVC"
    public static final int HEADER = 5;

    public static final byte T_HANDSHAKE = 1;
    public static final byte T_VOICE = 3;
    public static final byte T_BYE = 4;
    public static final byte T_PING = 5;
    public static final byte T_AUTH_OK = 10;
    public static final byte T_PONG = 11;

    /** Категории звука в relay-пакете. */
    public static final byte CAT_PROXIMITY = 0;
    public static final byte CAT_GLOBAL = 1;
    public static final byte CAT_RADIO = 2;
    public static final byte CAT_CCTV = 3;

    /** Смещение opus-данных в S2C VOICE. */
    public static final int RELAY_OPUS_OFFSET = HEADER + 16 + 4 + 2;

    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_SAMPLES = 960;          // 20 мс при 48 кГц
    public static final int DEFAULT_PORT = 24455;
    public static final int DEFAULT_MAX_DISTANCE = 48;
    public static final int MAX_PACKET = 2048;

    private CozyVoiceProtocol() {}

    public static byte[] handshake(UUID token) {
        byte[] d = new byte[HEADER + 16];
        writeInt(d, 0, MAGIC);
        d[4] = T_HANDSHAKE;
        writeUuid(d, 5, token);
        return d;
    }

    public static byte[] authOk() {
        byte[] d = new byte[HEADER];
        writeInt(d, 0, MAGIC);
        d[4] = T_AUTH_OK;
        return d;
    }

    public static byte[] bye() {
        byte[] d = new byte[HEADER];
        writeInt(d, 0, MAGIC);
        d[4] = T_BYE;
        return d;
    }

    public static byte[] ping(long clientNanos) {
        byte[] d = new byte[HEADER + 8];
        writeInt(d, 0, MAGIC);
        d[4] = T_PING;
        writeLong(d, 5, clientNanos);
        return d;
    }

    public static byte[] pong(long clientNanos) {
        byte[] d = new byte[HEADER + 8];
        writeInt(d, 0, MAGIC);
        d[4] = T_PONG;
        writeLong(d, 5, clientNanos);
        return d;
    }

    public static byte[] voice(int seq, byte[] opus, int opusLen) {
        byte[] d = new byte[HEADER + 4 + opusLen];
        writeInt(d, 0, MAGIC);
        d[4] = T_VOICE;
        writeInt(d, 5, seq);
        System.arraycopy(opus, 0, d, 9, opusLen);
        return d;
    }

    public static byte[] relayVoice(UUID sender, int seq, byte category, float gain,
                                    byte[] opus, int opusOff, int opusLen) {
        byte[] d = new byte[RELAY_OPUS_OFFSET + opusLen];
        writeInt(d, 0, MAGIC);
        d[4] = T_VOICE;
        writeUuid(d, 5, sender);
        writeInt(d, 21, seq);
        d[25] = category;
        d[26] = (byte) Math.round(Math.max(0f, Math.min(1f, gain)) * 255f);
        System.arraycopy(opus, opusOff, d, RELAY_OPUS_OFFSET, opusLen);
        return d;
    }

    /** Тип пакета или -1, если пакет не наш / битый. */
    public static int type(byte[] d, int len) {
        if (len < HEADER || readInt(d, 0) != MAGIC) return -1;
        return d[4];
    }

    public static int readInt(byte[] d, int o) {
        return ((d[o] & 0xFF) << 24) | ((d[o + 1] & 0xFF) << 16) | ((d[o + 2] & 0xFF) << 8) | (d[o + 3] & 0xFF);
    }

    public static void writeInt(byte[] d, int o, int v) {
        d[o] = (byte) (v >>> 24);
        d[o + 1] = (byte) (v >>> 16);
        d[o + 2] = (byte) (v >>> 8);
        d[o + 3] = (byte) v;
    }

    public static long readLong(byte[] d, int o) {
        return ((long) readInt(d, o) << 32) | (readInt(d, o + 4) & 0xFFFFFFFFL);
    }

    public static void writeLong(byte[] d, int o, long v) {
        writeInt(d, o, (int) (v >>> 32));
        writeInt(d, o + 4, (int) v);
    }

    public static UUID readUuid(byte[] d, int o) {
        return new UUID(readLong(d, o), readLong(d, o + 8));
    }

    public static void writeUuid(byte[] d, int o, UUID id) {
        writeLong(d, o, id.getMostSignificantBits());
        writeLong(d, o + 8, id.getLeastSignificantBits());
    }
}
