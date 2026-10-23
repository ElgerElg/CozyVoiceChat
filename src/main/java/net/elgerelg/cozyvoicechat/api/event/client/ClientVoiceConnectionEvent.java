package net.elgerelg.cozyvoicechat.api.event.client;

import net.elgerelg.cozyvoicechat.api.client.VoiceConnectionStatus;
import net.minecraftforge.eventbus.api.Event;

/** The local voice connection status changed. Posted on the <b>client thread</b>. */
public class ClientVoiceConnectionEvent extends Event {

    private final VoiceConnectionStatus status;

    /** @param status the new status */
    public ClientVoiceConnectionEvent(VoiceConnectionStatus status) {
        this.status = status;
    }

    /**
     * The new status.
     *
     * @return the status
     */
    public VoiceConnectionStatus getStatus() {
        return status;
    }
}
