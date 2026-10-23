package net.elgerelg.cozyvoicechat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Регистрация forge-пакетов войсчата и отправка служебных пакетов.
 * Сам голос ходит по UDP — по forge-каналу только hello и состояние микрофона.
 */
@Mod.EventBusSubscriber(modid = CozyVoiceChat.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CozyVoiceNetwork {

    private static boolean registered = false;

    private CozyVoiceNetwork() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(CozyVoiceNetwork::init);
    }

    public static synchronized void init() {
        if (registered) return;
        try {
            CozyVoiceChat.addNetworkMessage(VoiceHelloPacket.class, VoiceHelloPacket::buffer, VoiceHelloPacket::new, VoiceHelloPacket::handler);
            CozyVoiceChat.addNetworkMessage(VoiceStatePacket.class, VoiceStatePacket::buffer, VoiceStatePacket::new, VoiceStatePacket::handler);
            registered = true;
        } catch (Exception ex) {
            CozyVoiceChat.LOGGER.error("[CozyVoice] Не удалось зарегистрировать пакеты", ex);
        }
    }

    public static void sendTo(ServerPlayer player, Object msg) {
        CozyVoiceChat.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToServer(Object msg) {
        CozyVoiceChat.PACKET_HANDLER.sendToServer(msg);
    }
}
