package net.elgerelg.cozyvoicechat.api.server;

import net.minecraft.world.entity.Entity;

/**
 * Audio that follows an entity (a talking mob, a boombox carried by a player, a minecart radio).
 * Players near the entity hear it positionally; clients track the entity's position themselves,
 * so the sound moves smoothly. Nothing plays once the entity is removed.
 */
public interface EntityAudioChannel extends AudioChannel {

    /**
     * The entity the sound is attached to.
     *
     * @return the entity
     */
    Entity getEntity();

    /**
     * Hearing distance in blocks.
     *
     * @return the distance; by default the server voice range
     */
    float getDistance();

    /**
     * Sets the hearing distance.
     *
     * @param distance blocks, at least 1
     */
    void setDistance(float distance);
}
