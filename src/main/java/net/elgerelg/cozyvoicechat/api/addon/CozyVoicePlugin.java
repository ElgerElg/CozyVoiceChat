package net.elgerelg.cozyvoicechat.api.addon;

import net.elgerelg.cozyvoicechat.api.CozyVoiceApi;

/**
 * Entry point of a Cozy Voice Chat addon. Implement it in a class annotated with {@link CozyVoiceAddon}.
 *
 * <p>One instance is created per annotated class, on both physical sides (client and dedicated server).
 * Keep client-only code (rendering, {@code Minecraft} classes) out of this class or behind a
 * {@code FMLEnvironment.dist} check, otherwise a dedicated server will fail to load it.</p>
 *
 * @see CozyVoiceAddon
 * @since 1.1.0
 */
public interface CozyVoicePlugin {

    /**
     * Unique id of the addon, shown in logs and in {@code /cozyvoice addons}. Usually your mod id.
     * Use lowercase letters, digits, {@code _}, {@code -} and {@code .}. If two addons report the same id,
     * only the first one is registered.
     *
     * @return the addon id, not blank
     */
    String getId();

    /**
     * Called once when the addon is registered, during {@code FMLCommonSetupEvent} on the main thread.
     * Register event listeners, route hooks and similar here. No server is running yet, so server-only
     * actions (groups, channels, mutes) must wait for a server event such as {@code ServerStartedEvent}.
     *
     * <p>An exception thrown here is logged and the addon is not registered; it does not crash the game.</p>
     *
     * @param api the Cozy Voice Chat API, same as {@link CozyVoiceApi#get()}
     */
    default void onRegister(CozyVoiceApi api) {
    }
}
