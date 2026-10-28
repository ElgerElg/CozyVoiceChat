package net.elgerelg.cozyvoicechat.api.server;

import java.util.UUID;

/**
 * One recipient of a player's voice, as used by
 * {@link net.elgerelg.cozyvoicechat.api.event.MicrophonePacketEvent#getTargets()} and
 * {@link net.elgerelg.cozyvoicechat.api.event.VoiceRouteEvent#getTargets()}. Immutable.
 *
 * @param playerId the listener
 * @param category how the client plays it, see {@link net.elgerelg.cozyvoicechat.api.VoiceCategory}
 * @param gain     volume multiplier, 0..1
 *
 * @since 1.1.0
 */
public record VoiceTarget(UUID playerId, byte category, float gain) {

    /**
     * Creates a target, clamping the gain to 0..1.
     *
     * @param playerId the listener, not {@code null}
     * @param category the category
     * @param gain     the gain
     * @throws IllegalArgumentException if {@code playerId} is {@code null}
     */
    public VoiceTarget {
        if (playerId == null) throw new IllegalArgumentException("playerId");
        gain = Math.max(0f, Math.min(1f, gain));
    }
}
