package net.elgerelg.cozyvoicechat.server;

import net.elgerelg.cozyvoicechat.CozyVoiceProtocol;
import net.elgerelg.cozyvoicechat.api.CozyVoiceApi;
import net.elgerelg.cozyvoicechat.api.VoiceRouteHook;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Маршрутизация голоса по получателям.
 *
 * Базовая логика: муты, спектаторы, эффекты (radio/echo), «слышать себя» и
 * обычный позиционный голос с фильтром прохождения через стены.
 * Всё серверно-специфичное (глобальный микрофон, рации, CCTV и т.п. из
 * Union-MC SCP-RP) подключается через {@link VoiceRouteHook}.
 *
 * Считается НЕ на каждый голосовой кадр. Кадры идут 50 раз в секунду, а тиков
 * всего 20 — поэтому серверный поток раз в несколько тиков строит маршрут, а
 * рассылкой кадров по нему занимаются сетевые потоки, не трогая мир.
 */
public final class CozyVoiceRouter {

    /** Один получатель готового маршрута. */
    public record Target(UUID id, byte category, float gain) {}

    /** Готовый маршрут говорящего: кому слать и какие эффекты наложить. */
    public record Route(List<Target> targets, boolean radioFx, boolean echoFx, long expiresAtTick) {
        public boolean expired(long gameTime) { return gameTime >= expiresAtTick; }
    }

    /** Сколько тиков живёт посчитанный маршрут. */
    public static final long ROUTE_TTL_TICKS = 2L;

    private static final Route EMPTY = new Route(List.of(), false, false, 0L);

    private static final long MUTE_CACHE_TICKS = 4L;
    private static final long NOISE_THROTTLE_TICKS = 2L;
    /** Уровень прав (op), с которым наблюдатель может говорить. */
    private static final int SPECTATOR_TALK_PERMISSION = 2;

    private record MuteCheck(boolean blocked, long expiresAt) {}

    private static final Map<UUID, MuteCheck> MUTE_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NOISE_THROTTLE = new ConcurrentHashMap<>();

    private CozyVoiceRouter() {}

    public static Route empty() { return EMPTY; }

    /** Строит маршрут для говорящего. Вызывается только из серверного потока. */
    public static Route compute(MinecraftServer srv, ServerPlayer sender, byte[] lastFrame) {
        long gt = sender.level().getGameTime();
        List<Target> targets = new java.util.ArrayList<>();
        VoiceRouteHook.Sink sink = (id, cat, gain) -> targets.add(new Target(id, cat, gain));

        UUID senderId = sender.getUUID();
        boolean radioFx = VoiceEffectManager.hasEffect(senderId, "radio");
        boolean echoFx = VoiceEffectManager.hasEffect(senderId, "echo");

        route(srv, sender, lastFrame, sink);

        if (targets.isEmpty() && !radioFx && !echoFx) {
            return new Route(List.of(), false, false, gt + ROUTE_TTL_TICKS);
        }
        return new Route(List.copyOf(targets), radioFx, echoFx, gt + ROUTE_TTL_TICKS);
    }

    public static void clear(UUID playerId) {
        if (playerId == null) return;
        MUTE_CACHE.remove(playerId);
        NOISE_THROTTLE.remove(playerId);
        VoiceEffectsServer.clear(playerId);
    }

    private static void route(MinecraftServer srv, ServerPlayer sender, byte[] lastFrame, VoiceRouteHook.Sink sink) {
        UUID senderId = sender.getUUID();
        long gt = sender.level().getGameTime();
        List<VoiceRouteHook> hooks = CozyVoiceApi.routeHooks();

        if (isBlocked(srv, sender, senderId, gt, hooks)) return;

        Long lastNoise = NOISE_THROTTLE.get(senderId);
        if (lastNoise == null || gt - lastNoise >= NOISE_THROTTLE_TICKS) {
            NOISE_THROTTLE.put(senderId, gt);
            for (VoiceRouteHook h : hooks) {
                try { h.onVoiceNoise(sender); } catch (Throwable ignored) {}
            }
        }

        CozyVoiceServerConfig cfg = CozyVoiceServerConfig.get();
        boolean selfMonitor = SelfMonitorManager.isEnabled(senderId);
        float level = hooks.isEmpty() ? 0f : level(senderId, lastFrame);

        for (VoiceRouteHook h : hooks) {
            if (h.routeExclusive(srv, sender, level, selfMonitor, sink)) return;
        }

        Set<UUID> heardLive = routeProximity(srv, sender, senderId, selfMonitor, cfg, sink);

        for (VoiceRouteHook h : hooks) {
            h.routeExtra(srv, sender, level, selfMonitor, java.util.Collections.unmodifiableSet(heardLive), sink);
        }
    }

    /** Мут и спектатор — проверка кэшируется, она идёт на каждый кадр. */
    private static boolean isBlocked(MinecraftServer srv, ServerPlayer sender, UUID senderId, long gt,
                                     List<VoiceRouteHook> hooks) {
        MuteCheck mc = MUTE_CACHE.get(senderId);
        if (mc == null || mc.expiresAt() < gt) {
            boolean muted = VoiceMuteManager.isMuted(senderId);
            boolean spectator = sender.isSpectator() && !sender.hasPermissions(SPECTATOR_TALK_PERMISSION);
            for (VoiceRouteHook h : hooks) {
                if (!muted && h.isMuted(srv, sender)) muted = true;
                if (spectator && h.canSpectatorTalk(sender)) spectator = false;
            }
            mc = new MuteCheck(muted || spectator, gt + MUTE_CACHE_TICKS);
            MUTE_CACHE.put(senderId, mc);
        }
        return mc.blocked();
    }

    /**
     * Обычный позиционный голос. Затухание и панораму считает клиент — он
     * знает актуальные позиции без задержки сети; сервер лишь отсекает по
     * дальности и по стенам.
     */
    private static Set<UUID> routeProximity(MinecraftServer srv, ServerPlayer sender, UUID senderId,
                                            boolean selfMonitor, CozyVoiceServerConfig cfg,
                                            VoiceRouteHook.Sink sink) {
        double maxSq = (double) cfg.maxDistance * cfg.maxDistance;
        boolean occlusion = VoicePassthroughManager.isFilterActive(srv);
        Set<UUID> delivered = new HashSet<>();

        for (ServerPlayer target : srv.getPlayerList().getPlayers()) {
            UUID targetId = target.getUUID();
            if (targetId.equals(senderId)) {
                if (selfMonitor) sink.send(targetId, CozyVoiceProtocol.CAT_PROXIMITY, 1f);
                continue;
            }
            if (target.level() != sender.level()) continue;
            if (target.distanceToSqr(sender) > maxSq) continue;
            if (occlusion && !VoicePassthroughManager.canPass(sender, target)) continue;
            sink.send(targetId, CozyVoiceProtocol.CAT_PROXIMITY, 1f);
            delivered.add(targetId);
        }
        return delivered;
    }

    /** Маршрут может считаться раньше первого кадра — тогда громкости ещё нет. */
    private static float level(UUID senderId, byte[] lastFrame) {
        return lastFrame == null ? 0f : VoiceEffectsServer.level01(senderId, lastFrame);
    }
}
