package net.elgerelg.cozyvoicechat.api;

/**
 * Audio categories carried with every voice frame. The category tells the client how to play it:
 * {@link #PROXIMITY} is attenuated by distance and panned; the other player categories play
 * "in the head" at full volume (times the frame gain).
 *
 * <p>Use these values in {@link VoiceRouteHook.Sink#send}, in
 * {@link net.elgerelg.cozyvoicechat.api.server.VoiceTarget} and when reading
 * {@link net.elgerelg.cozyvoicechat.api.event.client.ClientReceiveAudioEvent#getCategory()}.
 * Categories 4..15 are free for addons and play like {@link #GLOBAL}.</p>
 */
public final class VoiceCategory {

    /** Normal positional voice. */
    public static final byte PROXIMITY = 0;
    /** Global or group voice, no distance attenuation. */
    public static final byte GLOBAL = 1;
    /** Radio / walkie-talkie style voice, no distance attenuation. */
    public static final byte RADIO = 2;
    /** Voice heard through a camera or other remote device, no distance attenuation. */
    public static final byte CCTV = 3;

    /**
     * Client-side only: audio from a server {@code StaticAudioChannel}.
     * Not valid for routing.
     */
    public static final byte STATIC_SOURCE = 16;
    /**
     * Client-side only: audio from a server {@code LocationalAudioChannel}.
     * Not valid for routing.
     */
    public static final byte LOCATIONAL_SOURCE = 17;
    /**
     * Client-side only: audio from a server {@code EntityAudioChannel}.
     * Not valid for routing.
     */
    public static final byte ENTITY_SOURCE = 18;

    private VoiceCategory() {}

    /**
     * Tells whether a category comes from an addon audio channel rather than a player's microphone.
     *
     * @param category a category byte
     * @return {@code true} for {@link #STATIC_SOURCE}, {@link #LOCATIONAL_SOURCE} and {@link #ENTITY_SOURCE}
     */
    public static boolean isChannelSource(byte category) {
        return category >= STATIC_SOURCE && category <= ENTITY_SOURCE;
    }
}
