package net.elgerelg.cozyvoicechat.client;

import java.util.UUID;

/**
 * Геометрия иконок войсчата в единой сетке 16x16.
 *
 * Иконки рисуются прямоугольниками, без текстур, и одна и та же геометрия идёт
 * и в HUD, и в голограмму над головой — иначе они неизбежно разъезжаются.
 * Куда именно ложатся прямоугольники, решает Canvas.
 */
public final class VoiceIcons {

    /** Приёмник прямоугольников в координатах 0..16. */
    public interface Canvas {
        void rect(float x0, float y0, float x1, float y1, int argb);
    }

    /** Что показывать в данный момент. */
    public enum Kind { NONE, MIC, MIC_MUTED, DEAFENED, DISCONNECTED, CONNECTING }

    public static final int OUTLINE = 0xFF17191C;
    public static final int BODY    = 0xFFB8BCC2;
    public static final int FILL    = 0xFFFFFFFF;
    public static final int MUTED   = 0xFFD05050;
    public static final int WARN    = 0xFFFFC040;

    private static final float[][] MIC_PARTS = {
            {6f, 1f, 10f, 9f},    // капсула
            {5f, 6f, 11f, 8f},    // дуга
            {7f, 10f, 9f, 13f},   // ножка
            {5f, 13f, 11f, 15f},  // база
    };

    private VoiceIcons() {}

    /** Что рисовать про самого себя. */
    public static Kind selfKind(boolean intent) {
        CozyVoiceSettings cfg = CozyVoiceSettings.get();
        if (cfg.deafened) return Kind.DEAFENED;
        if (cfg.micMuted) return Kind.MIC_MUTED;
        CozyVoiceClient.Status st = CozyVoiceClient.status();
        if (st == CozyVoiceClient.Status.CONNECTING) return Kind.CONNECTING;
        if (st == CozyVoiceClient.Status.OFF) return Kind.DISCONNECTED;
        return intent ? Kind.MIC : Kind.NONE;
    }

    /** Что рисовать над головой чужого игрока. */
    public static Kind otherKind(UUID player, boolean talking) {
        if (VoiceStateClient.deafened(player)) return Kind.DEAFENED;
        if (VoiceStateClient.micMuted(player)) return Kind.MIC_MUTED;
        return talking ? Kind.MIC : Kind.NONE;
    }

    /**
     * @param level  громкость 0..1 (заливка микрофона снизу вверх)
     * @param time   секунды для анимации точек подключения
     */
    public static void draw(Canvas c, Kind kind, float level, float time, float alpha) {
        switch (kind) {
            case MIC -> mic(c, level, alpha);
            case MIC_MUTED -> { mic(c, 0f, alpha); slash(c, alpha); }
            case DEAFENED -> { speaker(c, alpha); slash(c, alpha); }
            case DISCONNECTED -> plug(c, alpha);
            case CONNECTING -> dots(c, time, alpha);
            case NONE -> {}
        }
    }

    private static void mic(Canvas c, float level, float a) {
        // Почти полный уровень дотягиваем до верха: иначе на крике остаётся
        // незакрашенная полоска в пару пикселей.
        float lvl = clamp(level);
        if (lvl > 0.93f) lvl = 1f;
        float lineY = 16f - lvl * 16f;

        for (float[] r : MIC_PARTS) {
            c.rect(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, alpha(OUTLINE, a));
        }
        for (float[] r : MIC_PARTS) {
            c.rect(r[0], r[1], r[2], r[3], alpha(BODY, a));
            if (lineY < r[3]) {
                c.rect(r[0], Math.max(r[1], lineY), r[2], r[3], alpha(FILL, a));
            }
        }
    }

    private static void speaker(Canvas c, float a) {
        int o = alpha(OUTLINE, a), b = alpha(BODY, a);
        c.rect(3f, 4f, 9f, 12f, o);
        c.rect(4f, 6f, 7f, 10f, b);   // корпус
        c.rect(7f, 4f, 9f, 12f, b);   // раструб
        c.rect(10f, 5f, 11f, 11f, b); // волна
        c.rect(12f, 3f, 13f, 13f, b);
    }

    private static void slash(Canvas c, float a) {
        // Диагональ ступеньками: прямоугольниками другой не нарисовать,
        // а на размере иконки ступеньки не читаются.
        for (int i = 0; i < 14; i++) {
            c.rect(1f + i, 1f + i, 4f + i, 4f + i, alpha(OUTLINE, a));
        }
        for (int i = 0; i < 14; i++) {
            c.rect(2f + i, 2f + i, 3f + i, 3f + i, alpha(MUTED, a));
        }
    }

    /**
     * Разорванное соединение: вилка и розетка, разведённые в стороны.
     * Просто вилка читалась плохо — из неё не понять, что связи нет.
     */
    private static void plug(Canvas c, float a) {
        int o = alpha(OUTLINE, a), w = alpha(WARN, a), d = alpha(0xFF8A6A20, a);

        // Верхняя половина — вилка со штырями вниз.
        c.rect(3f, 1f, 13f, 6f, o);
        c.rect(4f, 2f, 12f, 5f, w);
        c.rect(7f, 0f, 9f, 2f, d);      // кабель
        c.rect(5f, 6f, 7f, 8f, o);
        c.rect(9f, 6f, 11f, 8f, o);

        // Нижняя половина — гнездо с отверстиями вверх, с зазором.
        c.rect(3f, 10f, 13f, 15f, o);
        c.rect(4f, 11f, 12f, 14f, w);
        c.rect(5f, 11f, 7f, 13f, o);    // отверстие
        c.rect(9f, 11f, 11f, 13f, o);   // отверстие
        c.rect(7f, 14f, 9f, 16f, d);    // кабель
    }

    /** Три точки: идёт подключение. */
    private static void dots(Canvas c, float time, float a) {
        for (int i = 0; i < 3; i++) {
            float phase = time * 3f - i * 0.5f;
            float bob = (float) Math.max(0, Math.sin(phase)) * 3f;
            float x = 2f + i * 5f;
            float y = 9f - bob;
            c.rect(x, y, x + 3f, y + 3f, alpha(BODY, a));
        }
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static int alpha(int color, float a) {
        int base = (color >>> 24) & 0xFF;
        int na = (int) (base * Math.max(0f, Math.min(1f, a)));
        return (na << 24) | (color & 0x00FFFFFF);
    }
}
