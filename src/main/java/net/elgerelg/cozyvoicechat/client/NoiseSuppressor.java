package net.elgerelg.cozyvoicechat.client;

/**
 * Шумоподавление для микрофона.
 *
 * Полноценный спектральный шумодав здесь не нужен и дорог: задача — убрать
 * ровный фон (кулеры, кондиционер, гул усилителя), а не чужую речь. Поэтому
 * обычная связка: обрезаем низы и давим сигнал тем сильнее, чем он ближе к
 * замеренному уровню шума. Уровень шума оценивается сам, по тихим участкам.
 *
 * Состояние привязано к потоку захвата, синхронизация не нужна.
 */
public final class NoiseSuppressor {

    /** Насколько сигнал должен превышать шум, чтобы пройти без ослабления. */
    private static final float OPEN_RATIO = 2.6f;
    /** Быстро подстраиваемся вниз (стало тише) и очень медленно вверх. */
    private static final float FLOOR_DOWN = 0.35f;
    private static final float FLOOR_UP = 0.0025f;
    /** Скорость открытия и закрытия по кадрам (20 мс). */
    private static final float ATTACK = 0.6f;
    private static final float RELEASE = 0.12f;
    /** Ниже этого держать нет смысла: полная тишина звучит как обрыв связи. */
    private static final float MIN_GAIN = 0.02f;

    private float noiseFloor = 300f;
    private float gain = 1f;
    private float hpPrevIn, hpPrevOut;

    /** Сбросить оценку шума (смена устройства). */
    public void reset() {
        noiseFloor = 300f;
        gain = 1f;
        hpPrevIn = hpPrevOut = 0f;
    }

    public void process(short[] pcm, int n) {
        if (n <= 0) return;

        // Обрезка ниже ~80 Гц: гул и стук по столу в речи не участвуют,
        // но сильно завышают замер громкости.
        for (int i = 0; i < n; i++) {
            float in = pcm[i];
            float out = 0.96f * (hpPrevOut + in - hpPrevIn);
            hpPrevIn = in;
            hpPrevOut = out;
            pcm[i] = clip(out);
        }

        double sum = 0;
        for (int i = 0; i < n; i++) sum += (double) pcm[i] * pcm[i];
        float rms = (float) Math.sqrt(sum / n);

        noiseFloor += (rms - noiseFloor) * (rms < noiseFloor ? FLOOR_DOWN : FLOOR_UP);
        if (noiseFloor < 20f) noiseFloor = 20f;

        // Плавный экспандер вместо резкого гейта: щелчков на границе нет.
        float ratio = rms / (noiseFloor * OPEN_RATIO);
        float target = ratio <= 1f ? Math.max(MIN_GAIN, ratio * ratio * 0.5f) : 1f;
        float prev = gain;
        gain += (target - gain) * (target > gain ? ATTACK : RELEASE);

        // Громкость меняем не скачком на границе кадра, а линейно по сэмплам.
        for (int i = 0; i < n; i++) {
            float g = prev + (gain - prev) * (i / (float) n);
            pcm[i] = clip(pcm[i] * g);
        }
    }

    private static short clip(float v) {
        if (v > 32767f) return 32767;
        if (v < -32768f) return -32768;
        return (short) v;
    }
}
