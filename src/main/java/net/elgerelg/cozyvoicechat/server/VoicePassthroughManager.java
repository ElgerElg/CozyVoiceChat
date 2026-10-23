package net.elgerelg.cozyvoicechat.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Set;

/**
 * Управляет фильтром блоков, КОТОРЫЕ блокируют голос.
 * Все остальные блоки ведут себя как "обычные" (звук проходит, как в дефолтном SVC).
 */
public class VoicePassthroughManager {

	// Кэш LOS, чтобы не делать raycast на каждый аудио-кадр (они идут очень часто).
	// TTL маленький: результат актуален несколько тиков и резко снижает нагрузку.
	private static final java.util.concurrent.ConcurrentHashMap<LosCacheKey, LosCacheEntry> LOS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
	private static final int LOS_CACHE_TTL_TICKS = 16;
	// Квантизация позиций для ключа кэша: вместо точного BlockPos берём сетку 2x2x2.
	// Это в разы повышает hit-rate, когда игроки двигаются — LOS почти не меняется
	// между соседними блоками, а пересчёт raycast'а на каждый сдвиг был очень дорог.
	private static final int LOS_GRID = 2;
	private static volatile long FILTER_VERSION = 0L;
	private static volatile int CACHED_FILTER_ACTIVE = -1; // -1=unknown, 0=no, 1=yes

	private record LosCacheKey(ResourceLocation dimension, java.util.UUID sender, java.util.UUID receiver,
							   BlockPos senderPos, BlockPos receiverPos, long filterVersion) {
	}

	private static BlockPos quantize(Vec3 v) {
		int x = (int) Math.floor(v.x / LOS_GRID) * LOS_GRID;
		int y = (int) Math.floor(v.y / LOS_GRID) * LOS_GRID;
		int z = (int) Math.floor(v.z / LOS_GRID) * LOS_GRID;
		return new BlockPos(x, y, z);
	}

	private record LosCacheEntry(boolean canPass, long expiresAtGameTime) {
	}

	/**
	 * Добавить блок в фильтр "глухих" блоков и сохранить его в данных мира.
	 */
	public static boolean addBlock(MinecraftServer server, ResourceLocation id) {
		if (id == null) {
            return false;
        }
		if (!BuiltInRegistries.BLOCK.containsKey(id)) {
			return false;
		}
		boolean added = VoicePassthroughData.get(server).addBlock(id);
		if (added) {
			FILTER_VERSION++;
			CACHED_FILTER_ACTIVE = 1;
			LOS_CACHE.clear();
		}
		return added;
	}

	/**
	 * Удалить блок из фильтра "глухих" блоков.
	 */
	public static boolean removeBlock(MinecraftServer server, ResourceLocation id) {
		if (id == null) {
            return false;
        }
		boolean removed = VoicePasified(server).removeBlock(id);
		if (removed) {
			FILTER_VERSION++;
			CACHED_FILTER_ACTIVE = -1;
			LOS_CACHE.clear();
		}
		return removed;
	}

	/**
	 * Получить текущий набор блоков, которые полностью глушат голос.
	 */
	public static Set<ResourceLocation> getBlocks(MinecraftServer server) {
		return VoicePasified(server).getBlocks();
	}

	/**
	 * Быстрый индикатор: включён ли пасс-тру (то есть есть ли хоть один блок, который глушит звук).
	 * Если список пуст — нет смысла перехватывать локальный голос на сервере.
	 */
	public static boolean isFilterActive(MinecraftServer server) {
		if (server == null) {
			return false;
		}
		int cached = CACHED_FILTER_ACTIVE;
		if (cached >= 0) {
			return cached == 1;
		}
		try {
			boolean active = !getBlocks(server).isEmpty();
			CACHED_FILTER_ACTIVE = active ? 1 : 0;
			return active;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static VoicePassthroughData VoicePasified(MinecraftServer server) {
		return VoicePassthroughData.get(server);
	}

	/**
	 * Проверка: может ли голос пройти от отправителя к получателю.
	 * Если между ними есть хотя бы один блок ИЗ ФИЛЬТРА — возвращает false.
	 */
	public static boolean canPass(ServerPlayer sender, ServerPlayer receiver) {
		if (sender == null || receiver == null) return false;
		if (sender.level() != receiver.level()) return false;
		if (!(sender.level() instanceof ServerLevel level)) return false;

		Vec3 from = sender.getEyePosition();
		Vec3 to = receiver.getEyePosition();

		MinecraftServer server = level.getServer();
		Set<ResourceLocation> blocked = VoicePasified(server).getBlocks();
		if (blocked.isEmpty()) {
			return true;
		}

		// Кэшируем только когда реально есть фильтр.
		// Позиции квантуем на 2-блочную сетку — LOS между близкими точками
		// практически не меняется, а попадание в кэш растёт в разы при движении.
		ResourceLocation dim = level.dimension().location();
		BlockPos fromPos = quantize(from);
		BlockPos toPos = quantize(to);
		long now = level.getGameTime();
		LosCacheKey key = new LosCacheKey(dim, sender.getUUID(), receiver.getUUID(), fromPos, toPos, FILTER_VERSION);
		LosCacheEntry cached = LOS_CACHE.get(key);
		if (cached != null && cached.expiresAtGameTime >= now) {
			return cached.canPass;
		}

		// Предохранитель от разрастания кэша в больших онлайнах.
		// Чистим только просроченные записи; полный clear() уничтожает все hits сразу
		// и провоцирует burst raycast'ов сразу после переполнения.
		if (LOS_CACHE.size() > 8000) {
			LOS_CACHE.entrySet().removeIf(en -> en.getValue() == null || en.getValue().expiresAtGameTime < now);
			if (LOS_CACHE.size() > 8000) {
				LOS_CACHE.clear();
			}
		}

		boolean result = hasLineOfSightWithFilter(level, from, to, blocked);
		LOS_CACHE.put(key, new LosCacheEntry(result, now + LOS_CACHE_TTL_TICKS));
		return result;
	}

	private static boolean hasLineOfSightWithFilter(ServerLevel level, Vec3 from, Vec3 to, Set<ResourceLocation> blocked) {
		// Важно: level.clip() возвращает ТОЛЬКО ближайшее попадание.
		// Поэтому нужно пройти по лучу и проверить ВСЕ блоки между игроками:
		// если встретился хоть один блок из фильтра — глушим звук.
		Vec3 dir = to.subtract(from);
		double len = dir.length();
		if (len <= 1.0E-6) {
			return true;
		}
		dir = dir.scale(1.0D / len); // normalize

		Vec3 curFrom = from;
		Vec3 lastHitPos = null;

		// Ограничение итераций, чтобы избежать зависаний/бесконечных циклов
		for (int i = 0; i < 256; i++) {
			BlockHitResult hit = level.clip(new ClipContext(
				curFrom,
				to,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				null
			));

			if (hit.getType() != HitResult.Type.BLOCK) {
				// Блоков больше нет — звук проходит
				return true;
			}

			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);

			// Воздух и блоки без коллизии не глушат звук — но нужно продолжить искать дальше
			if (!state.isAir() && !state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty()) {
				ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
				if (id != null && blocked.contains(id)) {
					return false;
				}
			}

			Vec3 hitPos = hit.getLocation();
			if (hitPos == null) {
				// На всякий случай — если API вернуло null, не рискуем "заглушить всё"
				return true;
			}

			// Защита от зацикливания: если точка попадания не меняется — выходим
			if (lastHitPos != null && hitPos.distanceToSqr(lastHitPos) < 1.0E-12) {
				return true;
			}
			lastHitPos = hitPos;

			// Сдвигаем старт немного вперёд по направлению луча, чтобы проверить следующий блок
			curFrom = hitPos.add(dir.scale(0.01D));

			// Если почти дошли до цели — считаем, что препятствий нет
			if (curFrom.distanceToSqr(to) < 1.0E-6) {
				return true;
			}
		}

		// Если по какой-то причине итерации закончились — по умолчанию пропускаем
		return true;
	}
}