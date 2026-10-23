package net.elgerelg.cozyvoicechat.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Публичное API Cozy Voice Chat для интеграции из других модов. */
public final class CozyVoiceApi {

    private static final List<VoiceRouteHook> HOOKS = new CopyOnWriteArrayList<>();

    private CozyVoiceApi() {}

    public static void registerRouteHook(VoiceRouteHook hook) {
        if (hook != null && !HOOKS.contains(hook)) HOOKS.add(hook);
    }

    public static void unregisterRouteHook(VoiceRouteHook hook) {
        HOOKS.remove(hook);
    }

    public static List<VoiceRouteHook> routeHooks() {
        return HOOKS;
    }
}
