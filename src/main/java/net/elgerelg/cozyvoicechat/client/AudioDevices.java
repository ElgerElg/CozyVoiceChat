package net.elgerelg.cozyvoicechat.client;

import net.minecraft.client.resources.language.I18n;

import net.elgerelg.cozyvoicechat.CozyVoiceProtocol;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;

/**
 * Перечисление и открытие аудиоустройств через Java Sound API.
 */
public final class AudioDevices {

    /** Микрофон: 48кГц, 16бит, моно, little-endian. */
    public static final AudioFormat CAPTURE_FORMAT =
            new AudioFormat(CozyVoiceProtocol.SAMPLE_RATE, 16, 1, true, false);
    /** Вывод: 48кГц, 16бит, стерео (для панорамы), little-endian. */
    public static final AudioFormat PLAYBACK_FORMAT =
            new AudioFormat(CozyVoiceProtocol.SAMPLE_RATE, 16, 2, true, false);

    /**
     * Обход микшеров через Java Sound стоит десятки миллисекунд, а список
     * устройств запрашивают из интерфейса каждый кадр. Держим его в кэше и
     * пересобираем не чаще раза в 5 секунд.
     */
    private static final long CACHE_TTL_MS = 5000;
    private static volatile List<Mixer.Info> inputsCache;
    private static volatile List<Mixer.Info> outputsCache;
    private static volatile long inputsAt, outputsAt;

    private AudioDevices() {}

    /** Микшеры, умеющие TargetDataLine (микрофоны/линейные входы). */
    public static List<Mixer.Info> inputs() {
        long now = System.currentTimeMillis();
        List<Mixer.Info> cached = inputsCache;
        if (cached != null && now - inputsAt < CACHE_TTL_MS) return cached;

        List<Mixer.Info> out = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                Mixer m = AudioSystem.getMixer(info);
                Line.Info[] lines = m.getTargetLineInfo(new DataLine.Info(TargetDataLine.class, CAPTURE_FORMAT));
                if (lines.length > 0) out.add(info);
            } catch (Exception ignored) {}
        }
        inputsCache = out;
        inputsAt = now;
        return out;
    }

    /** Микшеры, умеющие SourceDataLine (динамики/наушники). */
    public static List<Mixer.Info> outputs() {
        long now = System.currentTimeMillis();
        List<Mixer.Info> cached = outputsCache;
        if (cached != null && now - outputsAt < CACHE_TTL_MS) return cached;

        List<Mixer.Info> out = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                Mixer m = AudioSystem.getMixer(info);
                Line.Info[] lines = m.getSourceLineInfo(new DataLine.Info(SourceDataLine.class, PLAYBACK_FORMAT));
                if (lines.length > 0) out.add(info);
            } catch (Exception ignored) {}
        }
        outputsCache = out;
        outputsAt = now;
        return out;
    }

    /** Принудительно обновить списки (после смены устройства в системе). */
    public static void invalidateCache() {
        inputsCache = null;
        outputsCache = null;
    }

    /** name == null/"" -> устройство по умолчанию. */
    public static TargetDataLine openInput(String name) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, CAPTURE_FORMAT);
        TargetDataLine line;
        // Устройство из конфига могли отключить — тогда берём системное,
        // иначе войс молча перестаёт работать до правки настроек.
        Mixer.Info mi = (name == null || name.isEmpty()) ? null : findMixer(name, true);
        if (mi == null) {
            line = (TargetDataLine) AudioSystem.getLine(info);
        } else {
            line = (TargetDataLine) AudioSystem.getMixer(mi).getLine(info);
        }
        // Запас на 16 кадров (~320 мс): маленький буфер даёт срывы и треск.
        line.open(CAPTURE_FORMAT, CozyVoiceProtocol.FRAME_SAMPLES * 2 * 16);
        return line;
    }

    public static SourceDataLine openOutput(String name) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, PLAYBACK_FORMAT);
        SourceDataLine line;
        Mixer.Info mi = (name == null || name.isEmpty()) ? null : findMixer(name, false);
        if (mi == null) {
            line = (SourceDataLine) AudioSystem.getLine(info);
        } else {
            line = (SourceDataLine) AudioSystem.getMixer(mi).getLine(info);
        }
        line.open(PLAYBACK_FORMAT, CozyVoiceProtocol.FRAME_SAMPLES * 4 * 16);
        return line;
    }

    private static Mixer.Info findMixer(String name, boolean input) {
        List<Mixer.Info> list = input ? inputs() : outputs();
        for (Mixer.Info i : list) if (i.getName().equals(name)) return i;
        return null;
    }

    /** Короткое имя для GUI. */
    public static String displayName(Mixer.Info info) {
        if (info == null) return I18n.get("cozyvoicechat.device.default");
        String n = info.getName();
        return n.length() > 38 ? n.substring(0, 38) : n;
    }
}
