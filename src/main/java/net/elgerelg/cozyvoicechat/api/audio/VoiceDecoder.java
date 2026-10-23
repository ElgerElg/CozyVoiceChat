package net.elgerelg.cozyvoicechat.api.audio;

/**
 * Opus decoder producing {@link VoiceAudioFormat} frames. Create one with
 * {@code CozyVoiceApi.get().createDecoder()}. Not thread-safe; use one per stream.
 */
public interface VoiceDecoder extends AutoCloseable {

    /**
     * Decodes one Opus packet.
     *
     * @param opus an Opus packet, or {@code null} to conceal a lost packet (packet loss concealment)
     * @return {@link VoiceAudioFormat#FRAME_SAMPLES} samples; silence if decoding failed
     */
    short[] decode(byte[] opus);

    /** Resets the stream state. */
    void reset();

    /** Releases the decoder. Calling {@link #decode} afterwards is an error. */
    @Override
    void close();
}
