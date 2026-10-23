package net.elgerelg.cozyvoicechat.server;

import net.elgerelg.cozyvoicechat.CozyVoiceProtocol;
import net.elgerelg.cozyvoicechat.api.CozyVoiceApi;
import net.elgerelg.cozyvoicechat.api.VoiceRouteHook;
import net.elgerelg.cozyvoicechat.api.event.VoiceRouteEvent;
import net.elgerelg.cozyvoicechat.api.server.VoiceTarget;
import net.minecraftforge.common.MinecraftForge;
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

    /** Готовый маршрут говорящего: кому слать и какие эффекты наложить. */
    public record Route(List<VoiceTarget> targets, boolean radioFx, boolean echoFx, long expiresAtTick) {
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
        List<VoiceTarget> targets = new java.util.ArrayList<>();
        VoiceRouteHook.Sink sink = (id, cat, gain) -> {
            if (id != null) targets.add(new VoiceTarget(id, cat, gain));
        };

        UUID senderId = sender.getUUID();
        boolean radioFx = VoiceEffectManager.hasEffect(senderId, "radio");
        boolean echoFx = VoiceEffectManager.hasEffect(senderId, "echo");

        if (!route(srv, sender, lastFrame, sink)) {
            return new Route(List.of(), false, false, gt + ROUTE_TTL_TICKS); // мут/спектатор
        }

        // Публичный API: аддоны могут поправить маршрут и эффекты.
        VoiceRouteEvent ev = new VoiceRouteEvent(sender, targets, radioFx, echoFx);
        if (MinecraftForge.EVENT_BUS.post(ev)) {
            return new Route(List.of(), false, false, gt + ROUTE_TTL_TICKS);
        }
        radioFx = ev.isRadioEffect();
        echoFx = ev.isEchoEffect();
        targets.removeIf(java.util.Objects::isNull);

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

    /** @return false, если говорящий заблокирован (мут, спектатор) */
    private static boolean route(MinecraftServer srv, ServerPlayer sender, byte[] lastFrame, VoiceRouteHook.Sink sink) {
        UUID senderId = sender.getUUID();
        long gt = sender.level().getGameTime();
        List<VoiceRouteHook> hooks = CozyVoiceApi.routeHooks();

        if (isBlocked(srv, sender, senderId, gt, hooks)) return false;

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

        // Группы (API): участники слышат друг друга без ограничения по дальности.
        Set<UUID> grouped = Set.of();
        VoiceGroupManager.GroupImpl group = VoiceGroupManager.groupOfRaw(senderId);
        if (group != null) {
            grouped = new HashSet<>();
            for (UUID m : group.membersRaw()) {
                if (m.equals(senderId)) continue;
                sink.send(m, CozyVoiceProtocol.CAT_GLOBAL, 1f);
                grouped.add(m);
            }
            if (group.isExclusive()) {
                if (selfMonitor) sink.send(senderId, CozyVoiceProtocol.CAT_PROXIMITY, 1f);
                return true;
            }
        }

        for (VoiceRouteHook h : hooks) {
            if (h.routeExclusive(srv, sender, level, selfMonitor, sink)) return true;
        }

        Set<UUID> heardLive = routeProximity(srv, sender, senderId, selfMonitor, cfg, sink, grouped);
        heardLive.addAll(grouped);

        for (VoiceRouteHook h : hooks) {
            h.routeExtra(srv, sender, level, selfMonitor, java.util.Collections.unmodifiableSet(heardLive), sink);
        }
        return true;
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
                                            VoiceRouteHook.Sink sink, Set<UUID> skip) {
        double maxSq = (double) cfg.maxDistance * cfg.maxDistance;
        boolean occlusion = VoicePassthroughManager.isFilterActive(srv);
        Set<UUID> delivered = new HashSet<>();

        for (ServerPlayer target : srv.getPlayerList().getPlayers()) {
            UUID targetId = target.getUUID();
            if (targetId.equals(senderId)) {
                if (selfMonitor) sink.send(targetId, CozyVoiceProtocol.CAT_PROXIMITY, 1f);
                continue;
            }
            if (skip.contains(targetId)) continue;
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
