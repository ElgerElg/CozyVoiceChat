package net.elgerelg.cozyvoicechat.server;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player enabled voice effects.
 */
public class VoiceEffectManager {

	private static final Map<UUID, Set<String>> PLAYER_EFFECTS = new ConcurrentHashMap<>();

	public static void setEffect(UUID playerId, String effectName, boolean enabled) {
		if (effectName == null) return;
		String key = effectName.toLowerCase();
		PLAYER_EFFECTS.compute(playerId, (id, set) -> {
			Set<String> result = set == null ? new HashSet<>() : new HashSet<>(set);
			if (enabled) {
				result.add(key);
			} else {
				result.remove(key);
			}
			return result.isEmpty() ? null : result;
		});
	}

	public static boolean hasEffect(UUID playerId, String effectName) {
		Set<String> set = PLAYER_EFFECTS.get(playerId);
		return set != null && set.contains(effectName.toLowerCase());
	}

	public static boolean hasAny(UUID playerId) {
		Set<String> set = PLAYER_EFFECTS.get(playerId);
		return set != null && !set.isEmpty();
	}

	public static void clear(UUID playerId) {
		PLAYER_EFFECTS.remove(playerId);
	}

	public static java.util.List<String> getAvailableEffects() {
		// Список доступных эффектов в команде /cozyvoice effect <targets> <name> <state>
		// Здесь именно "звуковые" эффекты, отдельно от механики раций.
		return java.util.Arrays.asList("radio", "echo");
	}
}