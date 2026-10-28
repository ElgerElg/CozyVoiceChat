package net.elgerelg.cozyvoicechat.api.event.client;

import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * The local microphone produced a frame that is about to be encoded and sent to the server.
 * Posted on the <b>microphone thread</b>, 50 times per second while transmitting. Keep it fast.
 *
 * <p>The PCM array (960 samples, 48 kHz mono) is already gain-adjusted and noise-suppressed.
 * Modify it in place to change what others hear (voice changers, filters), read it to record,
 * or cancel the event to not send this frame.</p>
 *
 * @since 1.1.0
 */
@Cancelable
public class ClientMicrophoneEvent extends Event {

    private final short[] pcm;

    /**
     * Creates the event. Posted by Cozy Voice Chat; addons normally only listen to it.
     *
     * @param pcm the frame, modified in place by listeners
     */
    public ClientMicrophoneEvent(short[] pcm) {
        this.pcm = pcm;
    }

    /**
     * The frame. Changes made to this array are sent.
     *
     * @return the 960-sample PCM array
     */
    public short[] getPcm() {
        return pcm;
    }
}
