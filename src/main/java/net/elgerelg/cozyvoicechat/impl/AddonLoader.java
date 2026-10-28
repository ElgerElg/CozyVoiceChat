package net.elgerelg.cozyvoicechat.impl;

import net.elgerelg.cozyvoicechat.CozyVoiceChat;
import net.elgerelg.cozyvoicechat.api.addon.AddonInfo;
import net.elgerelg.cozyvoicechat.api.addon.CozyVoiceAddon;
import net.elgerelg.cozyvoicechat.api.addon.CozyVoicePlugin;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.objectweb.asm.Type;

import java.lang.annotation.ElementType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Находит классы с {@link CozyVoiceAddon} в scan data всех модов Forge, создаёт их и вызывает
 * {@link CozyVoicePlugin#onRegister}. Ошибки одного аддона логируются и не мешают остальным.
 */
@Mod.EventBusSubscriber(modid = CozyVoiceChat.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AddonLoader {

    private static final Type ANNOTATION = Type.getType(CozyVoiceAddon.class);

    private static final Map<String, AddonInfo> ADDONS = new LinkedHashMap<>();
    private static volatile List<AddonInfo> snapshot = List.of();
    private static boolean loaded;

    private AddonLoader() {}

    /** @return зарегистрированные аддоны (неизменяемый снимок) */
    public static List<AddonInfo> addons() {
        return snapshot;
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(AddonLoader::discover);
    }

    static synchronized void discover() {
        if (loaded) return;
        loaded = true;
        for (ModFileScanData scan : ModList.get().getAllScanData()) {
            String modId = "unknown";
            String modVersion = "unknown";
            List<IModFileInfo> files = scan.getIModInfoData();
            if (!files.isEmpty() && !files.get(0).getMods().isEmpty()) {
                IModInfo mod = files.get(0).getMods().get(0);
                modId = mod.getModId();
                modVersion = mod.getVersion().toString();
            }
            for (ModFileScanData.AnnotationData a : scan.getAnnotations()) {
                if (!ANNOTATION.equals(a.annotationType()) || a.targetType() != ElementType.TYPE) continue;
                register(a.clazz().getClassName(), modId, modVersion);
            }
        }
        snapshot = Collections.unmodifiableList(new ArrayList<>(ADDONS.values()));
        if (ADDONS.isEmpty()) {
            CozyVoiceChat.LOGGER.info("[Cozy Voice] No addons found");
        } else {
            CozyVoiceChat.LOGGER.info("[Cozy Voice] Found {} addon(s): {}", ADDONS.size(), ADDONS.values().stream()
                    .map(i -> i.id() + " (mod " + i.modId() + " " + i.modVersion() + ")")
                    .collect(Collectors.joining(", ")));
        }
    }

    private static void register(String className, String modId, String modVersion) {
        try {
            Class<?> c = Class.forName(className, true, AddonLoader.class.getClassLoader());
            if (!CozyVoicePlugin.class.isAssignableFrom(c)) {
                CozyVoiceChat.LOGGER.warn("[Cozy Voice] Addon {} (mod {}) is annotated with @CozyVoiceAddon but does not implement CozyVoicePlugin, skipped",
                        className, modId);
                return;
            }
            if (c.isInterface() || Modifier.isAbstract(c.getModifiers())) {
                CozyVoiceChat.LOGGER.warn("[Cozy Voice] Addon {} (mod {}) is abstract, skipped", className, modId);
                return;
            }
            Constructor<?> ctor = c.getConstructor();
            CozyVoicePlugin plugin = (CozyVoicePlugin) ctor.newInstance();
            String id = plugin.getId();
            if (id == null || id.isBlank()) {
                CozyVoiceChat.LOGGER.warn("[Cozy Voice] Addon {} (mod {}) returned a blank id, skipped", className, modId);
                return;
            }
            if (ADDONS.containsKey(id)) {
                CozyVoiceChat.LOGGER.warn("[Cozy Voice] Duplicate addon id '{}' from {} (mod {}), already registered by {}, skipped",
                        id, className, modId, ADDONS.get(id).className());
                return;
            }
            plugin.onRegister(CozyVoiceApiImpl.INSTANCE);
            ADDONS.put(id, new AddonInfo(id, modId, modVersion, className));
        } catch (NoSuchMethodException ex) {
            CozyVoiceChat.LOGGER.warn("[Cozy Voice] Addon {} (mod {}) has no public no-argument constructor, skipped", className, modId);
        } catch (Throwable t) {
            CozyVoiceChat.LOGGER.error("[Cozy Voice] Failed to load addon {} (mod {})", className, modId, t);
        }
    }
}
