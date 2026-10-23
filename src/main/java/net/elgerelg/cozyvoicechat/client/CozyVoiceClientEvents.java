package net.elgerelg.cozyvoicechat.client;

import net.minecraft.client.resources.language.I18n;

import net.elgerelg.cozyvoicechat.CozyVoiceChat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * HUD-индикация войсчата + дисконнект при выходе с сервера.
 * Микрофон рисуется примитивами (без текстур): тело, ножка, база.
 */
@Mod.EventBusSubscriber(modid = CozyVoiceChat.MODID, value = Dist.CLIENT)
public final class CozyVoiceClientEvents {

    private CozyVoiceClientEvents() {}

    /** Сторона иконки в пикселях интерфейса (сетка VoiceIcons — 16x16). */
    private static final int ICON = 14;

    private static float appear = 0f;   // 0..1 — появление/исчезновение иконки
    private static float shownLevel = 0f;
    private static long lastFrameNanos = 0L;

    private static boolean debugHud = false;

    public static void toggleDebug() {
        debugHud = !debugHud;
    }

    public static boolean debugEnabled() {
        return debugHud;
    }

    public static void setDebug(boolean b) {
        debugHud = b;
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) {
        // Сервер должен знать про выключенный микрофон и глушилку сразу:
        // иначе над головой у нас ничего не покажется до первого нажатия.
        VoiceStateClient.publish();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        CozyVoiceClient.disconnect();
        VoicePlayback.stop();
        VoiceStateClient.clear();
        VoiceHologramRenderer.clear();
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post e) {
        if (!e.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        boolean sending = MicCapture.isGateOpen();
        // Показываем иконку и при простом нажатии PTT, даже если войс не подключён —
        // иначе непонятно, работает ли клавиша вообще.
        boolean intent = sending || CozyVoiceClient.isPtt()
                || (CozyVoiceSettings.get().vadMode && MicCapture.level() >= CozyVoiceSettings.get().vadThreshold);

        VoiceIcons.Kind kind = VoiceIcons.selfKind(intent);

        float dt = dt();
        appear += ((kind != VoiceIcons.Kind.NONE ? 1f : 0f) - appear) * (1f - (float) Math.exp(-22f * dt));
        if (appear < 0.01f) appear = 0f;
        if (appear > 0.99f) appear = 1f;

        // Нарастание мгновенное, спад плавный: иначе на крике заливка
        // не успевает дойти до верха и «недоливает».
        float raw = MicCapture.level();
        shownLevel = raw >= shownLevel ? raw : shownLevel + (raw - shownLevel) * (1f - (float) Math.exp(-18f * dt));

        GuiGraphics g = e.getGuiGraphics();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        if (debugHud) renderDebug(g, mc);

        if (appear <= 0f) return;

        // Над ванильным хотбаром, ниже строки с названием предмета.
        // Кто говорит — видно по голограммам над головами, список слева не нужен.
        drawIcon(g, sw / 2 - ICON / 2, sh - 40, kind, shownLevel, appear);
    }

    /** Оверлей отладки (F3 + V): каналы, пакеты, потери, RTT. */
    private static void renderDebug(GuiGraphics g, Minecraft mc) {
        String[] lines = debugLines();
        int y = 4;
        for (String s : lines) {
            g.fill(2, y - 1, 6 + mc.font.width(s), y + 9, 0x90000000);
            g.drawString(mc.font, s, 4, y, 0xFFE0E0E0, false);
            y += 10;
        }
    }

    /** Строки отладочного оверлея (F3+V). */
    public static String[] debugLines() {
        double rtt = VoiceStats.rttMs();
        return new String[]{
                I18n.get("cozyvoicechat.debug.header", statusName(), CozyVoiceClient.serverAddress()),
                I18n.get("cozyvoicechat.debug.net",
                        VoiceStats.chosenPort(), CozyVoiceClient.bitrate(),
                        CozyVoiceClient.maxDistance(), String.format(Locale.ROOT, "%.2f", CozyVoiceClient.fadeStart())),
                I18n.get("cozyvoicechat.debug.traffic",
                        VoiceStats.sentPackets.get(), String.format(Locale.ROOT, "%.1f", VoiceStats.sentBytes.get() / 1024.0),
                        VoiceStats.recvPackets.get(), String.format(Locale.ROOT, "%.1f", VoiceStats.recvBytes.get() / 1024.0)),
                I18n.get("cozyvoicechat.debug.loss",
                        VoiceStats.lostPackets.get(), String.format(Locale.ROOT, "%.2f", VoiceStats.lossPercent()),
                        rtt < 0 ? "—" : I18n.get("cozyvoicechat.debug.rtt_ms", String.format(Locale.ROOT, "%.1f", rtt))),
                I18n.get("cozyvoicechat.debug.buffers",
                        VoiceStats.jitterQueue(), VoiceStats.underruns.get(),
                        VoiceStats.decodeErrors.get(), VoiceStats.monitorGaps.get()),
                I18n.get("cozyvoicechat.debug.mic",
                        String.format(Locale.ROOT, "%.2f", MicCapture.level()),
                        I18n.get(MicCapture.isGateOpen() ? "cozyvoicechat.debug.gate_open" : "cozyvoicechat.debug.gate_closed"),
                        CozyVoiceClient.talkingNow().size())
        };
    }

    private static String statusName() {
        return switch (CozyVoiceClient.status()) {
            case CONNECTED -> I18n.get("cozyvoicechat.status.connected");
            case CONNECTING -> I18n.get("cozyvoicechat.status.connecting");
            default -> I18n.get("cozyvoicechat.status.disconnected");
        };
    }

    private static float dt() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float d = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        return Math.min(0.1f, Math.max(0f, d));
    }

    /**
     * Иконка из общей геометрии VoiceIcons: сетка 16x16 растягивается в квадрат
     * ICON пикселей, вся иконка «выезжает» снизу и слегка пульсирует на громкости.
     */
    private static void drawIcon(GuiGraphics g, int x, int y, VoiceIcons.Kind kind, float level, float a) {
        float scale = 0.82f + 0.18f * a + (kind == VoiceIcons.Kind.MIC ? 0.06f * Math.min(1f, level) : 0f);
        float offY = (1f - a) * 6f;
        float unit = ICON / 16f;

        g.pose().pushPose();
        g.pose().translate(x + ICON / 2f, y + offY + ICON / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-(x + ICON / 2f), -(y + ICON / 2f), 0);

        float time = (System.currentTimeMillis() % 100000L) / 1000f;
        VoiceIcons.draw((x0, y0, x1, y1, argb) -> g.fill(
                x + Math.round(x0 * unit), y + Math.round(y0 * unit),
                x + Math.round(x1 * unit), y + Math.round(y1 * unit), argb), kind, level, time, a);

        g.pose().popPose();
    }

}
