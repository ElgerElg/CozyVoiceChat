package net.elgerelg.cozyvoicechat.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;

/**
 * Server-side extension point for voice routing (radios, global microphones, custom mutes...).
 *
 * <p>Register with {@link CozyVoiceApi#registerRouteHook(VoiceRouteHook)}. All methods are
 * called on the <b>server thread</b> while a player is talking, about every 2 ticks per talking
 * player (the result is cached and used for the frames in between), so keep them cheap.</p>
 *
 * <p>For per-route changes with access to the full target list, you can also listen to
 * {@link net.elgerelg.cozyvoicechat.api.event.VoiceRouteEvent}.</p>
 */
public interface VoiceRouteHook {

    /** Receives the recipients of a route. */
    interface Sink {
        /**
         * Sends the speaker's voice to a player.
         *
         * @param targetId the listener's UUID
         * @param category how the client should play it, see {@link VoiceCategory}
         * @param gain     volume multiplier, 0..1
         */
        void send(UUID targetId, byte category, float gain);
    }

    /**
     * Blocks a player's voice completely (custom mutes, jail, permissions...).
     * The result is cached for a few ticks.
     *
     * @param server the server
     * @param sender the talking player
     * @return {@code true} to block all voice from this player
     */
    default boolean isMuted(MinecraftServer server, ServerPlayer sender) { return false; }

    /**
     * Lets a spectator talk. By default only players with permission level 2 can talk in spectator mode.
     *
     * @param sender the spectator
     * @return {@code true} to allow this spectator to talk
     */
    default boolean canSpectatorTalk(ServerPlayer sender) { return false; }

    /**
     * Called when a player makes noise in voice chat (at most once every 2 ticks).
     * Useful for mechanics like "mobs hear you".
     *
     * @param sender the talking player
     */
    default void onVoiceNoise(ServerPlayer sender) {}

    /**
     * Replaces positional voice completely, for example a global microphone.
     *
     * @param server      the server
     * @param sender      the talking player
     * @param level       loudness of the last frame, 0..1 (0 before the first frame)
     * @param selfMonitor whether the sender wants to hear themselves
     * @param sink        where to put the recipients
     * @return {@code true} if this hook built the route and positional voice must be skipped
     */
    default boolean routeExclusive(MinecraftServer server, ServerPlayer sender, float level,
                                   boolean selfMonitor, Sink sink) { return false; }

    /**
     * Adds extra recipients on top of positional voice (radios, cameras...).
     *
     * @param server      the server
     * @param sender      the talking player
     * @param level       loudness of the last frame, 0..1
     * @param selfMonitor whether the sender wants to hear themselves
     * @param heardLive   players who already hear the sender directly (read-only), to avoid duplicates
     * @param sink        where to put the extra recipients
     */
    default void routeExtra(MinecraftServer server, ServerPlayer sender, float level,
                            boolean selfMonitor, Set<UUID> heardLive, Sink sink) {}
}
