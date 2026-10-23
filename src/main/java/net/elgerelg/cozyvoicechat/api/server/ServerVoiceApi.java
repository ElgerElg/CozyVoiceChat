package net.elgerelg.cozyvoicechat.api.server;

import net.elgerelg.cozyvoicechat.api.VoiceRouteHook;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server-side Cozy Voice Chat API, from {@code CozyVoiceApi.get().server()}.
 *
 * <p>All methods are thread-safe unless stated otherwise. Mutes, effects and "hear yourself"
 * are kept in memory until the server restarts, exactly like the {@code /cozyvoice} command.</p>
 */
public interface ServerVoiceApi {

    // -------------------- status --------------------

    /**
     * Whether the UDP voice relay is running. In singleplayer it only runs once the world is opened to LAN.
     *
     * @return {@code true} if voice is available
     */
    boolean isRunning();

    /**
     * Whether a player has an active voice connection (the client completed the UDP handshake).
     *
     * @param playerId the player
     * @return {@code true} if connected to voice
     */
    boolean isConnected(UUID playerId);

    /**
     * Players connected to voice.
     *
     * @return an unmodifiable snapshot
     */
    Set<UUID> getConnectedPlayers();

    /**
     * Whether the server received voice from this player within the last 400 ms.
     *
     * @param playerId the player
     * @return {@code true} if talking now
     */
    boolean isTalking(UUID playerId);

    /**
     * Proximity voice range from the server config.
     *
     * @return the range in blocks
     */
    int getVoiceRange();

    /**
     * Fraction of the range where volume starts to fade, 0..0.95.
     *
     * @return the fade start
     */
    double getFadeStart();

    /**
     * Opus bitrate clients use.
     *
     * @return bits per second
     */
    int getBitrate();

    // -------------------- mutes --------------------

    /**
     * Whether a player is muted by an admin or the API. Route-hook mutes are not included.
     *
     * @param playerId the player
     * @return {@code true} if muted
     */
    boolean isMuted(UUID playerId);

    /**
     * Remaining mute time.
     *
     * @param playerId the player
     * @return seconds left, {@code -1} if muted until restart, {@code 0} if not muted
     */
    long getMuteRemainingSeconds(UUID playerId);

    /**
     * Mutes a player. Fires {@link net.elgerelg.cozyvoicechat.api.event.VoiceMuteChangedEvent}.
     *
     * @param playerId the player
     * @param seconds  duration, or {@code 0} (or less) for "until server restart"
     */
    void mute(UUID playerId, long seconds);

    /**
     * Unmutes a player. Fires {@link net.elgerelg.cozyvoicechat.api.event.VoiceMuteChangedEvent} if they were muted.
     *
     * @param playerId the player
     * @return {@code true} if the player was muted
     */
    boolean unmute(UUID playerId);

    /**
     * Whether the player turned their own microphone off (client-reported).
     *
     * @param playerId the player
     * @return {@code true} if their mic is muted
     */
    boolean isMicMuted(UUID playerId);

    /**
     * Whether the player deafened themselves (client-reported). Deafened players don't send or hear voice.
     *
     * @param playerId the player
     * @return {@code true} if deafened
     */
    boolean isDeafened(UUID playerId);

    // -------------------- effects --------------------

    /**
     * Checks an effect.
     *
     * @param playerId the player
     * @param effect   the effect
     * @return {@code true} if it is enabled for the player
     */
    boolean hasEffect(UUID playerId, VoiceEffect effect);

    /**
     * Enables or disables an effect on everything a player says.
     *
     * @param playerId the player
     * @param effect   the effect
     * @param enabled  the new state
     */
    void setEffect(UUID playerId, VoiceEffect effect, boolean enabled);

    /**
     * Enabled effects.
     *
     * @param playerId the player
     * @return an unmodifiable set, possibly empty
     */
    Set<VoiceEffect> getEffects(UUID playerId);

    /**
     * Whether the player hears their own voice back (as in {@code /cozyvoice hearself}).
     *
     * @param playerId the player
     * @return {@code true} if enabled
     */
    boolean isHearingSelf(UUID playerId);

    /**
     * Enables or disables hearing yourself.
     *
     * @param playerId the player
     * @param enabled  the new state
     */
    void setHearSelf(UUID playerId, boolean enabled);

    // -------------------- audio channels --------------------

    /**
     * Creates an audio channel without a position.
     *
     * @param id channel id, or {@code null} for a random one
     * @return the new channel
     */
    StaticAudioChannel createStaticChannel(UUID id);

    /**
     * Creates an audio channel at a fixed position.
     *
     * @param id       channel id, or {@code null} for a random one
     * @param level    the level
     * @param position the position
     * @return the new channel
     */
    LocationalAudioChannel createLocationalChannel(UUID id, ServerLevel level, Vec3 position);

    /**
     * Creates an audio channel that follows an entity.
     *
     * @param id     channel id, or {@code null} for a random one
     * @param entity the entity
     * @return the new channel
     */
    EntityAudioChannel createEntityChannel(UUID id, Entity entity);

    /**
     * Creates a player that sends a frame to {@code channel} every 20 ms. Call {@link AudioPlayer#start()} to begin.
     *
     * @param channel the channel to feed
     * @param frames  returns the next 960-sample frame, or {@code null} when the sound is over.
     *                Called on a background thread.
     * @return the player
     */
    AudioPlayer createAudioPlayer(AudioChannel channel, Supplier<short[]> frames);

    // -------------------- groups --------------------

    /**
     * Creates a voice group.
     *
     * @param name      display name
     * @param exclusive whether members are heard only by the group
     * @return the new group
     */
    VoiceGroup createGroup(String name, boolean exclusive);

    /**
     * Finds a group by id.
     *
     * @param groupId the id
     * @return the group, if it exists
     */
    Optional<VoiceGroup> getGroup(UUID groupId);

    /**
     * Finds the group a player is in.
     *
     * @param playerId the player
     * @return the group, if any
     */
    Optional<VoiceGroup> getGroupOf(UUID playerId);

    /**
     * All groups.
     *
     * @return an unmodifiable snapshot
     */
    Collection<VoiceGroup> getGroups();

    // -------------------- routing --------------------

    /**
     * Registers a routing hook. Same as {@code CozyVoiceApi.registerRouteHook(hook)}.
     *
     * @param hook the hook
     */
    void registerRouteHook(VoiceRouteHook hook);

    /**
     * Removes a routing hook.
     *
     * @param hook the hook
     */
    void unregisterRouteHook(VoiceRouteHook hook);
}
