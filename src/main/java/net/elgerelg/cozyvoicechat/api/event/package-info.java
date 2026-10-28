/**
 * Server-side Forge events, posted on {@code MinecraftForge.EVENT_BUS}. Listen with
 * {@code @SubscribeEvent} like any other Forge event.
 *
 * <p><b>Side:</b> logical server. <b>Threads:</b> each event documents the thread it is posted on.
 * {@link net.elgerelg.cozyvoicechat.api.event.MicrophonePacketEvent} runs on a network thread;
 * the others run on the server thread.</p>
 *
 * @see net.elgerelg.cozyvoicechat.api.server.ServerVoiceApi
 * @since 1.1.0
 */
package net.elgerelg.cozyvoicechat.api.event;
