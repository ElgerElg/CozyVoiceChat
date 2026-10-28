/**
 * Server-side API, from {@link net.elgerelg.cozyvoicechat.api.CozyVoiceApi#server()}: status queries,
 * mutes, {@link net.elgerelg.cozyvoicechat.api.server.VoiceEffect effects},
 * {@link net.elgerelg.cozyvoicechat.api.server.VoiceGroup groups} and addon
 * {@link net.elgerelg.cozyvoicechat.api.server.AudioChannel audio channels} played through the voice relay.
 *
 * <p><b>Side:</b> logical server (dedicated or integrated). <b>Threads:</b> methods are thread-safe
 * unless documented otherwise; {@link net.elgerelg.cozyvoicechat.api.server.AudioPlayer} feeds
 * channels from its own background thread.</p>
 *
 * <p>Related events: {@link net.elgerelg.cozyvoicechat.api.event}.</p>
 *
 * @since 1.1.0
 */
package net.elgerelg.cozyvoicechat.api.server;
