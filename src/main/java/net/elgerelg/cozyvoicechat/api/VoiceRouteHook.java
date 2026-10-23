package net.elgerelg.cozyvoicechat.api;

import net.elgerelg.cozyvoicechat.CozyVoiceProtocol;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;

/**
 * Точка расширения маршрутизации голоса для других модов.
 *
 * В Union-MC SCP-RP маршрутизатор знал про глобальный микрофон с регионами,
 * рации, CCTV, SCP-939, муты и права LuckPerms. В Cozy Voice Chat всего этого
 * нет — вместо этого любой мод может зарегистрировать хук через
 * {@link CozyVoiceApi#registerRouteHook(VoiceRouteHook)} и добавить своё.
 *
 * Все методы вызываются из серверного потока (раз в пару тиков на говорящего).
 */
public interface VoiceRouteHook {

    /** Куда отправить кадр: получатель, категория (CozyVoiceProtocol.CAT_*) и множитель громкости. */
    interface Sink {
        void send(UUID targetId, byte category, float gain);
    }

    /** true — голос игрока полностью блокируется (мут и т.п.). */
    default boolean isMuted(MinecraftServer server, ServerPlayer sender) { return false; }

    /** true — игроку в режиме наблюдателя разрешено говорить. */
    default boolean canSpectatorTalk(ServerPlayer sender) { return false; }

    /** Говорящий издал звук (для механик вроде «слышит шум»). */
    default void onVoiceNoise(ServerPlayer sender) {}

    /**
     * Полностью заменить позиционный голос (например, глобальный микрофон).
     * Вернуть true, если маршрут построен хуком и обычный голос не нужен.
     *
     * @param level громкость последнего кадра 0..1 (0, если кадров ещё не было)
     */
    default boolean routeExclusive(MinecraftServer server, ServerPlayer sender, float level,
                                   boolean selfMonitor, Sink sink) { return false; }

    /**
     * Дополнительные получатели поверх позиционного голоса (рации, камеры...).
     *
     * @param heardLive кто уже слышит говорящего вживую (чтобы не дублировать)
     */
    default void routeExtra(MinecraftServer server, ServerPlayer sender, float level,
                            boolean selfMonitor, Set<UUID> heardLive, Sink sink) {}
}
