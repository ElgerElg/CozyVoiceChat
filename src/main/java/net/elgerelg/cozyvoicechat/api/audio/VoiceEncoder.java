package net.elgerelg.cozyvoicechat.api.audio;

/**
 * Opus encoder for {@link VoiceAudioFormat} frames. Create one with
 * {@code CozyVoiceApi.get().createEncoder()}. Not thread-safe; use one per stream.
 */
public interface VoiceEncoder extends AutoCloseable {

    /**
     * Encodes one frame.
     *
     * @param pcm exactly {@link VoiceAudioFormat#FRAME_SAMPLES} samples
     * @return the Opus packet, or an empty array if encoding failed
     * @throws IllegalArgumentException if {@code pcm} has the wrong length
     */
    byte[] encode(short[] pcm);

    /** Resets the stream state, e.g. after a pause, so the next frame starts cleanly. */
    void reset();

    /** Releases the encoder. Calling {@link #encode} afterwards is an error. */
    @Override
    void close();
}
