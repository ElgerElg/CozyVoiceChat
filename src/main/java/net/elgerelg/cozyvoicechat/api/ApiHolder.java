package net.elgerelg.cozyvoicechat.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Internal holder behind {@link CozyVoiceApi#get()}. Not part of the public API. */
final class ApiHolder {

    static final String IMPL_CLASS = "net.elgerelg.cozyvoicechat.impl.CozyVoiceApiImpl";
    static final List<VoiceRouteHook> HOOKS = new CopyOnWriteArrayList<>();

    private static volatile CozyVoiceApi instance;

    private ApiHolder() {}

    static CozyVoiceApi get() {
        CozyVoiceApi i = instance;
        if (i != null) return i;
        synchronized (ApiHolder.class) {
            if (instance == null) {
                try {
                    Class<?> c = Class.forName(IMPL_CLASS, true, ApiHolder.class.getClassLoader());
                    instance = (CozyVoiceApi) c.getField("INSTANCE").get(null);
                } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
                    throw new IllegalStateException(
                            "Cozy Voice Chat is not installed: only the API jar is on the classpath", e);
                }
            }
            return instance;
        }
    }
}
