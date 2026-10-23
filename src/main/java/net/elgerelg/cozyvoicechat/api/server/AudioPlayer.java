package net.elgerelg.cozyvoicechat.api.server;

/**
 * Feeds an {@link AudioChannel} with one frame every 20 ms on a background thread.
 * Created by {@link ServerVoiceApi#createAudioPlayer}. It stops when the frame supplier
 * returns {@code null}, when the channel is closed, or when {@link #stop()} is called.
 */
public interface AudioPlayer {

    /** Starts playing. Does nothing if already playing or already stopped once. */
    void start();

    /** Stops playing. Safe to call more than once. */
    void stop();

    /**
     * Whether the player is currently running.
     *
     * @return {@code true} while frames are being sent
     */
    boolean isPlaying();

    /**
     * Whether the player has stopped (and cannot be restarted).
     *
     * @return {@code true} after the player stopped
     */
    boolean isStopped();

    /**
     * Runs a callback once the player stops for any reason. If it already stopped, the callback runs immediately.
     *
     * @param callback the callback; it runs on the audio thread or the caller's thread
     */
    void onStopped(Runnable callback);

    /**
     * The channel this player feeds.
     *
     * @return the channel
     */
    AudioChannel getChannel();
}
