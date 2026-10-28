package net.elgerelg.cozyvoicechat.api.client;

/**
 * State of the local client's voice connection.
 *
 * @since 1.1.0
 */
public enum VoiceConnectionStatus {
    /** Not connected (not on a server, or the server has no voice). */
    DISCONNECTED,
    /** Handshake sent, waiting for the relay to answer. */
    CONNECTING,
    /** Voice is working. */
    CONNECTED
}
