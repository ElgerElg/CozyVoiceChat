package net.elgerelg.cozyvoicechat.api.event.client;

import net.minecraftforge.eventbus.api.Event;

/**
 * The local voice settings were changed and saved (devices, mode, volumes, player volumes...).
 * Read the new values from {@code CozyVoiceApi.get().client()}. Posted on the thread that saved
 * the settings, normally the <b>client thread</b>.
 */
public class ClientVoiceSettingsChangedEvent extends Event {

    /** Creates the event. */
    public ClientVoiceSettingsChangedEvent() {}
}
