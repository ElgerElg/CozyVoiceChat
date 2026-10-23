package net.elgerelg.cozyvoicechat.api.server;

import java.util.Set;
import java.util.UUID;

/**
 * Audio without a position, played "in the head" of the chosen listeners at full volume
 * (phone calls, announcements, background music).
 *
 * <p>It plays to the players added with {@link #addTarget(UUID)}. If a filter is set with
 * {@link #setFilter}, every online player matching the filter hears it as well.</p>
 */
public interface StaticAudioChannel extends AudioChannel {

    /**
     * Adds a listener.
     *
     * @param playerId the player
     */
    void addTarget(UUID playerId);

    /**
     * Removes a listener.
     *
     * @param playerId the player
     * @return {@code true} if the player was a listener
     */
    boolean removeTarget(UUID playerId);

    /**
     * Explicit listeners of this channel.
     *
     * @return an unmodifiable snapshot
     */
    Set<UUID> getTargets();

    /** Removes all explicit listeners. */
    void clearTargets();
}
