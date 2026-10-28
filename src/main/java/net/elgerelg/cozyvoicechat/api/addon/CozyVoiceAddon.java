package net.elgerelg.cozyvoicechat.api.addon;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a Cozy Voice Chat addon entry point, so the mod finds it automatically.
 *
 * <p>The annotated class must:</p>
 * <ul>
 *     <li>implement {@link CozyVoicePlugin};</li>
 *     <li>be a public, non-abstract top-level (or static nested) class;</li>
 *     <li>have a public no-argument constructor;</li>
 *     <li>live in a Forge mod jar (it is found through Forge's annotation scan data of all loaded mod files,
 *     {@code ModList.get().getAllScanData()}).</li>
 * </ul>
 *
 * <p>During {@code FMLCommonSetupEvent} (on the main thread, through {@code enqueueWork}) Cozy Voice Chat
 * scans every mod file for this annotation, creates one instance of each class, calls
 * {@link CozyVoicePlugin#onRegister(net.elgerelg.cozyvoicechat.api.CozyVoiceApi)} and logs the list of
 * addons. Registered addons are listed by {@code /cozyvoice addons} and by
 * {@link net.elgerelg.cozyvoicechat.api.CozyVoiceApi#getAddons()}.</p>
 *
 * <pre>{@code
 * @CozyVoiceAddon
 * public final class MyVoiceAddon implements CozyVoicePlugin {
 *     @Override
 *     public String getId() {
 *         return "myaddon";
 *     }
 *
 *     @Override
 *     public void onRegister(CozyVoiceApi api) {
 *         MinecraftForge.EVENT_BUS.register(MyVoiceEvents.class);
 *     }
 * }
 * }</pre>
 *
 * <p>The class is only loaded when Cozy Voice Chat is installed, so it is a safe place for API calls
 * in an addon that has Cozy Voice Chat as an optional dependency.</p>
 *
 * <p>Using the annotation is optional: addons can still just call {@link net.elgerelg.cozyvoicechat.api.CozyVoiceApi#get()}
 * and subscribe to events; they are simply not listed as addons then.</p>
 *
 * @see CozyVoicePlugin
 * @see AddonInfo
 * @since 1.1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CozyVoiceAddon {
}
