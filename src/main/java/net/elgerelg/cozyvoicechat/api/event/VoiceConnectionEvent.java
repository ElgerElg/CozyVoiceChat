package net.elgerelg.cozyvoicechat.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * A player's voice connection on the server opened or closed. Posted on
 * {@code MinecraftForge.EVENT_BUS} on the <b>server thread</b>.
 *
 * @since 1.1.0
 */
public abstract class VoiceConnectionEvent extends Event {

    private final UUID playerId;
    private final ServerPlayer player;

    /**
     * Creates the event. Posted by Cozy Voice Chat; addons normally only listen to it.
     *
     * @param playerId the player
     * @param player   the player entity, if online
     */
    protected VoiceConnectionEvent(UUID playerId, ServerPlayer player) {
        this.playerId = playerId;
        this.player = player;
    }

    /**
     * The player's UUID.
     *
     * @return the UUID
     */
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * The player, or {@code null} if they already left the server.
     *
     * @return the player or {@code null}
     */
    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * The client finished the UDP handshake; the player can now talk and listen.
     *
     * @since 1.1.0
     */
    public static class Connected extends VoiceConnectionEvent {
        /**
         * Creates the event. Posted by Cozy Voice Chat; addons normally only listen to it.
         *
         * @param playerId the player
         * @param player   the player entity
         */
        public Connected(UUID playerId, ServerPlayer player) {
            super(playerId, player);
        }
    }

    /**
     * The player's voice session ended.
     *
     * @since 1.1.0
     */
    public static class Disconnected extends VoiceConnectionEvent {

        /**
         * Why the session ended.
         *
         * @since 1.1.0
         */
        public enum Reason {
            /** The client closed the session (left the world, changed device and reconnected...). */
            CLIENT_CLOSED,
            /** No packets for longer than the configured timeout. */
            TIMED_OUT,
            /** The player left the server. */
            LOGGED_OUT
        }

        private final Reason reason;

        /**
         * Creates the event. Posted by Cozy Voice Chat; addons normally only listen to it.
         *
         * @param playerId the player
         * @param player   the player entity, if online
         * @param reason   why it ended
         */
        public Disconnected(UUID playerId, ServerPlayer player, Reason reason) {
            super(playerId, player);
            this.reason = reason;
        }

        /**
         * Why the session ended.
         *
         * @return the reason
         */
        public Reason getReason() {
            return reason;
        }
    }
}
