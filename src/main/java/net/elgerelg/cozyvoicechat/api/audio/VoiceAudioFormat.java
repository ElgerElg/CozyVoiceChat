package net.elgerelg.cozyvoicechat.api.audio;

/**
 * Audio format used everywhere in Cozy Voice Chat: 48 kHz, mono, signed 16-bit PCM,
 * in frames of 20 ms (960 samples). Every PCM array passed to or received from the API
 * is one frame in this format.
 */
public final class VoiceAudioFormat {

    /** Sample rate in Hz. */
    public static final int SAMPLE_RATE = 48000;
    /** Number of channels (mono). */
    public static final int CHANNELS = 1;
    /** Samples per frame. */
    public static final int FRAME_SAMPLES = 960;
    /** Frame length in milliseconds. */
    public static final int FRAME_MILLIS = 20;

    private VoiceAudioFormat() {}
}
