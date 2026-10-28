/**
 * Public API of Cozy Voice Chat: the entry point {@link net.elgerelg.cozyvoicechat.api.CozyVoiceApi},
 * the {@link net.elgerelg.cozyvoicechat.api.VoiceCategory audio categories} and the
 * {@link net.elgerelg.cozyvoicechat.api.VoiceRouteHook routing hook}.
 *
 * <p>Start with {@link net.elgerelg.cozyvoicechat.api.CozyVoiceApi#get()}:</p>
 * <ul>
 *     <li>{@link net.elgerelg.cozyvoicechat.api.server} &ndash; logical-server API (mutes, effects,
 *     groups, addon audio channels);</li>
 *     <li>{@link net.elgerelg.cozyvoicechat.api.client} &ndash; physical-client API (talking state,
 *     levels, local mute/deafen, volumes);</li>
 *     <li>{@link net.elgerelg.cozyvoicechat.api.event} and
 *     {@link net.elgerelg.cozyvoicechat.api.event.client} &ndash; Forge events on
 *     {@code MinecraftForge.EVENT_BUS};</li>
 *     <li>{@link net.elgerelg.cozyvoicechat.api.audio} &ndash; audio format and Opus codec.</li>
 * </ul>
 *
 * <p>Only {@code net.elgerelg.cozyvoicechat.api} and its subpackages are public API.
 * Everything else in the mod is internal and may change in any release.</p>
 *
 * @since 1.0.0
 */
package net.elgerelg.cozyvoicechat.api;
