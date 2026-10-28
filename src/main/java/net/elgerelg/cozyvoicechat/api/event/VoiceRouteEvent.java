package net.elgerelg.cozyvoicechat.api.event;

import net.elgerelg.cozyvoicechat.api.server.VoiceTarget;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

/**
 * A route (the list of listeners) was computed for a talking player. Posted on the
 * <b>server thread</b> about every 2 ticks while the player talks; the route is then used for
 * every frame until the next computation.
 *
 * <p>Fired after mutes, groups, route hooks and proximity were applied. It is not fired for
 * muted players. Edit {@link #getTargets()} to add or remove listeners, toggle effects for the
 * route, or cancel the event so nobody hears the player for this route period.</p>
 *
 * @since 1.1.0
 */
@Cancelable
public class VoiceRouteEvent extends Event {

    private final ServerPlayer sender;
    private final List<VoiceTarget> targets;
    private boolean radioEffect;
    private boolean echoEffect;

    /**
     * Creates the event. Posted by Cozy Voice Chat; addons normally only listen to it.
     *
     * @param sender      the talking player
     * @param targets     the mutable target list
     * @param radioEffect whether the radio effect is applied
     * @param echoEffect  whether the echo effect is applied
     */
    public VoiceRouteEvent(ServerPlayer sender, List<VoiceTarget> targets, boolean radioEffect, boolean echoEffect) {
        this.sender = sender;
        this.targets = targets;
        this.radioEffect = radioEffect;
        this.echoEffect = echoEffect;
    }

    /**
     * The talking player.
     *
     * @return the player
     */
    public ServerPlayer getSender() {
        return sender;
    }

    /**
     * Listeners for this route. Mutable.
     *
     * @return the target list
     */
    public List<VoiceTarget> getTargets() {
        return targets;
    }

    /**
     * Whether the radio effect is applied to this route.
     *
     * @return the flag
     */
    public boolean isRadioEffect() {
        return radioEffect;
    }

    /**
     * Applies or removes the radio effect for this route only.
     *
     * @param radioEffect the flag
     */
    public void setRadioEffect(boolean radioEffect) {
        this.radioEffect = radioEffect;
    }

    /**
     * Whether the echo effect is applied to this route.
     *
     * @return the flag
     */
    public boolean isEchoEffect() {
        return echoEffect;
    }

    /**
     * Applies or removes the echo effect for this route only.
     *
     * @param echoEffect the flag
     */
    public void setEchoEffect(boolean echoEffect) {
        this.echoEffect = echoEffect;
    }
}
