package net.elgerelg.cozyvoicechat.server;

import net.elgerelg.cozyvoicechat.api.event.VoiceMuteChangedEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Простые муты голосового чата (в памяти, до перезапуска сервера).
 * Упрощённая замена VoiceMuteManager из Union-MC SCP-RP, у которого
 * были публичные/тихие муты, сохранение в мир и синхронизация HUD.
 */
public final class VoiceMuteManager {

    /** playerId -> момент окончания мута (мс), Long.MAX_VALUE = навсегда. */
    private static final Map<UUID, Long> MUTES = new ConcurrentHashMap<>();

    private VoiceMuteManager() {}

    public static void mute(UUID playerId, long seconds) {
        if (playerId == null) return;
        long until = seconds <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + seconds * 1000L;
        MUTES.put(playerId, until);
        MinecraftForge.EVENT_BUS.post(new VoiceMuteChangedEvent(playerId, true, seconds <= 0 ? -1 : seconds));
    }

    public static boolean unmute(UUID playerId) {
        boolean removed = playerId != null && MUTES.remove(playerId) != null;
        if (removed) MinecraftForge.EVENT_BUS.post(new VoiceMuteChangedEvent(playerId, false, 0));
        return removed;
    }

    public static boolean isMuted(UUID playerId) {
        if (playerId == null) return false;
        Long until = MUTES.get(playerId);
        if (until == null) return false;
        if (until != Long.MAX_VALUE && System.currentTimeMillis() >= until) {
            MUTES.remove(playerId, until);
            return false;
        }
        return true;
    }

    /** Оставшееся время в секундах, -1 = навсегда, 0 = не замучен. */
    public static long remainingSeconds(UUID playerId) {
        if (!isMuted(playerId)) return 0;
        long until = MUTES.get(playerId);
        if (until == Long.MAX_VALUE) return -1;
        return Math.max(1, (until - System.currentTimeMillis()) / 1000L);
    }

    public static Map<UUID, Long> view() {
        return java.util.Collections.unmodifiableMap(MUTES);
    }
}
