package net.elgerelg.cozyvoicechat.server;

import net.elgerelg.cozyvoicechat.CozyVoiceProtocol;
import net.elgerelg.cozyvoicechat.OpusCodec;

import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверные эффекты голоса (radio, echo) поверх нашего Opus.
 *
 * Кодек на игрока создаётся один раз и переиспользуется: создавать
 * декодер/энкодер на каждый кадр слишком дорого, при большом онлайне это
 * заметно по нагрузке и рвёт звук.
 */
public final class VoiceEffectsServer {

    private static final int ECHO_DELAY_SAMPLES = 4800; // ~100 мс
    private static final float ECHO_MIX = 0.55f;
    private static final float ECHO_FEEDBACK = 0.35f;

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    /** Отдельные декодеры для замера громкости: делить их с эффектами нельзя. */
    private static final Map<UUID, LevelState> LEVELS = new ConcurrentHashMap<>();

    private static final class LevelState {
        final Object lock = new Object();
        final OpusDecoder decoder;
        final short[] pcm = new short[CozyVoiceProtocol.FRAME_SAMPLES];
        float smoothed = 0f;

        LevelState(OpusDecoder decoder) { this.decoder = decoder; }
    }

    private static final class State {
        final Object lock = new Object();
        final OpusDecoder decoder;
        final OpusEncoder encoder;
        final short[] pcm = new short[CozyVoiceProtocol.FRAME_SAMPLES];
        final byte[] out = new byte[1024];
        final float[] echoDelay = new float[ECHO_DELAY_SAMPLES];
        int echoPos = 0;
        /** Сглаженное состояние фильтра, чтобы не щёлкало на границе кадров. */
        float lowpass = 0f;

        State(OpusDecoder decoder, OpusEncoder encoder) {
            this.decoder = decoder;
            this.encoder = encoder;
        }
    }

    private VoiceEffectsServer() {}

    public static void clear(UUID playerId) {
        if (playerId == null) return;
        STATES.remove(playerId);
        LEVELS.remove(playerId);
    }

    public static void clearAll() {
        STATES.clear();
        LEVELS.clear();
    }

    /**
     * Применяет эффекты за один decode/encode. Если что-то пошло не так,
     * возвращает исходные данные — голос важнее эффекта.
     */
    public static byte[] apply(UUID senderId, byte[] opus, int bitrate, boolean radio, boolean echo) {
        if (opus == null || opus.length == 0 || (!radio && !echo)) return opus;

        State st = STATES.computeIfAbsent(senderId, k -> {
            OpusDecoder d = OpusCodec.newDecoder();
            OpusEncoder e = OpusCodec.newEncoder(bitrate);
            return (d == null || e == null) ? null : new State(d, e);
        });
        if (st == null) return opus;

        synchronized (st.lock) {
            try {
                int n = st.decoder.decode(opus, 0, opus.length, st.pcm, 0, CozyVoiceProtocol.FRAME_SAMPLES, false);
                if (n <= 0) return opus;

                if (radio) {
                    // Фильтр нижних частот + квантование: глуховатый «эфирный» тембр.
                    final float alpha = 0.12f;
                    float lp = st.lowpass;
                    for (int i = 0; i < n; i++) {
                        lp += alpha * (st.pcm[i] - lp);
                        int s = ((int) lp >> 4) << 4;
                        if (s > Short.MAX_VALUE) s = Short.MAX_VALUE;
                        else if (s < Short.MIN_VALUE) s = Short.MIN_VALUE;
                        st.pcm[i] = (short) s;
                    }
                    st.lowpass = lp;
                }

                if (echo) {
                    int pos = st.echoPos;
                    for (int i = 0; i < n; i++) {
                        float in = st.pcm[i];
                        float delayed = st.echoDelay[pos];
                        float v = in + delayed * ECHO_MIX;
                        st.echoDelay[pos] = in + delayed * ECHO_FEEDBACK;
                        if (v > Short.MAX_VALUE) v = Short.MAX_VALUE;
                        else if (v < Short.MIN_VALUE) v = Short.MIN_VALUE;
                        st.pcm[i] = (short) v;
                        if (++pos >= st.echoDelay.length) pos = 0;
                    }
                    st.echoPos = pos;
                }

                int len = st.encoder.encode(st.pcm, 0, n, st.out, 0, st.out.length);
                if (len <= 0) return opus;
                byte[] res = new byte[len];
                System.arraycopy(st.out, 0, res, 0, len);
                return res;
            } catch (Throwable t) {
                STATES.remove(senderId);
                return opus;
            }
        }
    }

    /** Громкость кадра 0..1 для HUD говорящих. */
    public static float level01(UUID senderId, byte[] opus) {
        if (opus == null || opus.length == 0) return 0f;
        LevelState st = LEVELS.computeIfAbsent(senderId, k -> {
            OpusDecoder d = OpusCodec.newDecoder();
            return d == null ? null : new LevelState(d);
        });
        if (st == null) return 0f;

        synchronized (st.lock) {
            try {
                int n = st.decoder.decode(opus, 0, opus.length, st.pcm, 0, CozyVoiceProtocol.FRAME_SAMPLES, false);
                if (n <= 0) return st.smoothed;
                // Пик, как у иконки микрофона. Средняя энергия кадра для обычной
                // речи сидит около 0.1–0.3, и столбики рации от неё почти не росли.
                int peak = 0;
                for (int i = 0; i < n; i++) {
                    int a = st.pcm[i];
                    if (a < 0) a = -a;
                    if (a > peak) peak = a;
                }
                float lvl = Math.min(1f, peak / 32768f);
                if (lvl > 0.93f) lvl = 1f;
                // Сэмпл берётся раз в несколько тиков: тихий кадр между словами
                // не должен ронять индикатор в ноль, пока фраза ещё идёт.
                st.smoothed = Math.max(lvl, st.smoothed * 0.62f);
                return st.smoothed;
            } catch (Throwable t) {
                return 0f;
            }
        }
    }
}
