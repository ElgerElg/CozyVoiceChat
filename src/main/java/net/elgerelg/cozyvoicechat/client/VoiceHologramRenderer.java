package net.elgerelg.cozyvoicechat.client;

import net.elgerelg.cozyvoicechat.CozyVoiceChat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Голограмма состояния войсчата над головой игрока.
 *
 * Рисуется тем же набором иконок, что и HUD, и намеренно обычным depth-тестом:
 * сквозь стены её видно быть не должно, иначе она превращается в вх.
 */
@Mod.EventBusSubscriber(modid = CozyVoiceChat.MODID, value = Dist.CLIENT)
public final class VoiceHologramRenderer {

    /** Высота над макушкой, в блоках. */
    private static final float ABOVE_HEAD = 0.32f;
    /** Тот же масштаб, что у ванильных ников. */
    private static final float SCALE = 0.025f;
    /** Сторона иконки в «пиксельных» единицах ника. */
    private static final float SIZE = 10f;
    private static final double MAX_DIST_SQ = 48 * 48;

    /** Сглаженная громкость на игрока, чтобы иконка не дёргалась покадрово. */
    private static final Map<UUID, Float> LEVELS = new ConcurrentHashMap<>();

    private static long lastFrameNanos = 0L;

    private VoiceHologramRenderer() {}

    public static void clear() {
        LEVELS.clear();
    }

    /**
     * Именно Pre и именно с receiveCanceled: морф отменяет это событие, чтобы
     * подменить модель игрока, а при отмене Post уже не приходит — и над
     * превращёнными игроками голограмма просто не рисовалась бы.
     * Система координат здесь та же: начало — в ногах сущности.
     */
    @SubscribeEvent(receiveCanceled = true)
    public static void onRenderPlayer(RenderPlayerEvent.Pre e) {
        Player player = e.getEntity();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        if (mc.player.distanceToSqr(player) > MAX_DIST_SQ) return;

        UUID id = player.getUUID();
        boolean self = player == mc.player;
        boolean talking = self ? MicCapture.isGateOpen() : CozyVoiceClient.isTalking(id);

        VoiceIcons.Kind kind = self ? VoiceIcons.selfKind(talking) : VoiceIcons.otherKind(id, talking);
        // Выключенный микрофон — дело самого игрока, окружающим он ничего не
        // сообщает: молчащий игрок и так молчит. Эта иконка остаётся в HUD.
        if (kind == VoiceIcons.Kind.NONE || kind == VoiceIcons.Kind.MIC_MUTED) {
            LEVELS.remove(id);
            return;
        }

        float raw = self ? MicCapture.level() : VoicePlayback.levelOf(id);
        // Глушилка не даёт декодировать чужой звук, уровня нет — показываем
        // иконку целиком, иначе она выглядела бы пустой.
        if (!self && CozyVoiceSettings.get().deafened && talking) raw = 1f;
        float level = smooth(id, raw);

        render(e.getPoseStack(), e.getMultiBufferSource(), player, kind, level);
    }

    private static float smooth(UUID id, float raw) {
        float dt = dt();
        float prev = LEVELS.getOrDefault(id, 0f);
        float next = raw >= prev ? raw : prev + (raw - prev) * (1f - (float) Math.exp(-18f * dt));
        LEVELS.put(id, next);
        return next;
    }

    private static float dt() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) lastFrameNanos = now;
        float d = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        return Math.min(0.1f, Math.max(0f, d));
    }

    private static void render(PoseStack pose, MultiBufferSource buffers, Player player,
                               VoiceIcons.Kind kind, float level) {
        Minecraft mc = Minecraft.getInstance();

        pose.pushPose();
        pose.translate(0f, player.getBbHeight() + ABOVE_HEAD, 0f);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-SCALE, -SCALE, SCALE);

        Matrix4f m = pose.last().pose();
        // textBackground, а не textBackgroundSeeThrough: именно этот тип уважает
        // глубину, поэтому за стеной голограммы не будет.
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        int light = LightTexture.pack(15, 15);

        float unit = SIZE / 16f;
        float half = SIZE / 2f;
        float time = (System.currentTimeMillis() % 100000L) / 1000f;

        // Прямоугольники иконки перекрываются (обводка под телом), и на одной
        // глубине они дрались за пиксели — отсюда мелькающие чёрные полосы.
        // Каждый следующий кладём чуть ближе к камере.
        float[] z = {0f};
        VoiceIcons.draw((x0, y0, x1, y1, argb) -> {
            quad(vc, m, light, x0 * unit - half, y0 * unit - half,
                    x1 * unit - half, y1 * unit - half, z[0], argb);
            z[0] -= 0.02f;
        }, kind, level, time, 1f);

        pose.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, int light,
                             float x0, float y0, float x1, float y1, float z, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) return;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(light).endVertex();
    }
}
