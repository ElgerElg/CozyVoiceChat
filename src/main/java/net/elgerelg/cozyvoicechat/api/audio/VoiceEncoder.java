package net.elgerelg.cozyvoicechat.api.audio;

/**
 * Opus encoder for {@link VoiceAudioFormat} frames. Create one with
 * {@link net.elgerelg.cozyvoicechat.api.CozyVoiceApi#createEncoder()}. Encoders keep stream state,
 * so use one instance per audio stream. Calls are synchronized, but interleaving two streams
 * on one encoder produces bad audio. Works on any thread and on both sides.
 *
 * @since 1.1.0
 */
public interface VoiceEncoder extends AutoCloseable {

    /**
     * Encodes one frame.
     *
     * @param pcm exactly {@link VoiceAudioFormat#FRAME_SAMPLES} samples
     * @return the Opus packet
     * @throws IllegalArgumentException if {@code pcm} is {@code null} or has the wrong length
     * @throws IllegalStateException    if the encoder is closed or Opus failed to encode the frame
     */
    byte[] encode(short[] pcm);

    /** Resets the stream state, e.g. after a pause, so the next frame starts cleanly. */
    void reset();

    /** Releases the encoder. Calling {@link #encode} afterwards is an error. */
    @Override
    void close();
}
