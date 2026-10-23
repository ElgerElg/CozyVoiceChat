package net.elgerelg.cozyvoicechat.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * A player turned their microphone off/on or deafened/undeafened themselves on their client.
 * Posted on the <b>server thread</b>.
 */
public class VoiceStateChangedEvent extends Event {

    private final ServerPlayer player;
    private final boolean micMuted;
    private final boolean deafened;

    /**
     * @param player   the player
     * @param micMuted whether their mic is now muted
     * @param deafened whether they are now deafened
     */
    public VoiceStateChangedEvent(ServerPlayer player, boolean micMuted, boolean deafened) {
        this.player = player;
        this.micMuted = micMuted;
        this.deafened = deafened;
    }

    /**
     * The player.
     *
     * @return the player
     */
    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * New microphone state.
     *
     * @return {@code true} if the mic is muted
     */
    public boolean isMicMuted() {
        return micMuted;
    }

    /**
     * New deafen state.
     *
     * @return {@code true} if deafened
     */
    public boolean isDeafened() {
        return deafened;
    }
}
