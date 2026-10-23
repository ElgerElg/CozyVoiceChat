package net.elgerelg.cozyvoicechat.api.client;

import java.util.Set;
import java.util.UUID;

/**
 * Client-side Cozy Voice Chat API for the local player, from {@code CozyVoiceApi.get().client()}.
 * Only available on the physical client.
 *
 * <p>Getters are thread-safe and cheap enough to call every frame (e.g. from a HUD overlay).
 * Methods that change state or open screens must be called on the client (render) thread.</p>
 *
 * <p>To draw your own HUD, register a normal Forge overlay ({@code RegisterGuiOverlaysEvent})
 * and read the state from here.</p>
 */
public interface ClientVoiceApi {

    /**
     * Connection status.
     *
     * @return the status
     */
    VoiceConnectionStatus getStatus();

    /**
     * Shortcut for {@code getStatus() == CONNECTED}.
     *
     * @return {@code true} if voice is working
     */
    boolean isConnected();

    /**
     * Whether a player (or an addon audio channel id) is audible right now.
     * For the local player this is "currently transmitting".
     *
     * @param id a player UUID or channel id
     * @return {@code true} if talking
     */
    boolean isTalking(UUID id);

    /**
     * Loudness of what we hear from a player or channel, from the last played frame.
     * For the local player it is the microphone level.
     *
     * @param id a player UUID or channel id
     * @return 0..1, or 0 if silent
     */
    float getAudioLevel(UUID id);

    /**
     * Players we are hearing right now (not including the local player).
     *
     * @return a new set
     */
    Set<UUID> getTalkingPlayers();

    /**
     * Whether the local player's microphone is transmitting right now.
     *
     * @return {@code true} while transmitting
     */
    boolean isLocalTalking();

    /**
     * Smoothed microphone level, also when not transmitting (while the mic is open).
     *
     * @return 0..1
     */
    float getLocalMicLevel();

    /**
     * Whether the local microphone is muted.
     *
     * @return {@code true} if muted
     */
    boolean isMicMuted();

    /**
     * Mutes or unmutes the local microphone, saves the setting and tells the server. Client thread only.
     *
     * @param muted the new state
     */
    void setMicMuted(boolean muted);

    /**
     * Whether the local player is deafened (hears nothing and sends nothing).
     *
     * @return {@code true} if deafened
     */
    boolean isDeafened();

    /**
     * Deafens or undeafens the local player, saves the setting and tells the server. Client thread only.
     *
     * @param deafened the new state
     */
    void setDeafened(boolean deafened);

    /**
     * Whether another player turned their microphone off.
     *
     * @param playerId the player
     * @return {@code true} if their mic is muted
     */
    boolean isPlayerMicMuted(UUID playerId);

    /**
     * Whether another player is deafened.
     *
     * @param playerId the player
     * @return {@code true} if deafened
     */
    boolean isPlayerDeafened(UUID playerId);

    /**
     * Local volume multiplier for a player, as set in the player volume screen.
     *
     * @param playerId the player
     * @return 0..2, 1 by default
     */
    float getPlayerVolume(UUID playerId);

    /**
     * Sets the local volume multiplier for a player and saves it. Client thread only.
     *
     * @param playerId the player
     * @param volume   0..2, clamped
     */
    void setPlayerVolume(UUID playerId, float volume);

    /**
     * Master voice volume.
     *
     * @return 0..2
     */
    float getMasterVolume();

    /**
     * Whether the local player uses push-to-talk (otherwise voice activation).
     *
     * @return {@code true} for push-to-talk
     */
    boolean isPushToTalkMode();

    /**
     * Whether the push-to-talk key is held right now.
     *
     * @return {@code true} if held
     */
    boolean isPushToTalkPressed();

    /**
     * Voice range announced by the server.
     *
     * @return the range in blocks
     */
    int getVoiceRange();

    /** Opens the voice chat settings screen. Client thread only. */
    void openSettingsScreen();

    /** Opens the per-player volume screen. Client thread only. */
    void openPlayerVolumeScreen();
}
