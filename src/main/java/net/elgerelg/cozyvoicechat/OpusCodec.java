package net.elgerelg.cozyvoicechat;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusSignal;

/**
 * Обёртка над Concentus (чисто-java Opus).
 * Если библиотеки нет в рантайме — мод не падает, войс просто отключается,
 * а реальная причина пишется в лог и в initError() для показа в меню.
 */
public final class OpusCodec {

    private static Boolean available;
    private static String initError;

    private OpusCodec() {}

    public static synchronized boolean available() {
        if (available == null) {
            try {
                OpusEncoder e = new OpusEncoder(CozyVoiceProtocol.SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP);
                OpusDecoder d = new OpusDecoder(CozyVoiceProtocol.SAMPLE_RATE, 1);
                available = e != null && d != null;
                initError = null;
                CozyVoiceChat.LOGGER.info("[CozyVoice] Opus (Concentus) готов: {} Hz, mono", CozyVoiceProtocol.SAMPLE_RATE);
            } catch (Throwable t) {
                available = false;
                initError = t.getClass().getSimpleName() + ": " + t.getMessage();
                CozyVoiceChat.LOGGER.error("[CozyVoice] Opus недоступен — войс отключён", t);
            }
        }
        return available;
    }

    /** Текст ошибки инициализации Opus или null. */
    public static String initError() {
        available();
        return initError;
    }

    public static OpusEncoder newEncoder(int bitrate) {
        try {
            OpusEncoder e = new OpusEncoder(CozyVoiceProtocol.SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP);
            e.setBitrate(Math.max(8000, Math.min(96000, bitrate)));
            e.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
            e.setComplexity(5);
            return e;
        } catch (Throwable t) {
            CozyVoiceChat.LOGGER.error("[CozyVoice] Не удалось создать Opus-энкодер", t);
            return null;
        }
    }

    public static OpusDecoder newDecoder() {
        try {
            return new OpusDecoder(CozyVoiceProtocol.SAMPLE_RATE, 1);
        } catch (Throwable t) {
            CozyVoiceChat.LOGGER.error("[CozyVoice] Не удалось создать Opus-декодер", t);
            return null;
        }
    }
}
