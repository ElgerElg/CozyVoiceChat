package net.elgerelg.cozyvoicechat.impl.client;

import net.elgerelg.cozyvoicechat.CozyVoiceChat;
import net.elgerelg.cozyvoicechat.api.client.VoiceConnectionStatus;
import net.elgerelg.cozyvoicechat.api.event.client.ClientTalkingStateEvent;
import net.elgerelg.cozyvoicechat.api.event.client.ClientVoiceConnectionEvent;
import net.elgerelg.cozyvoicechat.client.CozyVoiceClient;
import net.elgerelg.cozyvoicechat.client.MicCapture;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Раз в клиентский тик сравнивает состояние с прошлым и рассылает события API
 * (статус соединения, кто начал/перестал говорить) в клиентском потоке.
 */
@Mod.EventBusSubscriber(modid = CozyVoiceChat.MODID, value = Dist.CLIENT)
public final class ClientApiEvents {

    private static VoiceConnectionStatus lastStatus = VoiceConnectionStatus.DISCONNECTED;
    private static Set<UUID> lastTalking = Set.of();

    private ClientApiEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        VoiceConnectionStatus status = ClientVoiceApiImpl.map(CozyVoiceClient.status());
        if (status != lastStatus) {
            lastStatus = status;
            MinecraftForge.EVENT_BUS.post(new ClientVoiceConnectionEvent(status));
        }

        Minecraft mc = Minecraft.getInstance();
        UUID self = mc.player == null ? null : mc.player.getUUID();
        Set<UUID> now = new HashSet<>();
        if (mc.player != null && status != VoiceConnectionStatus.DISCONNECTED) {
            now.addAll(CozyVoiceClient.talkingNow());
            if (MicCapture.isGateOpen()) now.add(self);
        }
        if (now.equals(lastTalking)) return;
        for (UUID id : now) {
            if (!lastTalking.contains(id)) {
                MinecraftForge.EVENT_BUS.post(new ClientTalkingStateEvent(id, true, id.equals(self)));
            }
        }
        for (UUID id : lastTalking) {
            if (!now.contains(id)) {
                MinecraftForge.EVENT_BUS.post(new ClientTalkingStateEvent(id, false, id.equals(self)));
            }
        }
        lastTalking = now;
    }
}
