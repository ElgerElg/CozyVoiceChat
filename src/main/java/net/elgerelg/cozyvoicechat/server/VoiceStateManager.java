package net.elgerelg.cozyvoicechat.server;

import net.elgerelg.cozyvoicechat.CozyVoiceNetwork;
import net.elgerelg.cozyvoicechat.VoiceStatePacket;

import net.elgerelg.cozyvoicechat.api.event.VoiceStateChangedEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.common.MinecraftForge;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный реестр состояний войсчата (микрофон выключен / полная глушилка).
 * Хранится ровно для того, чтобы раздать состояние тем, кто зашёл позже.
 */
public final class VoiceStateManager {

    private static final Map<UUID, Integer> FLAGS = new ConcurrentHashMap<>();

    private VoiceStateManager() {}

    public static void set(MinecraftServer srv, UUID playerId, int flags) {
        if (srv == null || playerId == null) return;
        Integer old = FLAGS.put(playerId, flags);
        if (old != null && old == flags) return;
        VoiceStatePacket msg = new VoiceStatePacket(playerId, flags);
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (!p.getUUID().equals(playerId)) CozyVoiceNetwork.sendTo(p, msg);
        }
        if (old == null && flags == 0) return; // первый отчёт после входа: ничего не поменялось
        ServerPlayer who = srv.getPlayerList().getPlayer(playerId);
        if (who != null) {
            MinecraftForge.EVENT_BUS.post(new VoiceStateChangedEvent(who,
                    (flags & VoiceStatePacket.FLAG_MIC_MUTED) != 0, (flags & VoiceStatePacket.FLAG_DEAFENED) != 0));
        }
    }

    /** Флаги игрока (см. VoiceStatePacket), 0 если неизвестны. */
    public static int flags(UUID playerId) {
        Integer f = playerId == null ? null : FLAGS.get(playerId);
        return f == null ? 0 : f;
    }

    /** Новичку — состояния всех остальных. */
    public static void syncTo(ServerPlayer target) {
        for (Map.Entry<UUID, Integer> e : FLAGS.entrySet()) {
            if (e.getKey().equals(target.getUUID())) continue;
            CozyVoiceNetwork.sendTo(target, new VoiceStatePacket(e.getKey(), e.getValue()));
        }
    }

    public static void remove(UUID playerId) {
        FLAGS.remove(playerId);
    }
}
