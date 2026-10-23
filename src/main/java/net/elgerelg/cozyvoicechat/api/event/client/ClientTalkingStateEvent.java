package net.elgerelg.cozyvoicechat.api.event.client;

import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * A player started or stopped talking, as heard by the local client (includes the local player).
 * Posted on the <b>client thread</b> at the end of a client tick, so it can lag by up to one tick.
 */
public class ClientTalkingStateEvent extends Event {

    private final UUID playerId;
    private final boolean talking;
    private final boolean localPlayer;

    /**
     * @param playerId    the player
     * @param talking     the new state
     * @param localPlayer whether it is the local player
     */
    public ClientTalkingStateEvent(UUID playerId, boolean talking, boolean localPlayer) {
        this.playerId = playerId;
        this.talking = talking;
        this.localPlayer = localPlayer;
    }

    /**
     * The player.
     *
     * @return the UUID
     */
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * The new state.
     *
     * @return {@code true} if they started talking
     */
    public boolean isTalking() {
        return talking;
    }

    /**
     * Whether this is the local player.
     *
     * @return {@code true} for the local player
     */
    public boolean isLocalPlayer() {
        return localPlayer;
    }
}
