package net.elgerelg.cozyvoicechat.api.server;

import java.util.Set;
import java.util.UUID;

/**
 * A voice group: members hear each other at full volume regardless of distance or level
 * (as {@link net.elgerelg.cozyvoicechat.api.VoiceCategory#GLOBAL}). A player is in at most one group.
 *
 * <p>In an <b>exclusive</b> group, members are heard <i>only</i> by the group: no proximity voice
 * and no route hooks. In a non-exclusive group, nearby non-members still hear members as usual.</p>
 *
 * <p>Groups exist in memory until removed or the server stops. Thread-safe.</p>
 */
public interface VoiceGroup {

    /**
     * Group id.
     *
     * @return the id
     */
    UUID getId();

    /**
     * Display name chosen by the addon.
     *
     * @return the name
     */
    String getName();

    /**
     * Whether members are heard only inside the group.
     *
     * @return {@code true} for an exclusive group
     */
    boolean isExclusive();

    /**
     * Changes the exclusive flag.
     *
     * @param exclusive the new value
     */
    void setExclusive(boolean exclusive);

    /**
     * Adds a member, moving them out of any other group.
     *
     * @param playerId the player
     */
    void addMember(UUID playerId);

    /**
     * Removes a member.
     *
     * @param playerId the player
     * @return {@code true} if the player was a member
     */
    boolean removeMember(UUID playerId);

    /**
     * Checks membership.
     *
     * @param playerId the player
     * @return {@code true} if the player is a member
     */
    boolean hasMember(UUID playerId);

    /**
     * Current members.
     *
     * @return an unmodifiable snapshot
     */
    Set<UUID> getMembers();

    /** Dissolves the group: all members leave and it can no longer be used. */
    void remove();

    /**
     * Whether {@link #remove()} was called.
     *
     * @return {@code true} if removed
     */
    boolean isRemoved();
}
