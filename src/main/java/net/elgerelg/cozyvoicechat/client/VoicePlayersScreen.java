package net.elgerelg.cozyvoicechat.client;

import net.minecraft.client.resources.language.I18n;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Список игроков с индивидуальной громкостью (клавиша P).
 *
 * Своя отрисовка списка, а не набор виджетов: строк может быть много, они
 * прокручиваются и постоянно пересобираются при входе-выходе игроков.
 */
public class VoicePlayersScreen extends Screen {

	private static final int BG_DIM       = 0xCC0A0A0A;
	private static final int PANEL        = 0xF0141414;
	private static final int PANEL_INNER  = 0xF0181818;
	private static final int BORDER       = 0xFF2C2C2C;
	private static final int ACCENT       = 0xFFC3C7CD;
	private static final int ACCENT_DIM   = 0xFF6E7278;
	private static final int ROW_BG       = 0xFF1E1E1E;
	private static final int ROW_BG_HOVER = 0xFF2A2A2A;
	private static final int TEXT         = 0xFFCCCCCC;
	private static final int TEXT_HOVER   = 0xFFFFFFFF;
	private static final int TEXT_DIM     = 0xFF888888;
	private static final int TEXT_MUTED   = 0xFF666666;
	private static final int TRACK        = 0xFF111111;
	private static final int TALK_GREEN   = 0xFF35D05A;
	private static final int MUTED_RED    = 0xFFD05050;

	private static final int PANEL_W = 380;
	private static final int PANEL_H = 300;
	private static final int LIST_TOP = 52;
	private static final int LIST_BOTTOM = 44;
	private static final int ROW_H = 30;
	/** Ширина полосы громкости в строке. */
	private static final int BAR_W = 130;

	private record Entry(UUID id, String name) {}

	private final UiHover hover = new UiHover();
	private final List<Entry> entries = new ArrayList<>();

	private float appear = 0f;
	private float frameDt = 0.016f;
	private int px, py;
	private int scroll;
	/** Игрок, чью полосу сейчас тянут мышью. */
	private UUID dragging;

	public VoicePlayersScreen() {
		super(Component.translatable("cozyvoicechat.players.title"));
	}

	@Override
	protected void init() {
		super.init();
		px = (width - PANEL_W) / 2;
		py = (height - PANEL_H) / 2;
		refresh();
	}

	/** Список берём из таблицы игроков: он есть даже для тех, кто вне прогрузки. */
	private void refresh() {
		entries.clear();
		if (minecraft == null || minecraft.getConnection() == null || minecraft.player == null) return;
		UUID self = minecraft.player.getUUID();
		for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
			UUID id = info.getProfile().getId();
			if (id.equals(self)) continue;
			entries.add(new Entry(id, info.getProfile().getName()));
		}
		entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
		int max = Math.max(0, entries.size() - visibleRows());
		if (scroll > max) scroll = max;
	}

	private int visibleRows() {
		return (PANEL_H - LIST_TOP - LIST_BOTTOM) / ROW_H;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float dt = hover.dt();
		frameDt = dt;
		appear += (1f - appear) * (1f - (float) Math.exp(-11f * dt));
		float a = UiHover.easeOut(appear);

		// Состав игроков меняется на ходу, а экран живёт долго.
		if ((System.currentTimeMillis() / 500) % 2 == 0) refresh();

		g.fill(0, 0, width, height, applyAlpha(BG_DIM, a));
		g.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, applyAlpha(BORDER, a));
		g.fill(px, py, px + PANEL_W, py + PANEL_H, applyAlpha(PANEL, a));
		g.fill(px, py, px + PANEL_W, py + 32, applyAlpha(PANEL_INNER, a));
		g.fill(px, py + 32, px + PANEL_W, py + 33, applyAlpha(ACCENT, a));

		g.drawString(font, I18n.get("cozyvoicechat.players.title"), px + 16, py + 12, applyAlpha(ACCENT, a), false);
		String count = I18n.get("cozyvoicechat.players.online", entries.size());
		g.drawString(font, count, px + PANEL_W - 16 - font.width(count), py + 12,
				applyAlpha(TEXT_MUTED, a), false);

		renderList(g, mouseX, mouseY, a);

		String hint = I18n.get("cozyvoicechat.players.hint");
		g.drawString(font, hint, px + PANEL_W / 2 - font.width(hint) / 2, py + PANEL_H - 22,
				applyAlpha(TEXT_MUTED, a), false);

		super.render(g, mouseX, mouseY, partialTick);
	}

	private void renderList(GuiGraphics g, int mx, int my, float a) {
		int lx = px + 12;
		int lw = PANEL_W - 24;
		int ly = py + LIST_TOP;
		int rows = visibleRows();

		if (entries.isEmpty()) {
			String s = I18n.get("cozyvoicechat.players.empty");
			g.drawString(font, s, px + PANEL_W / 2 - font.width(s) / 2, ly + 20,
					applyAlpha(TEXT_MUTED, a), false);
			return;
		}

		for (int i = 0; i < rows; i++) {
			int idx = scroll + i;
			if (idx >= entries.size()) break;
			Entry en = entries.get(idx);
			int ry = ly + i * ROW_H;
			boolean on = mx >= lx && mx < lx + lw && my >= ry && my < ry + ROW_H - 2;
			float h = hover.update("pl" + en.id(), on, frameDt, 18f);

			g.fill(lx, ry, lx + lw, ry + ROW_H - 2, applyAlpha(UiHover.lerpArgb(ROW_BG, ROW_BG_HOVER, h), a));

			double vol = CozyVoiceSettings.playerVolume(en.id());
			boolean talking = CozyVoiceClient.isTalking(en.id());
			boolean muted = vol <= 0.001;

			// Точка слева: зелёная, когда игрок говорит прямо сейчас.
			int dotColor = muted ? MUTED_RED : (talking ? TALK_GREEN : ACCENT_DIM);
			g.fill(lx + 8, ry + ROW_H / 2 - 3, lx + 12, ry + ROW_H / 2 + 1, applyAlpha(dotColor, a));

			int nameW = lw - BAR_W - 70;
			g.drawString(font, trim(en.name(), nameW), lx + 20, ry + (ROW_H - 2 - 8) / 2,
					applyAlpha(UiHover.lerpArgb(TEXT_DIM, TEXT_HOVER, h), a), false);

			// Полоса громкости.
			int bx = lx + lw - BAR_W - 52;
			int by = ry + (ROW_H - 2) / 2 - 3;
			g.fill(bx, by, bx + BAR_W, by + 6, applyAlpha(TRACK, a));
			int fw = (int) (BAR_W * Math.min(1.0, vol / 2.0));
			int fill = muted ? MUTED_RED : UiHover.lerpArgb(ACCENT_DIM, ACCENT, h);
			g.fill(bx, by, bx + fw, by + 6, applyAlpha(fill, a));
			// Отметка на 100%: без неё не понять, где «как было».
			int mid = bx + BAR_W / 2;
			g.fill(mid, by - 2, mid + 1, by + 8, applyAlpha(0x60FFFFFF, a));
			g.fill(bx + fw - 1, by - 3, bx + fw + 2, by + 9, applyAlpha(0xFFF0F0F0, a));

			String pct = muted ? I18n.get("cozyvoicechat.players.off") : Math.round(vol * 100) + "%";
			g.drawString(font, pct, lx + lw - 10 - font.width(pct), ry + (ROW_H - 2 - 8) / 2,
					applyAlpha(muted ? MUTED_RED : TEXT, a), false);
		}

		if (entries.size() > rows) {
			int trackH = rows * ROW_H;
			int thumbH = Math.max(12, trackH * rows / entries.size());
			int thumbY = ly + (trackH - thumbH) * scroll / Math.max(1, entries.size() - rows);
			g.fill(px + PANEL_W - 8, ly, px + PANEL_W - 6, ly + trackH, applyAlpha(TRACK, a));
			g.fill(px + PANEL_W - 8, thumbY, px + PANEL_W - 6, thumbY + thumbH, applyAlpha(ACCENT_DIM, a));
		}
	}

	// ==================== ввод ====================

	private int rowAt(double my) {
		int ly = py + LIST_TOP;
		int i = (int) ((my - ly) / ROW_H);
		if (i < 0 || i >= visibleRows()) return -1;
		int idx = scroll + i;
		return idx < entries.size() ? idx : -1;
	}

	private void setFromMouse(UUID id, double mx) {
		int bx = px + 12 + (PANEL_W - 24) - BAR_W - 52;
		double v = (mx - bx) / (double) BAR_W * 2.0;
		CozyVoiceSettings.setPlayerVolume(id, Math.max(0, Math.min(2, v)));
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		int idx = rowAt(my);
		if (idx >= 0) {
			UUID id = entries.get(idx).id();
			if (button == 1) {
				CozyVoiceSettings.setPlayerVolume(id, 1.0);
				CozyVoiceSettings.save();
				return true;
			}
			int bx = px + 12 + (PANEL_W - 24) - BAR_W - 52;
			if (mx >= bx - 4 && mx <= bx + BAR_W + 4) {
				dragging = id;
				setFromMouse(id, mx);
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (dragging != null) {
			setFromMouse(dragging, mx);
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (dragging != null) {
			dragging = null;
			CozyVoiceSettings.save();
			return true;
		}
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double delta) {
		int max = Math.max(0, entries.size() - visibleRows());
		scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
		return true;
	}

	@Override
	public void removed() {
		CozyVoiceSettings.save();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private String trim(String s, int maxWidth) {
		if (font.width(s) <= maxWidth) return s;
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (font.width(sb.toString() + c + "…") > maxWidth) break;
			sb.append(c);
		}
		return sb + "…";
	}

	private static int applyAlpha(int color, float a) {
		int base = (color >>> 24) & 0xFF;
		int na = (int) (base * Math.max(0f, Math.min(1f, a)));
		return (na << 24) | (color & 0x00FFFFFF);
	}
}
