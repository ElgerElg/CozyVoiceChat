package net.elgerelg.cozyvoicechat.api.server;

import java.util.Locale;

/**
 * Built-in server-side voice effects. They are applied to everything the player says.
 *
 * @since 1.1.0
 */
public enum VoiceEffect {
    /** Band-limited, slightly distorted radio sound. */
    RADIO,
    /** Echo with feedback, like a large hall. */
    ECHO;

    /**
     * Name used in the {@code /cozyvoice effect} command.
     *
     * @return the lowercase id, e.g. {@code "radio"}
     */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
