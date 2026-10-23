package net.elgerelg.cozyvoicechat.api.event;

import net.elgerelg.cozyvoicechat.api.server.VoiceTarget;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The server received one voice frame (20 ms of Opus audio) from a player.
 *
 * <p><b>Posted on a network thread</b>, about 50 times per second per talking player.
 * Do not touch the world or players here; keep listeners very fast. For routing with world
 * access use {@link VoiceRouteEvent} or a {@code VoiceRouteHook} instead.</p>
 *
 * <p>You can:</p>
 * <ul>
 *     <li>cancel the event to drop the frame;</li>
 *     <li>replace the audio with {@link #setOpusData(byte[])} (e.g. after decoding, processing
 *     and re-encoding it with the API codec);</li>
 *     <li>change who receives this frame with {@link #getTargets()};</li>
 *     <li>just read the audio (recording, speech detection).</li>
 * </ul>
 * Effects (radio/echo) are applied after this event.
 */
@Cancelable
public class MicrophonePacketEvent extends Event {

    private final UUID senderId;
    private final int sequence;
    private final List<VoiceTarget> originalTargets;
    private byte[] opusData;
    private List<VoiceTarget> editedTargets;

    /**
     * @param senderId the talking player
     * @param sequence packet sequence number
     * @param opusData the Opus frame
     * @param targets  the precomputed route for this frame
     */
    public MicrophonePacketEvent(UUID senderId, int sequence, byte[] opusData, List<VoiceTarget> targets) {
        this.senderId = senderId;
        this.sequence = sequence;
        this.opusData = opusData;
        this.originalTargets = targets;
    }

    /**
     * The talking player.
     *
     * @return the UUID
     */
    public UUID getSenderId() {
        return senderId;
    }

    /**
     * Sequence number of the packet, increasing by one per frame; gaps mean lost packets.
     *
     * @return the sequence number
     */
    public int getSequence() {
        return sequence;
    }

    /**
     * The Opus frame (48 kHz mono, 20 ms). Do not modify the array; use {@link #setOpusData}.
     *
     * @return the frame
     */
    public byte[] getOpusData() {
        return opusData;
    }

    /**
     * Replaces the Opus frame that will be relayed.
     *
     * @param opusData the new frame, not empty
     */
    public void setOpusData(byte[] opusData) {
        if (opusData == null || opusData.length == 0) throw new IllegalArgumentException("opusData");
        this.opusData = opusData;
    }

    /**
     * Recipients of this frame. The returned list is mutable: add, remove or replace targets
     * to change routing for this frame only. Empty while the route is not computed yet
     * (the first frame or two after the player starts talking).
     *
     * @return the mutable target list
     */
    public List<VoiceTarget> getTargets() {
        if (editedTargets == null) editedTargets = new ArrayList<>(originalTargets);
        return editedTargets;
    }

    /**
     * Whether {@link #getTargets()} was called, i.e. routing may have been changed.
     *
     * @return {@code true} if the target list was accessed for editing
     */
    public boolean areTargetsModified() {
        return editedTargets != null;
    }
}
