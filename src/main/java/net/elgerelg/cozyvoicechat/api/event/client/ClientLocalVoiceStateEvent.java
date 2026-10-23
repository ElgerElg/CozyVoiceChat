package net.elgerelg.cozyvoicechat.api.event.client;

import net.minecraftforge.eventbus.api.Event;

/**
 * The local player's microphone mute or deafen state was published (hotkey, settings screen or API).
 * Posted on the <b>client thread</b>.
 */
public class ClientLocalVoiceStateEvent extends Event {

    private final boolean micMuted;
    private final boolean deafened;

    /**
     * @param micMuted whether the mic is muted
     * @param deafened whether the player is deafened
     */
    public ClientLocalVoiceStateEvent(boolean micMuted, boolean deafened) {
        this.micMuted = micMuted;
        this.deafened = deafened;
    }

    /**
     * Microphone state.
     *
     * @return {@code true} if muted
     */
    public boolean isMicMuted() {
        return micMuted;
    }

    /**
     * Deafen state.
     *
     * @return {@code true} if deafened
     */
    public boolean isDeafened() {
        return deafened;
    }
}
