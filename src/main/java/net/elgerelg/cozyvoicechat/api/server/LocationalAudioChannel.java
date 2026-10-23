package net.elgerelg.cozyvoicechat.api.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Audio coming from a fixed point in a level (speakers, jukeboxes, alarms). Every player in the same
 * level and within {@link #getDistance()} blocks hears it, attenuated and panned like a player's voice.
 */
public interface LocationalAudioChannel extends AudioChannel {

    /**
     * Level the sound plays in.
     *
     * @return the level
     */
    ServerLevel getLevel();

    /**
     * Position of the sound.
     *
     * @return the position
     */
    Vec3 getPosition();

    /**
     * Moves the sound.
     *
     * @param level    the level
     * @param position the new position
     */
    void setPosition(ServerLevel level, Vec3 position);

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
