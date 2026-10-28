/**
 * Client-side Forge events, posted on {@code MinecraftForge.EVENT_BUS} on the physical client only.
 *
 * <p><b>Threads:</b> each event documents the thread it is posted on.
 * {@link net.elgerelg.cozyvoicechat.api.event.client.ClientMicrophoneEvent} runs on the microphone
 * thread and {@link net.elgerelg.cozyvoicechat.api.event.client.ClientReceiveAudioEvent} on the playback
 * thread; keep those listeners fast and do not touch the world from them. The rest run on the
 * client thread.</p>
 *
 * @see net.elgerelg.cozyvoicechat.api.client.ClientVoiceApi
 * @since 1.1.0
 */
package net.elgerelg.cozyvoicechat.api.event.client;
