package net.elgerelg.cozyvoicechat.api.server;

import java.util.UUID;

/**
 * One recipient of a player's voice.
 *
 * @param playerId the listener
 * @param category how the client plays it, see {@link net.elgerelg.cozyvoicechat.api.VoiceCategory}
 * @param gain     volume multiplier, 0..1
 */
public record VoiceTarget(UUID playerId, byte category, float gain) {

    /**
     * Creates a target, clamping the gain to 0..1.
     *
     * @param playerId the listener, not {@code null}
     * @param category the category
     * @param gain     the gain
     */
    public VoiceTarget {
        if (playerId == null) throw new IllegalArgumentException("playerId");
        gain = Math.max(0f, Math.min(1f, gain));
    }
}
