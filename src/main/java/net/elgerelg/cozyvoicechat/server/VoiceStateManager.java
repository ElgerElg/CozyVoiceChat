package net.elgerelg.cozyvoicechat.server;

import net.elgerelg.cozyvoicechat.CozyVoiceNetwork;
import net.elgerelg.cozyvoicechat.VoiceStatePacket;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
