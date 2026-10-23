package net.elgerelg.cozyvoicechat.client;

import net.elgerelg.cozyvoicechat.CozyVoiceNetwork;
import net.elgerelg.cozyvoicechat.VoiceStatePacket;
import net.elgerelg.cozyvoicechat.api.event.client.ClientLocalVoiceStateEvent;
import net.minecraftforge.common.MinecraftForge;


import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Что клиент знает о состоянии войсчата других игроков: выключенный микрофон
 * и полная глушилка. Своё состояние отправляем на сервер при каждом изменении.
 */
public final class VoiceStateClient {

    private static final Map<UUID, Integer> FLAGS = new ConcurrentHashMap<>();

    private VoiceStateClient() {}

    public static void set(UUID player, int flags) {
        if (player == null) return;
        if (flags == 0) FLAGS.remove(player); else FLAGS.put(player, flags);
    }

    public static void clear() {
        FLAGS.clear();
    }

    public static boolean micMuted(UUID player) {
        return has(player, VoiceStatePacket.FLAG_MIC_MUTED);
    }

    public static boolean deafened(UUID player) {
        return has(player, VoiceStatePacket.FLAG_DEAFENED);
    }

    private static boolean has(UUID player, int flag) {
        Integer f = FLAGS.get(player);
        return f != null && (f & flag) != 0;
    }

    /** Сообщить серверу своё текущее состояние. */
    public static void publish() {
        CozyVoiceSettings cfg = CozyVoiceSettings.get();
        int flags = 0;
        if (cfg.micMuted) flags |= VoiceStatePacket.FLAG_MIC_MUTED;
        if (cfg.deafened) flags |= VoiceStatePacket.FLAG_DEAFENED;
        try {
            CozyVoiceNetwork.sendToServer(new VoiceStatePacket(new UUID(0, 0), flags));
        } catch (Throwable ignored) {
            // не подключены к серверу — состояние уедет при следующем входе
        }
        MinecraftForge.EVENT_BUS.post(new ClientLocalVoiceStateEvent(cfg.micMuted, cfg.deafened));
    }
}
