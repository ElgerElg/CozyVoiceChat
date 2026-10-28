package net.elgerelg.cozyvoicechat.api.event;

import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * A player was muted or unmuted (by {@code /cozyvoice mute|unmute} or the API).
 * Posted on {@code MinecraftForge.EVENT_BUS} on the thread that changed the mute,
 * usually the server thread. Mutes that simply expire do not fire this event.
 *
 * @since 1.1.0
 */
public class VoiceMuteChangedEvent extends Event {

    private final UUID playerId;
    private final boolean muted;
    private final long durationSeconds;

    /**
     * Creates the event. Posted by Cozy Voice Chat; addons normally only listen to it.
     *
     * @param playerId        the player
     * @param muted           the new state
     * @param durationSeconds mute length, {@code -1} until restart, {@code 0} when unmuted
     */
    public VoiceMuteChangedEvent(UUID playerId, boolean muted, long durationSeconds) {
        this.playerId = playerId;
        this.muted = muted;
        this.durationSeconds = durationSeconds;
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
     * @return {@code true} if now muted
     */
    public boolean isMuted() {
        return muted;
    }

    /**
     * Mute length.
     *
     * @return seconds, {@code -1} until restart, {@code 0} when unmuted
     */
    public long getDurationSeconds() {
        return durationSeconds;
    }
}
