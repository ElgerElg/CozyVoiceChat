package net.elgerelg.cozyvoicechat.server;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores players who should also hear their own static packets (self monitor).
 * Default is false; cleared on logout/death.
 */
public class SelfMonitorManager {

	private static final Set<UUID> SELF_MONITOR = Collections.newSetFromMap(new ConcurrentHashMap<>());

	public static void set(UUID playerId, boolean enabled) {
		if (enabled) {
			SELF_MONITOR.add(playerId);
		} else {
			SELF_MONITOR.remove(playerId);
		}
	}

	public static boolean isEnabled(UUID playerId) {
		return SELF_MONITOR.contains(playerId);
	}

	public static void clear(UUID playerId) {
		SELF_MONITOR.remove(playerId);
	}
}


