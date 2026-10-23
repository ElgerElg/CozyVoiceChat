package net.elgerelg.cozyvoicechat.client;

import net.minecraft.client.resources.language.I18n;

import net.elgerelg.cozyvoicechat.OpusCodec;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import javax.sound.sampled.Mixer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * Меню настройки голосового чата. Полностью кастомный UI в стиле остальных
 * меню мода: тёмная панель, светло-серый акцент, плавный hover.
 */
public class CozyVoiceScreen extends Screen {

	private static final int BG_DIM        = 0xCC0A0A0A;
	private static final int PANEL         = 0xF0141414;
	private static final int PANEL_INNER   = 0xF0181818;
	private static final int BORDER        = 0xFF2C2C2C;
	private static final int ACCENT        = 0xFFC3C7CD;
	private static final int ACCENT_DIM    = 0xFF6E7278;
	private static final int ROW_BG        = 0xFF1E1E1E;
	private static final int ROW_BG_HOVER  = 0xFF2E2E2E;
	private static final int TEXT          = 0xFFCCCCCC;
	private static final int TEXT_HOVER    = 0xFFFFFFFF;
	private static final int TEXT_DIM      = 0xFF888888;
	private static final int TEXT_MUTED    = 0xFF666666;
	private static final int TRACK         = 0xFF111111;
	private static final int OK_GREEN      = 0xFF35D05A;
	private static final int WARN_YELLOW   = 0xFFFFC040;
	private static final int ERR_RED       = 0xFFE84B4B;

	private static final int PANEL_W = 368;
	private static final int PANEL_H = 440;
	private static final int ROW_H   = 28;

	private final UiHover hover = new UiHover();
	private float appear = 0f;
	/** dt считается один раз за кадр: hover.dt() съедает время при каждом вызове. */
	private float frameDt = 0.016f;

	private List<Mixer.Info> inputs;
	private List<Mixer.Info> outputs;

	private int px, py;

	/** Открытый выпадающий список устройств (null — закрыт). */
	private DeviceSelect openList;

	/** Окно ждёт нажатия клавиши для переназначения рации. */
	private boolean binding;

	public boolean isBinding() {
		return binding;
	}

	public CozyVoiceScreen() {
		super(Component.translatable("cozyvoicechat.screen.title"));
	}

	@Override
	protected void init() {
		super.init();
		inputs = AudioDevices.inputs();
		outputs = AudioDevices.outputs();
		openList = null;

		px = (width - PANEL_W) / 2;
		py = (height - PANEL_H) / 2;

		CozyVoiceSettings cfg = CozyVoiceSettings.get();
		int x = px + 16;
		int w = PANEL_W - 32;

		addRenderableWidget(new DeviceSelect(x, py + 50, w, I18n.get("cozyvoicechat.screen.device.input"), true));
		addRenderableWidget(new DeviceSelect(x, py + 82, w, I18n.get("cozyvoicechat.screen.device.output"), false));

		addRenderableWidget(new Segmented(x, py + 132, w, new String[]{I18n.get("cozyvoicechat.screen.mode.ptt"), I18n.get("cozyvoicechat.screen.mode.vad")},
				() -> cfg.vadMode ? 1 : 0, i -> cfg.vadMode = i == 1));

		addRenderableWidget(new KeybindRow(x, py + 162, w));

		addRenderableWidget(new Slider(x, py + 192, w, I18n.get("cozyvoicechat.screen.slider.vad_threshold"), cfg.vadThreshold, 100,
				v -> cfg.vadThreshold = v, () -> cfg.vadMode, true));
		addRenderableWidget(new Slider(x, py + 226, w, I18n.get("cozyvoicechat.screen.slider.mic_gain"), cfg.micGain / 4.0, 400,
				v -> cfg.micGain = v * 4.0, () -> true, false));
		addRenderableWidget(new Slider(x, py + 260, w, I18n.get("cozyvoicechat.screen.slider.volume"), cfg.masterVolume / 2.0, 200,
				v -> cfg.masterVolume = v * 2.0, () -> true, false));

		addRenderableWidget(new Toggle(x, py + 306, w, I18n.get("cozyvoicechat.screen.toggle.noise_suppression"),
				() -> cfg.noiseSuppression, b -> {
			cfg.noiseSuppression = b;
			CozyVoiceSettings.save();
		}));

		addRenderableWidget(new Toggle(x, py + 334, w, I18n.get("cozyvoicechat.screen.toggle.monitor"),
				() -> cfg.monitor, b -> {
			cfg.monitor = b;
			MicCapture.updateDemand();
			if (b) VoicePlayback.ensureStarted();
		}));

		addRenderableWidget(new FlatButton(px + PANEL_W / 2 - 55, py + PANEL_H - 32, 110, 22,
				I18n.get("cozyvoicechat.screen.done"), this::onClose));

		MicCapture.setScreenDemand(true);
	}

	private String deviceLabel(String raw) {
		return raw == null || raw.isEmpty() ? I18n.get("cozyvoicechat.device.default") : raw;
	}

	private void selectDevice(boolean input, String name) {
		CozyVoiceSettings cfg = CozyVoiceSettings.get();
		if (input) {
			cfg.inputDevice = name;
			MicCapture.restart();
		} else {
			cfg.outputDevice = name;
			VoicePlayback.restart();
		}
		CozyVoiceSettings.save();
	}

	// ==================== render ====================

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float dt = hover.dt();
		frameDt = dt;
		appear += (1f - appear) * (1f - (float) Math.exp(-11f * dt));
		float a = UiHover.easeOut(appear);

		g.fill(0, 0, width, height, applyAlpha(BG_DIM, a));

		// панель + тонкая рамка
		g.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, applyAlpha(BORDER, a));
		g.fill(px, py, px + PANEL_W, py + PANEL_H, applyAlpha(PANEL, a));
		// шапка
		g.fill(px, py, px + PANEL_W, py + 32, applyAlpha(PANEL_INNER, a));
		g.fill(px, py + 32, px + PANEL_W, py + 33, applyAlpha(ACCENT, a));

		g.drawString(font, I18n.get("cozyvoicechat.screen.title"), px + 16, py + 12, applyAlpha(ACCENT, a), false);
		String st = statusText();
		int stw = font.width(st);
		g.drawString(font, st, px + PANEL_W - 16 - stw, py + 12, applyAlpha(statusColor(), a), false);

		// подписи секций
		g.drawString(font, I18n.get("cozyvoicechat.screen.section.devices"), px + 16, py + 40, applyAlpha(TEXT_MUTED, a), false);
		g.drawString(font, I18n.get("cozyvoicechat.screen.section.mode"), px + 16, py + 120, applyAlpha(TEXT_MUTED, a), false);
		g.drawString(font, I18n.get("cozyvoicechat.screen.section.processing"), px + 16, py + 294, applyAlpha(TEXT_MUTED, a), false);
		g.drawString(font, I18n.get("cozyvoicechat.screen.section.test"), px + 16, py + 366, applyAlpha(TEXT_MUTED, a), false);

		super.render(g, mouseX, mouseY, partialTick);

		renderMeter(g, px + 16, py + 378, PANEL_W - 32, a);

		// Открытый список рисуем последним, поверх всех виджетов.
		if (openList != null) openList.renderList(g, mouseX, mouseY);
	}

	/** Индикатор уровня микрофона: зелёный → жёлтый → красный + маркер порога. */
	private void renderMeter(GuiGraphics g, int x, int y, int w, float a) {
		g.fill(x, y, x + w, y + 9, applyAlpha(TRACK, a));
		g.fill(x, y, x + w, y + 1, applyAlpha(BORDER, a));

		float lvl = Math.min(1f, MicCapture.level());
		int filled = (int) (w * lvl);
		for (int i = 0; i < filled; i++) {
			float t = (float) i / Math.max(1, w);
			int c = t < 0.6f ? UiHover.lerpArgb(OK_GREEN, WARN_YELLOW, t / 0.6f)
					: UiHover.lerpArgb(WARN_YELLOW, ERR_RED, (t - 0.6f) / 0.4f);
			g.fill(x + i, y + 1, x + i + 1, y + 9, applyAlpha(c, a));
		}

		CozyVoiceSettings cfg = CozyVoiceSettings.get();
		if (cfg.vadMode) {
			int tx = x + (int) (w * cfg.vadThreshold);
			g.fill(tx, y - 2, tx + 1, y + 11, applyAlpha(0xFFFFFFFF, a));
		}

		boolean tx = MicCapture.isGateOpen();
		String hint;
		int hintColor;
		String err = null;
		if (OpusCodec.initError() != null) err = I18n.get("cozyvoicechat.error.opus", OpusCodec.initError());
		else if (MicCapture.error() != null) err = I18n.get("cozyvoicechat.error.mic", MicCapture.error());
		else if (VoicePlayback.error() != null) err = I18n.get("cozyvoicechat.error.playback", VoicePlayback.error());
		if (err != null) {
			hint = trim(err, w);
			hintColor = ERR_RED;
		} else {
			hint = tx ? I18n.get("cozyvoicechat.screen.hint.transmitting")
					: (cfg.vadMode ? I18n.get("cozyvoicechat.screen.hint.waiting_voice") : I18n.get("cozyvoicechat.screen.hint.hold_ptt", CozyVoiceKeys.pttKeyName()));
			hintColor = tx ? OK_GREEN : TEXT_MUTED;
		}
		g.drawString(font, hint, x, y + 14, applyAlpha(hintColor, a), false);
	}

	/** Обрезает строку по ширине, чтобы она не налезала на соседние элементы. */
	private String trim(String s, int maxWidth) {
		if (font.width(s) <= maxWidth) return s;
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (font.width(sb.toString() + c + "…") > maxWidth) break;
			sb.append(c);
		}
		return sb + "…";
	}

	private String statusText() {
		if (!OpusCodec.available()) return I18n.get("cozyvoicechat.status.no_opus");
		String err = MicCapture.error();
		if (err == null) err = VoicePlayback.error();
		if (err != null) return I18n.get("cozyvoicechat.status.audio_error");
		return switch (CozyVoiceClient.status()) {
			case CONNECTED -> I18n.get("cozyvoicechat.status.connected");
			case CONNECTING -> I18n.get("cozyvoicechat.status.connecting");
			default -> I18n.get("cozyvoicechat.status.disconnected");
		};
	}

	private int statusColor() {
		if (!OpusCodec.available()) return ERR_RED;
		if (MicCapture.error() != null || VoicePlayback.error() != null) return ERR_RED;
		return switch (CozyVoiceClient.status()) {
			case CONNECTED -> OK_GREEN;
			case CONNECTING -> WARN_YELLOW;
			default -> TEXT_MUTED;
		};
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		// Открытый список перехватывает клик раньше виджетов под ним.
		if (openList != null && openList.clickList(mx, my)) return true;
		if (binding) {
			CozyVoiceKeys.setPttKey(InputConstants.Type.MOUSE.getOrCreate(button));
			binding = false;
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (binding) {
			// Escape — отмена, как в ванильных настройках управления.
			if (keyCode != InputConstants.KEY_ESCAPE) {
				CozyVoiceKeys.setPttKey(InputConstants.getKey(keyCode, scanCode));
			}
			binding = false;
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double delta) {
		if (openList != null && openList.scrollList(mx, my, delta)) return true;
		return super.mouseScrolled(mx, my, delta);
	}

	@Override
	public void removed() {
		// Тест микрофона живёт только пока открыто меню: забытый включённым
		// loopback звучит как эхо и выглядит как поломка войса.
		CozyVoiceSettings cfg = CozyVoiceSettings.get();
		if (cfg.monitor) {
			cfg.monitor = false;
			VoicePlayback.stopMonitor();
		}
		MicCapture.setScreenDemand(false);
		CozyVoiceSettings.save();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static int applyAlpha(int color, float a) {
		int base = (color >>> 24) & 0xFF;
		int na = (int) (base * Math.max(0f, Math.min(1f, a)));
		return (na << 24) | (color & 0x00FFFFFF);
	}

	// ==================== кастомные виджеты ====================

	/** Строка выбора устройства с выпадающим списком. */
	private final class DeviceSelect extends AbstractWidget {
		private static final int ITEM_H = 20;
		private static final int MAX_VISIBLE = 6;

		private final String label;
		private final boolean input;
		private float h, openAnim;
		private int scroll;

		DeviceSelect(int x, int y, int w, String label, boolean input) {
			super(x, y, w, ROW_H, Component.literal(label));
			this.label = label;
			this.input = input;
		}

		private String current() {
			CozyVoiceSettings cfg = CozyVoiceSettings.get();
			return input ? cfg.inputDevice : cfg.outputDevice;
		}

		private List<String> options() {
			List<String> list = new ArrayList<>();
			list.add(""); // по умолчанию
			for (Mixer.Info i : (input ? inputs : outputs)) list.add(i.getName());
			return list;
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
			boolean open = openList == this;
			h = hover.update("dev" + label, isMouseOver(mx, my) && openList == null, frameDt, 18f);
			openAnim += ((open ? 1f : 0f) - openAnim) * (1f - (float) Math.exp(-20f * frameDt));

			int bg = UiHover.lerpArgb(ROW_BG, ROW_BG_HOVER, Math.max(h, openAnim));
			g.fill(getX(), getY(), getX() + width, getY() + height, bg);
			// акцентная полоска слева, выезжает по hover
			int barH = (int) (height * Math.max(h, openAnim));
			g.fill(getX(), getY() + (height - barH) / 2, getX() + 2, getY() + (height + barH) / 2,
					UiHover.lerpArgb(ACCENT_DIM, ACCENT, Math.max(h, openAnim)));

			int labelW = font.width(label);
			g.drawString(font, label, getX() + 10, getY() + (height - 8) / 2, TEXT_DIM, false);

			int valueX = getX() + 18 + labelW;
			int valueW = getX() + width - 22 - valueX;
			List<FormattedCharSequence> lines = font.split(Component.literal(deviceLabel(current())), Math.max(40, valueW));
			int shown = Math.min(2, lines.size());
			int textY = getY() + (height - shown * 10) / 2 + 1;
			for (int i = 0; i < shown; i++) {
				g.drawString(font, lines.get(i), valueX, textY + i * 10, TEXT, false);
			}

			// стрелка-шеврон, переворачивается при открытии
			int cx = getX() + width - 12;
			int cy = getY() + height / 2;
			int col = UiHover.lerpArgb(TEXT_DIM, TEXT_HOVER, Math.max(h, openAnim));
			for (int i = 0; i < 3; i++) {
				int dy = openAnim > 0.5f ? -(i - 1) : (i - 1);
				g.fill(cx - 3 + i * 2, cy + dy, cx - 1 + i * 2, cy + dy + 1, col);
				g.fill(cx + 3 - i * 2, cy + dy, cx + 5 - i * 2, cy + dy + 1, col);
			}
		}

		/** Список рисуется экраном поверх всего остального. */
		void renderList(GuiGraphics g, int mx, int my) {
			List<String> opts = options();
			int visible = Math.min(MAX_VISIBLE, opts.size());
			int lx = getX();
			int ly = getY() + height + 2;
			int lw = width;
			int lh = visible * ITEM_H + 2;
			float anim = UiHover.easeOut(Math.min(1f, openAnim));
			lh = Math.max(2, (int) (lh * anim));

			g.fill(lx - 1, ly - 1, lx + lw + 1, ly + lh + 1, BORDER);
			g.fill(lx, ly, lx + lw, ly + lh, 0xFF101010);

			g.enableScissor(lx, ly, lx + lw, ly + lh);
			for (int i = 0; i < visible; i++) {
				int idx = scroll + i;
				if (idx >= opts.size()) break;
				String name = opts.get(idx);
				int iy = ly + 1 + i * ITEM_H;
				boolean on = mx >= lx && mx < lx + lw && my >= iy && my < iy + ITEM_H;
				boolean sel = name.equals(current());
				float ih = hover.update("it" + label + idx, on, frameDt, 22f);

				g.fill(lx + 1, iy, lx + lw - 1, iy + ITEM_H - 1,
						sel ? ROW_BG_HOVER : UiHover.lerpArgb(0xFF141414, ROW_BG_HOVER, ih));
				if (sel) g.fill(lx + 1, iy, lx + 3, iy + ITEM_H - 1, ACCENT);

				String text = trim(deviceLabel(name), lw - 16);
				g.drawString(font, text, lx + 8, iy + (ITEM_H - 8) / 2,
						UiHover.lerpArgb(sel ? TEXT_HOVER : TEXT_DIM, TEXT_HOVER, ih), false);
			}
			g.disableScissor();

			if (opts.size() > visible) {
				int trackH = lh - 2;
				int thumbH = Math.max(8, trackH * visible / opts.size());
				int thumbY = ly + 1 + (trackH - thumbH) * scroll / Math.max(1, opts.size() - visible);
				g.fill(lx + lw - 3, ly + 1, lx + lw - 1, ly + lh - 1, TRACK);
				g.fill(lx + lw - 3, thumbY, lx + lw - 1, thumbY + thumbH, ACCENT_DIM);
			}
		}

		boolean clickList(double mx, double my) {
			List<String> opts = options();
			int visible = Math.min(MAX_VISIBLE, opts.size());
			int lx = getX();
			int ly = getY() + height + 2;
			int lh = visible * ITEM_H + 2;

			if (mx < lx || mx >= lx + width || my < ly || my >= ly + lh) {
				// клик мимо списка — просто закрываем
				openList = null;
				return !(mx >= getX() && mx < getX() + width && my >= getY() && my < getY() + height);
			}
			int i = (int) ((my - ly - 1) / ITEM_H);
			int idx = scroll + i;
			if (idx >= 0 && idx < opts.size()) selectDevice(input, opts.get(idx));
			openList = null;
			return true;
		}

		boolean scrollList(double mx, double my, double delta) {
			List<String> opts = options();
			int visible = Math.min(MAX_VISIBLE, opts.size());
			if (opts.size() <= visible) return false;
			int lx = getX();
			int ly = getY() + height + 2;
			int lh = visible * ITEM_H + 2;
			if (mx < lx || mx >= lx + width || my < ly || my >= ly + lh) return false;
			scroll = Math.max(0, Math.min(opts.size() - visible, scroll - (int) Math.signum(delta)));
			return true;
		}

		@Override
		public void onClick(double mx, double my) {
			openList = openList == this ? null : this;
			scroll = 0;
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {}
	}

	/**
	 * Строка переназначения клавиши рации.
	 *
	 * Пишет прямо в KeyMapping, поэтому привязка всегда одна и та же здесь и в
	 * ванильных настройках управления — синхронизировать нечего.
	 */
	private final class KeybindRow extends AbstractWidget {
		private float h, pulse;

		KeybindRow(int x, int y, int w) {
			super(x, y, w, 24, Component.literal("ptt"));
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
			boolean on = isMouseOver(mx, my) && openList == null;
			h = hover.update("kb", on, frameDt, 18f);
			pulse += frameDt;

			g.fill(getX(), getY(), getX() + width, getY() + height,
					UiHover.lerpArgb(ROW_BG, ROW_BG_HOVER, h));

			g.drawString(font, I18n.get("cozyvoicechat.screen.keybind.ptt"), getX() + 10, getY() + (height - 8) / 2,
					UiHover.lerpArgb(TEXT_DIM, TEXT_HOVER, h), false);

			String text;
			int color;
			if (binding) {
				// Мигание — единственный внятный способ показать, что окно ждёт нажатия.
				text = I18n.get("cozyvoicechat.screen.keybind.press");
				color = Math.sin(pulse * 8f) > 0 ? ACCENT : ACCENT_DIM;
			} else {
				text = CozyVoiceKeys.pttKeyName();
				color = UiHover.lerpArgb(TEXT, TEXT_HOVER, h);
			}

			int bw = Math.max(70, font.width(text) + 16);
			int bx = getX() + width - bw - 8;
			int by = getY() + 3;
			g.fill(bx - 1, by - 1, bx + bw + 1, by + 19, BORDER);
			g.fill(bx, by, bx + bw, by + 18, binding ? ROW_BG_HOVER : TRACK);
			g.drawString(font, text, bx + bw / 2 - font.width(text) / 2, by + 5, color, false);
		}

		@Override
		public void onClick(double mx, double my) {
			binding = !binding;
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {}
	}

	/** Переключатель из двух вариантов (сегментированная кнопка). */
	private final class Segmented extends AbstractWidget {
		private final String[] items;
		private final Supplier<Integer> get;
		private final Consumer<Integer> set;
		private final float[] h;
		private float slide = -1f;

		Segmented(int x, int y, int w, String[] items, Supplier<Integer> get, Consumer<Integer> set) {
			super(x, y, w, 24, Component.literal("mode"));
			this.items = items;
			this.get = get;
			this.set = set;
			this.h = new float[items.length];
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
			int seg = width / items.length;
			int sel = get.get();
			if (slide < 0) slide = sel;
			slide += (sel - slide) * (1f - (float) Math.exp(-20f * frameDt));

			g.fill(getX(), getY(), getX() + width, getY() + height, TRACK);
			// подсветка выбранного сегмента едет между вариантами
			int hx = getX() + (int) (slide * seg);
			g.fill(hx + 1, getY() + 1, hx + seg - 1, getY() + height - 1, ACCENT);

			for (int i = 0; i < items.length; i++) {
				int sx = getX() + i * seg;
				int sw = (i == items.length - 1) ? getX() + width - sx : seg;
				boolean on = mx >= sx && mx < sx + sw && my >= getY() && my < getY() + height && openList == null;
				h[i] = hover.update("seg" + i, on, frameDt, 18f);

				float near = Math.max(0f, 1f - Math.abs(slide - i));
				int col = UiHover.lerpArgb(UiHover.lerpArgb(TEXT_DIM, TEXT_HOVER, h[i]), 0xFF141414, near);
				String s = trim(items[i], sw - 8);
				g.drawString(font, s, sx + sw / 2 - font.width(s) / 2, getY() + 8, col, false);
			}
		}

		@Override
		public void onClick(double mx, double my) {
			int seg = width / items.length;
			int i = (int) ((mx - getX()) / seg);
			if (i < 0) i = 0;
			if (i >= items.length) i = items.length - 1;
			set.accept(i);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {}
	}

	/** Слайдер с заливкой, ручкой и процентами. */
	private final class Slider extends AbstractWidget {
		private final String label;
		private final int scale;
		private final DoubleConsumer apply;
		private final Supplier<Boolean> enabled;
		/** true — под треком показывается фактический уровень микрофона. */
		private final boolean showMicLevel;
		private double value;
		private boolean dragging;
		private float h;

		Slider(int x, int y, int w, String label, double value, int scale,
			   DoubleConsumer apply, Supplier<Boolean> enabled, boolean showMicLevel) {
			super(x, y, w, 28, Component.literal(label));
			this.label = label;
			this.value = Math.max(0, Math.min(1, value));
			this.scale = scale;
			this.apply = apply;
			this.enabled = enabled;
			this.showMicLevel = showMicLevel;
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
			boolean on = isMouseOver(mx, my) && openList == null;
			h = hover.update(label, on || dragging, frameDt, 18f);
			boolean en = enabled.get();
			float dim = en ? 1f : 0.45f;

			int barY = getY() + 17;
			int barH = 6;

			String pct = (int) Math.round(value * scale) + "%";
			int pw = font.width(pct);
			g.drawString(font, trim(label, width - pw - 8), getX(), getY() + 2,
					applyAlpha(en ? TEXT_DIM : TEXT_MUTED, dim), false);
			g.drawString(font, pct, getX() + width - pw, getY() + 2,
					applyAlpha(en ? TEXT : TEXT_MUTED, dim), false);

			// трек
			g.fill(getX(), barY, getX() + width, barY + barH, applyAlpha(TRACK, dim));

			// фактический уровень микрофона жёлтым — видно, где реально голос
			if (showMicLevel) {
				int lw = (int) (width * Math.min(1f, MicCapture.level()));
				g.fill(getX(), barY + 1, getX() + lw, barY + barH - 1, 0x80FFC040);
			}

			// заливка до порога
			int fw = (int) (width * value);
			int fill = UiHover.lerpArgb(ACCENT_DIM, ACCENT, h);
			g.fill(getX(), barY, getX() + fw, barY + 2, applyAlpha(fill, dim));
			g.fill(getX(), barY + barH - 2, getX() + fw, barY + barH, applyAlpha(fill, dim));

			// ручка
			int kx = getX() + fw;
			int kh = 4 + (int) (3 * h);
			g.fill(kx - 2, barY - kh, kx + 3, barY + barH + kh, applyAlpha(0xFFF0F0F0, dim));
		}

		private void setFromMouse(double mx) {
			double v = (mx - getX()) / (double) width;
			value = Math.max(0, Math.min(1, v));
			apply.accept(value);
		}

		@Override
		public void onClick(double mx, double my) {
			if (!enabled.get()) return;
			dragging = true;
			setFromMouse(mx);
		}

		@Override
		protected void onDrag(double mx, double my, double dx, double dy) {
			if (dragging) setFromMouse(mx);
		}

		@Override
		public void onRelease(double mx, double my) {
			dragging = false;
			CozyVoiceSettings.save();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {}
	}

	/** Тумблер вкл/выкл. */
	private final class Toggle extends AbstractWidget {
		private final String label;
		private final Supplier<Boolean> get;
		private final Consumer<Boolean> set;
		private float h, knob;

		Toggle(int x, int y, int w, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
			super(x, y, w, 24, Component.literal(label));
			this.label = label;
			this.get = get;
			this.set = set;
			this.knob = get.get() ? 1f : 0f;
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
			boolean on = isMouseOver(mx, my) && openList == null;
			h = hover.update("tg" + label, on, frameDt, 18f);
			boolean v = get.get();
			knob += ((v ? 1f : 0f) - knob) * (1f - (float) Math.exp(-16f * frameDt));

			g.fill(getX(), getY(), getX() + width, getY() + height,
					UiHover.lerpArgb(ROW_BG, ROW_BG_HOVER, h));

			int tw = 28, th = 12;
			int tx = getX() + width - tw - 10;
			// подпись обрезаем по месту до тумблера, чтобы не залезала под него
			g.drawString(font, trim(label, tw == 0 ? width : tx - getX() - 16), getX() + 10, getY() + (height - 8) / 2,
					UiHover.lerpArgb(TEXT_DIM, TEXT_HOVER, h), false);

			int ty = getY() + (height - th) / 2;
			g.fill(tx, ty, tx + tw, ty + th, UiHover.lerpArgb(TRACK, ACCENT, knob));
			int kx = tx + 1 + (int) ((tw - th + 1) * knob);
			g.fill(kx, ty + 1, kx + th - 2, ty + th - 1, 0xFFF0F0F0);
		}

		@Override
		public void onClick(double mx, double my) {
			set.accept(!get.get());
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {}
	}

	/** Плоская кнопка: заливка выезжает слева направо, при нажатии слегка вдавливается. */
	private final class FlatButton extends AbstractWidget {
		private final Runnable action;
		private float h, press;

		FlatButton(int x, int y, int w, int hgt, String text, Runnable action) {
			super(x, y, w, hgt, Component.literal(text));
			this.action = action;
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
			boolean on = isMouseOver(mx, my) && openList == null;
			h = hover.update("btn" + getX() + getY(), on, frameDt, 16f);
			press += ((isFocused() || (on && net.minecraft.client.Minecraft.getInstance().mouseHandler.isLeftPressed()) ? 1f : 0f) - press)
					* (1f - (float) Math.exp(-24f * frameDt));

			int inset = (int) (press * 1.5f);
			int x0 = getX() + inset, y0 = getY() + inset;
			int x1 = getX() + width - inset, y1 = getY() + height - inset;

			g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, BORDER);
			g.fill(x0, y0, x1, y1, ROW_BG);
			// заливка растёт от левого края
			g.fill(x0, y0, x0 + (int) ((x1 - x0) * UiHover.easeOut(h)), y1, ACCENT);

			String s = getMessage().getString();
			int col = UiHover.lerpArgb(TEXT, 0xFF141414, h);
			g.drawString(font, s, (x0 + x1) / 2 - font.width(s) / 2, (y0 + y1) / 2 - 4, col, false);
		}

		@Override
		public void onClick(double mx, double my) {
			action.run();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {}
	}
}
