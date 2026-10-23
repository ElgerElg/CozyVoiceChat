package net.elgerelg.cozyvoicechat.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Короткий hover 0→1 для кнопок GUI. Экспоненциальное приближение, чтобы
 * одинаково выглядело при 30 и 144 FPS.
 */
public final class UiHover {

	private final Map<String, Float> amounts = new HashMap<>();
	private long lastNs;

	public float dt() {
		long now = System.nanoTime();
		float d = lastNs == 0L ? 0.016f : (now - lastNs) / 1_000_000_000f;
		lastNs = now;
		if (d < 0.001f) d = 0.001f;
		if (d > 0.05f) d = 0.05f;
		return d;
	}

	/**
	 * @param speed  чем больше, тем короче (F4 ≈ 14, роли ≈ 20)
	 * @return 0..1 с ease-out
	 */
	public float update(String id, boolean hover, float dt, float speed) {
		float cur = amounts.getOrDefault(id, 0f);
		float target = hover ? 1f : 0f;
		cur += (target - cur) * (1f - (float) Math.exp(-speed * dt));
		if (!hover && cur < 0.002f) {
			amounts.remove(id);
			return 0f;
		}
		if (hover && cur > 0.997f) {
			amounts.put(id, 1f);
			return 1f;
		}
		amounts.put(id, cur);
		return easeOut(cur);
	}

	public static float easeOut(float t) {
		if (t <= 0f) return 0f;
		if (t >= 1f) return 1f;
		float inv = 1f - t;
		return 1f - inv * inv * inv;
	}

	public static int lerpArgb(int a, int b, float t) {
		if (t <= 0f) return a;
		if (t >= 1f) return b;
		int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
		int oa = (int) (aa + (ba - aa) * t);
		int or = (int) (ar + (br - ar) * t);
		int og = (int) (ag + (bg - ag) * t);
		int ob = (int) (ab + (bb - ab) * t);
		return (oa << 24) | (or << 16) | (og << 8) | ob;
	}
}
