package net.elgerelg.cozyvoicechat.api.audio;

/**
 * Opus decoder producing {@link VoiceAudioFormat} frames. Create one with
 * {@link net.elgerelg.cozyvoicechat.api.CozyVoiceApi#createDecoder()}. Decoders keep stream state,
 * so use one instance per incoming stream. Works on any thread and on both sides.
 *
 * @since 1.1.0
 */
public interface VoiceDecoder extends AutoCloseable {

    /**
     * Decodes one Opus packet.
     *
     * @param opus an Opus packet, or {@code null} to conceal a lost packet (packet loss concealment)
     * @return a new array of {@link VoiceAudioFormat#FRAME_SAMPLES} samples
     * @throws IllegalStateException if the decoder is closed or the packet could not be decoded
     */
    short[] decode(byte[] opus);

    /** Resets the stream state. */
    void reset();

    /** Releases the decoder. Calling {@link #decode} afterwards is an error. */
    @Override
    void close();
}
