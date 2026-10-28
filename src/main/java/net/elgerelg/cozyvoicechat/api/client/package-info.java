/**
 * Client-side API for the local player, from {@link net.elgerelg.cozyvoicechat.api.CozyVoiceApi#client()}.
 *
 * <p><b>Side:</b> physical client only. Do not load these classes from common code that also runs
 * on a dedicated server without a {@code DistExecutor}/{@code FMLEnvironment.dist} guard.
 * <b>Threads:</b> getters are thread-safe; setters and screen methods must run on the client thread.</p>
 *
 * <p>Related events: {@link net.elgerelg.cozyvoicechat.api.event.client}.</p>
 *
 * @since 1.1.0
 */
package net.elgerelg.cozyvoicechat.api.client;
