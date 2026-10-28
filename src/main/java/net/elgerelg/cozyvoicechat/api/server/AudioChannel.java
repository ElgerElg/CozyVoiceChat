package net.elgerelg.cozyvoicechat.api.server;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * A server-side audio source that streams sound to players through the voice relay:
 * music, sound effects, recordings, radio stations and so on.
 *
 * <p>Send one 20 ms frame every 20 ms with {@link #sendPcm(short[])} or {@link #sendOpus(byte[])}.
 * The easiest way to keep the pace is {@link ServerVoiceApi#createAudioPlayer}.
 * Sending methods are thread-safe and may be called from any thread.</p>
 *
 * <p>Recipients are recalculated on the server thread every couple of ticks, so the very
 * first frames of a new channel may reach nobody for up to 2 ticks.</p>
 *
 * <p>Side: logical server. Create channels through {@link ServerVoiceApi}.</p>
 *
 * @see StaticAudioChannel
 * @see LocationalAudioChannel
 * @see EntityAudioChannel
 *
 * @since 1.1.0
 */
public interface AudioChannel {

    /**
     * Channel id. Clients see it as the speaker UUID of the audio (for example in
     * {@code ClientReceiveAudioEvent} and in per-source levels).
     *
     * @return the id
     */
    UUID getId();

    /**
     * Encodes and sends one PCM frame.
     *
     * @param pcm {@link net.elgerelg.cozyvoicechat.api.audio.VoiceAudioFormat#FRAME_SAMPLES} samples
     * @throws IllegalArgumentException if the frame has the wrong length
     */
    void sendPcm(short[] pcm);

    /**
     * Sends one already encoded Opus frame (20 ms, 48 kHz, mono).
     *
     * @param opus the Opus packet
     */
    void sendOpus(byte[] opus);

    /**
     * Volume of this channel for every listener.
     *
     * @return gain 0..1
     */
    float getGain();

    /**
     * Sets the volume of this channel.
     *
     * @param gain 0..1, clamped
     */
    void setGain(float gain);

    /**
     * Restricts who can hear the channel. The filter runs on the server thread when
     * recipients are recalculated.
     *
     * @param filter a predicate, or {@code null} for no restriction
     */
    void setFilter(Predicate<ServerPlayer> filter);

    /** Resets the internal encoder, e.g. between two songs, to avoid a click. */
    void flush();

    /**
     * Whether {@link #close()} was called.
     *
     * @return {@code true} if closed
     */
    boolean isClosed();

    /** Closes the channel. Further frames are ignored. */
    void close();
}
