package net.elgerelg.cozyvoicechat.server;

import net.elgerelg.cozyvoicechat.CozyVoiceChat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Жизненный цикл UDP voice-сервера. Срабатывает и на dedicated-сервере (Mohist),
 * и на встроенном сервере одиночной игры.
 */
@Mod.EventBusSubscriber(modid = CozyVoiceChat.MODID)
public final class CozyVoiceServerEvents {

    private CozyVoiceServerEvents() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent e) {
        CozyVoiceServer.start(e.getServer());
    }

    /**
     * Раз в тик: разбираем накопленные голосовые кадры и, в одиночной игре,
     * проверяем, не открыли ли мир для локальной сети.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        if (srv != null) CozyVoiceServer.tick(srv);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        CozyVoiceServer.stop();
        VoiceGroupManager.clear();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            CozyVoiceRouter.clear(sp.getUUID());
            CozyVoiceServer.onPlayerJoin(sp);
            VoiceStateManager.syncTo(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent e) {
        CozyVoiceServer.onPlayerLeave(e.getEntity().getUUID());
        VoiceStateManager.remove(e.getEntity().getUUID());
        VoiceEffectManager.clear(e.getEntity().getUUID());
        SelfMonitorManager.clear(e.getEntity().getUUID());
        VoiceMuteManager.view(); // муты переживают релог (до рестарта сервера)
    }
}
