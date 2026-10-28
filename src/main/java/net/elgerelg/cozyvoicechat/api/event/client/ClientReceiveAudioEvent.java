package net.elgerelg.cozyvoicechat.api.event.client;

import net.elgerelg.cozyvoicechat.api.VoiceCategory;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * A frame of incoming audio was decoded and is about to be mixed into the output.
 * Posted on the <b>playback thread</b> for every speaker and audio channel, 50 times per second each.
 * Not posted while the local player is deafened. Keep listeners fast.
 *
 * <p>Modify {@link #getPcm()} in place (before volume and panning are applied) or cancel the
 * event to skip this frame.</p>
 *
 * @since 1.1.0
 */
@Cancelable
public class ClientReceiveAudioEvent extends Event {

    private final UUID sourceId;
    private final byte category;
    private final short[] pcm;

    /**
     * Creates the event. Posted by Cozy Voice Chat; addons normally only listen to it.
     *
     * @param sourceId speaking player or audio channel id
     * @param category the category
     * @param pcm      the decoded frame
     */
    public ClientReceiveAudioEvent(UUID sourceId, byte category, short[] pcm) {
        this.sourceId = sourceId;
        this.category = category;
        this.pcm = pcm;
    }

    /**
     * Speaking player's UUID, or the audio channel id for addon audio.
     *
     * @return the id
     */
    public UUID getSourceId() {
        return sourceId;
    }

    /**
     * Category of the audio, see {@link VoiceCategory}.
     *
     * @return the category
     */
    public byte getCategory() {
        return category;
    }

    /**
     * Whether this is a player's voice and not an addon audio channel.
     *
     * @return {@code true} for player voice
     */
    public boolean isPlayerVoice() {
        return !VoiceCategory.isChannelSource(category);
    }

    /**
     * The decoded frame (960 samples, 48 kHz mono). Changes made to it are played.
     *
     * @return the PCM array
     */
    public short[] getPcm() {
        return pcm;
    }
}
