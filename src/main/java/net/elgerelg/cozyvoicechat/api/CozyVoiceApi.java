package net.elgerelg.cozyvoicechat.api;

import net.elgerelg.cozyvoicechat.api.audio.VoiceDecoder;
import net.elgerelg.cozyvoicechat.api.audio.VoiceEncoder;
import net.elgerelg.cozyvoicechat.api.client.ClientVoiceApi;
import net.elgerelg.cozyvoicechat.api.server.ServerVoiceApi;

import java.util.List;

/**
 * Entry point of the Cozy Voice Chat addon API.
 *
 * <p>Get the instance with {@link #get()} at any time after mod construction
 * (for example in {@code FMLCommonSetupEvent}, a server event or a client tick).
 * Everything else is reached from it:</p>
 * <ul>
 *     <li>{@link #server()}: queries and actions on the logical server (mutes, effects,
 *     groups, custom audio channels);</li>
 *     <li>{@link #client()}: the local client (talking state, levels, local mute/deafen);</li>
 *     <li>{@link #createEncoder()} / {@link #createDecoder()}: the Opus codec used by the mod.</li>
 * </ul>
 *
 * <p>Events are regular Forge events posted on {@code MinecraftForge.EVENT_BUS}; see the
 * {@code net.elgerelg.cozyvoicechat.api.event} packages.</p>
 *
 * <p>If Cozy Voice Chat is an optional dependency, guard calls with
 * {@code ModList.get().isLoaded(CozyVoiceApi.MOD_ID)}.</p>
 */
public interface CozyVoiceApi {

    /**
     * Version of the API this jar was compiled from. It increases whenever the API changes.
     * Compare it with {@link #getApiVersion()} to detect an older mod at runtime.
     */
    int API_VERSION = 1;

    /** Mod id of Cozy Voice Chat, for {@code mods.toml} dependencies and {@code ModList} checks. */
    String MOD_ID = "cozyvoicechat";

    /**
     * Returns the API instance.
     *
     * @return the API, never {@code null}
     * @throws IllegalStateException if only the API jar is present and the mod itself is not installed
     */
    static CozyVoiceApi get() {
        return ApiHolder.get();
    }

    /**
     * Checks whether the full mod (not only the API jar) is present.
     *
     * @return {@code true} if {@link #get()} will succeed
     */
    static boolean isAvailable() {
        try {
            get();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * API version implemented by the installed mod.
     *
     * @return the runtime API version, at least 1
     */
    int getApiVersion();

    /**
     * Version of the installed mod, as written in its {@code mods.toml}.
     *
     * @return the mod version, e.g. {@code "1.1.0"}
     */
    String getModVersion();

    /**
     * Server-side API. Usable on a dedicated server and on the integrated server.
     * Methods that need a running server return neutral values when none is running.
     *
     * @return the server API, never {@code null}
     */
    ServerVoiceApi server();

    /**
     * Client-side API for the local player.
     *
     * @return the client API, never {@code null}
     * @throws IllegalStateException when called on a dedicated server
     */
    ClientVoiceApi client();

    /**
     * Creates an Opus encoder with the mod's audio format and the server's configured bitrate
     * (32 kbit/s if no server is running).
     *
     * @return a new encoder; each instance holds stream state, so use one per audio stream
     */
    VoiceEncoder createEncoder();

    /**
     * Creates an Opus encoder with a specific bitrate.
     *
     * @param bitrate bits per second, clamped to 8000..96000
     * @return a new encoder
     */
    VoiceEncoder createEncoder(int bitrate);

    /**
     * Creates an Opus decoder with the mod's audio format.
     *
     * @return a new decoder; use one per incoming stream
     */
    VoiceDecoder createDecoder();

    // ------------------------------------------------------------------
    // Route hooks (API 0 / mod 1.0.0, kept for backwards compatibility)
    // ------------------------------------------------------------------

    /**
     * Registers a server-side routing hook. Registering the same hook twice has no effect.
     * Same as {@code CozyVoiceApi.get().server().registerRouteHook(hook)}.
     *
     * @param hook the hook; {@code null} is ignored
     */
    static void registerRouteHook(VoiceRouteHook hook) {
        if (hook != null && !ApiHolder.HOOKS.contains(hook)) ApiHolder.HOOKS.add(hook);
    }

    /**
     * Removes a previously registered routing hook.
     *
     * @param hook the hook to remove
     */
    static void unregisterRouteHook(VoiceRouteHook hook) {
        ApiHolder.HOOKS.remove(hook);
    }

    /**
     * All registered routing hooks, in registration order.
     *
     * @return a live, thread-safe list; prefer {@link #registerRouteHook} over modifying it
     */
    static List<VoiceRouteHook> routeHooks() {
        return ApiHolder.HOOKS;
    }
}
