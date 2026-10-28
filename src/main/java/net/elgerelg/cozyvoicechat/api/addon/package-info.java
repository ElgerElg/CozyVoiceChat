/**
 * Addon discovery: annotate a {@link net.elgerelg.cozyvoicechat.api.addon.CozyVoicePlugin} implementation with
 * {@link net.elgerelg.cozyvoicechat.api.addon.CozyVoiceAddon} and Cozy Voice Chat finds, creates and
 * registers it during common setup.
 *
 * <p><b>Side:</b> both. <b>Threads:</b> plugins are registered on the main thread during
 * {@code FMLCommonSetupEvent}.</p>
 *
 * @since 1.1.0
 */
package net.elgerelg.cozyvoicechat.api.addon;
