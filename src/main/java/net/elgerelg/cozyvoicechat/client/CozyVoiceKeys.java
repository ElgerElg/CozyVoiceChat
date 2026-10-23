package net.elgerelg.cozyvoicechat.client;

import net.elgerelg.cozyvoicechat.CozyVoiceChat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Кейбинды войсчата (по образцу CmdReportKeybinds):
 *  T   — режим рации, push-to-talk (удерживать). Caps Lock не годится: это
 *        клавиша-переключатель, и GLFW отдаёт её состояние нестабильно.
 *  V   — меню настроек войсчата
 */
public final class CozyVoiceKeys {

    /** Своя категория в настройках управления, отдельно от остального мода. */
    private static final String CATEGORY = "key.categories.cozyvoicechat";

    private static KeyMapping openMenuKey;
    private static KeyMapping pttKey;
    private static KeyMapping muteMicKey;
    private static KeyMapping deafenKey;
    private static KeyMapping playersKey;

    private CozyVoiceKeys() {}

    /** Текущая привязка PTT для подсказок в интерфейсе. */
    public static String pttKeyName() {
        return pttKey == null ? "?" : pttKey.getTranslatedKeyMessage().getString();
    }

    public static KeyMapping ptt() {
        return pttKey;
    }

    /**
     * Переназначить PTT. Пишем в тот же KeyMapping, что показан в ванильных
     * настройках управления, поэтому привязка одна на оба места.
     */
    public static void setPttKey(InputConstants.Key key) {
        if (pttKey == null) return;
        Minecraft mc = Minecraft.getInstance();
        pttKey.setKey(key);
        KeyMapping.resetMapping();
        mc.options.save();
    }

    /**
     * Зажата ли клавиша рации прямо сейчас.
     *
     * Читаем состояние клавиши у окна напрямую, а не через KeyMapping.isDown():
     * при открытом экране ванильные привязки не срабатывают, а рация должна
     * работать и в чате, и в любом меню.
     */
    public static boolean pttHeld() {
        if (pttKey == null) return false;
        InputConstants.Key key = pttKey.getKey();
        long win = Minecraft.getInstance().getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> key.getValue() != GLFW.GLFW_KEY_UNKNOWN
                    && InputConstants.isKeyDown(win, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(win, key.getValue()) == GLFW.GLFW_PRESS;
            default -> pttKey.isDown();
        };
    }

    @Mod.EventBusSubscriber(modid = CozyVoiceChat.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Mod_ {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            openMenuKey = new KeyMapping(
                    "key.cozyvoicechat.menu",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_V),
                    CATEGORY);
            event.register(openMenuKey);

            // UNIVERSAL, а не IN_GAME: иначе ваниль считает привязку конфликтной
            // с элементами интерфейса и подсвечивает её красным.
            pttKey = new KeyMapping(
                    "key.cozyvoicechat.ptt",
                    KeyConflictContext.UNIVERSAL,
                    InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_T),
                    CATEGORY);
            event.register(pttKey);

            muteMicKey = new KeyMapping(
                    "key.cozyvoicechat.mute_mic",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_M),
                    CATEGORY);
            event.register(muteMicKey);

            deafenKey = new KeyMapping(
                    "key.cozyvoicechat.deafen",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_N),
                    CATEGORY);
            event.register(deafenKey);

            playersKey = new KeyMapping(
                    "key.cozyvoicechat.players",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_P),
                    CATEGORY);
            event.register(playersKey);
        }
    }

    @Mod.EventBusSubscriber(modid = CozyVoiceChat.MODID, value = Dist.CLIENT)
    public static final class Forge_ {

        /** F3 + V — оверлей отладки войса, как дебаг-экраны ванили. */
        @SubscribeEvent
        public static void onKey(InputEvent.Key event) {
            if (event.getAction() != GLFW.GLFW_PRESS || event.getKey() != GLFW.GLFW_KEY_V) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null || mc.player == null) return;
            long win = mc.getWindow().getWindow();
            if (InputConstants.isKeyDown(win, GLFW.GLFW_KEY_F3)) {
                CozyVoiceClientEvents.toggleDebug();
                mc.player.displayClientMessage(Component.translatable("cozyvoicechat.message",
                        Component.translatable(CozyVoiceClientEvents.debugEnabled()
                                ? "cozyvoicechat.toggle.debug_on" : "cozyvoicechat.toggle.debug_off")), true);
            }
        }

        /** Один раз за запуск: освободить клавишу нашего меню от ванильной привязки. */
        private static boolean socialChecked;

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (event.phase == TickEvent.Phase.START) {
                // Клики вычитывает Minecraft.tick(), поэтому ванильное меню надо
                // перехватить до неё — в END было бы уже поздно.
                freeSocialKey(mc);
                suppressSocialClicks(mc);
                return;
            }
            // Рация опрашивается напрямую, поэтому работает и поверх меню.
            // Единственное исключение — окно переназначения самой клавиши.
            CozyVoiceClient.setPtt(!(mc.screen instanceof CozyVoiceScreen s && s.isBinding()) && pttHeld());
            if (muteMicKey != null) {
                while (muteMicKey.consumeClick()) toggleMic(mc);
            }
            if (deafenKey != null) {
                while (deafenKey.consumeClick()) toggleDeafen(mc);
            }

            if (playersKey != null) {
                while (playersKey.consumeClick()) {
                    if (mc.screen != null || mc.player == null) continue;
                    mc.setScreen(new VoicePlayersScreen());
                }
            }

            if (openMenuKey == null) return;
            // F3 зажат — это комбинация для отладочного оверлея, и нажатие уже
            // обработано в onKey. Клик всё равно вычитываем, иначе он всплывёт
            // следующим тиком и меню откроется с опозданием.
            boolean debugCombo = InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_F3);
            while (openMenuKey.consumeClick()) {
                if (debugCombo || mc.screen != null || mc.player == null) continue;
                mc.setScreen(new CozyVoiceScreen());
            }
        }

        /**
         * P по умолчанию занят ванильными «Социальными взаимодействиями». Снимаем
         * эту привязку, чтобы клавиша досталась меню громкости игроков и в
         * настройках управления не горел конфликт.
         */
        private static void freeSocialKey(Minecraft mc) {
            if (socialChecked || playersKey == null) return;
            socialChecked = true;

            CozyVoiceSettings cfg = CozyVoiceSettings.get();
            if (cfg.socialKeyFreed) return;

            KeyMapping social = mc.options.keySocialInteractions;
            if (social == null || !social.getKey().equals(playersKey.getKey())) return;

            social.setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
            mc.options.save();
            cfg.socialKeyFreed = true;
            CozyVoiceSettings.save();
        }

        /**
         * Игрок мог вернуть ванильную привязку вручную. Тогда клавиша всё равно
         * остаётся за нами: иначе одно нажатие открывало бы сразу два экрана.
         */
        private static void suppressSocialClicks(Minecraft mc) {
            if (playersKey == null) return;
            KeyMapping social = mc.options.keySocialInteractions;
            if (social == null || !social.getKey().equals(playersKey.getKey())) return;
            if (social.getKey().equals(InputConstants.UNKNOWN)) return;
            social.setDown(false);
            while (social.consumeClick()) {
            }
        }

        private static void toggleMic(Minecraft mc) {
            CozyVoiceSettings cfg = CozyVoiceSettings.get();
            cfg.micMuted = !cfg.micMuted;
            afterToggle(mc, cfg.micMuted ? "cozyvoicechat.toggle.mic_muted" : "cozyvoicechat.toggle.mic_unmuted");
        }

        /**
         * Глушилка выключает и приём, и передачу: молчащий слушатель — это
         * отдельный режим, а здесь игрок выпадает из разговора целиком.
         */
        private static void toggleDeafen(Minecraft mc) {
            CozyVoiceSettings cfg = CozyVoiceSettings.get();
            cfg.deafened = !cfg.deafened;
            afterToggle(mc, cfg.deafened ? "cozyvoicechat.toggle.deafened" : "cozyvoicechat.toggle.undeafened");
        }

        private static void afterToggle(Minecraft mc, String messageKey) {
            CozyVoiceSettings.save();
            VoiceStateClient.publish();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("cozyvoicechat.message", Component.translatable(messageKey)), true);
            }
        }
    }
}
