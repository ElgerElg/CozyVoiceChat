package net.elgerelg.cozyvoicechat.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.elgerelg.cozyvoicechat.CozyVoiceChat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /cozyvoice — админ-команда голосового чата (уровень прав 2).
 * Голосовая часть бывшей /cmdvoice из Union-MC SCP-RP:
 *   debug, reload, effect, hearself, passthrough, mute/unmute/mutelist.
 * Глобальный микрофон, регионы WorldGuard и рации остались в SCP-RP.
 */
@Mod.EventBusSubscriber(modid = CozyVoiceChat.MODID)
public final class CozyVoiceCommand {

    private CozyVoiceCommand() {}

    /** Сообщение с префиксом мода; текст переводится на стороне клиента. */
    private static MutableComponent msg(String key, Object... args) {
        return Component.translatable("cozyvoicechat.prefix").append(" ").append(Component.translatable(key, args));
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(literal("cozyvoice")
            .requires(src -> src.hasPermission(2))
            .then(literal("debug").executes(ctx -> debug(ctx.getSource())))
            .then(literal("reload").executes(ctx -> {
                CozyVoiceServerConfig.load();
                ctx.getSource().sendSuccess(() -> msg("cozyvoicechat.command.reload"), true);
                return 1;
            }))
            .then(literal("effect")
                .then(argument("targets", EntityArgument.players())
                    .then(argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(VoiceEffectManager.getAvailableEffects(), b))
                        .then(argument("state", BoolArgumentType.bool())
                            .executes(ctx -> {
                                Collection<ServerPlayer> ps = EntityArgument.getPlayers(ctx, "targets");
                                String name = StringArgumentType.getString(ctx, "name").toLowerCase(Locale.ROOT);
                                boolean state = BoolArgumentType.getBool(ctx, "state");
                                if (!VoiceEffectManager.getAvailableEffects().contains(name)) {
                                    ctx.getSource().sendFailure(Component.translatable("cozyvoicechat.command.effect.unknown", name));
                                    return 0;
                                }
                                for (ServerPlayer p : ps) VoiceEffectManager.setEffect(p.getUUID(), name, state);
                                ctx.getSource().sendSuccess(() -> msg("cozyvoicechat.command.effect.set",
                                        name, String.valueOf(state), ps.size()), true);
                                return ps.size();
                            })))))
            .then(literal("hearself")
                .then(argument("targets", EntityArgument.players())
                    .then(argument("state", BoolArgumentType.bool())
                        .executes(ctx -> {
                            Collection<ServerPlayer> ps = EntityArgument.getPlayers(ctx, "targets");
                            boolean state = BoolArgumentType.getBool(ctx, "state");
                            for (ServerPlayer p : ps) SelfMonitorManager.set(p.getUUID(), state);
                            ctx.getSource().sendSuccess(() -> msg("cozyvoicechat.command.hearself.set",
                                    String.valueOf(state), ps.size()), true);
                            return ps.size();
                        }))))
            .then(literal("passthrough")
                .then(literal("filteradd")
                    .then(argument("block", ResourceLocationArgument.id())
                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), b))
                        .executes(ctx -> passAdd(ctx.getSource(), ResourceLocationArgument.getId(ctx, "block")))))
                .then(literal("filterrem")
                    .then(argument("block", ResourceLocationArgument.id())
                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                VoicePassthroughManager.getBlocks(c.getSource().getServer()), b))
                        .executes(ctx -> passRem(ctx.getSource(), ResourceLocationArgument.getId(ctx, "block")))))
                .then(literal("filterlist").executes(ctx -> passList(ctx.getSource()))))
            .then(literal("mute")
                .then(argument("targets", EntityArgument.players())
                    .executes(ctx -> mute(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), 0))
                    .then(argument("seconds", IntegerArgumentType.integer(0))
                        .executes(ctx -> mute(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                IntegerArgumentType.getInteger(ctx, "seconds"))))))
            .then(literal("unmute")
                .then(argument("targets", EntityArgument.players())
                    .executes(ctx -> {
                        Collection<ServerPlayer> ps = EntityArgument.getPlayers(ctx, "targets");
                        int n = 0;
                        for (ServerPlayer p : ps) if (VoiceMuteManager.unmute(p.getUUID())) n++;
                        int fn = n;
                        ctx.getSource().sendSuccess(() -> msg("cozyvoicechat.command.unmute.done", fn), true);
                        return Math.max(1, n);
                    })))
            .then(literal("mutelist").executes(ctx -> muteList(ctx.getSource()))));
    }

    private static int debug(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        CozyVoiceServerConfig cfg = CozyVoiceServerConfig.get();
        if (!CozyVoiceServer.isRunning()) {
            source.sendFailure(msg("cozyvoicechat.command.debug.not_running"));
            return 0;
        }
        // В чат уходит переводимый текст (язык клиента), в лог сервера — английский.
        List<Component> out = new ArrayList<>();
        List<String> log = new ArrayList<>();
        out.add(msg("cozyvoicechat.command.debug.config",
                String.valueOf(cfg.ports), cfg.bitrate, cfg.maxDistance, String.valueOf(cfg.fadeStart)));
        log.add("[Cozy Voice] ports " + cfg.ports + ", bitrate " + cfg.bitrate
                + ", range " + cfg.maxDistance + " blocks, fade starts at " + cfg.fadeStart);
        var sessions = CozyVoiceServer.sessionsView();
        int online = server.getPlayerList().getPlayerCount();
        out.add(msg("cozyvoicechat.command.debug.sessions", sessions.size(), online));
        log.add("[Cozy Voice] active sessions: " + sessions.size() + " of " + online + " players");
        long now = System.currentTimeMillis();
        for (var s : sessions.values()) {
            ServerPlayer p = server.getPlayerList().getPlayer(s.playerId());
            String name = p != null ? p.getGameProfile().getName() : s.playerId().toString();
            String loss = String.format(Locale.ROOT, "%.2f", s.lossPercent());
            String silent = String.format(Locale.ROOT, "%.1f", s.lastVoice() == 0 ? -1.0 : (now - s.lastVoice()) / 1000.0);
            out.add(Component.translatable("cozyvoicechat.command.debug.session",
                    name, s.received(), s.lost(), loss, s.relayed(), silent));
            log.add("  " + name + ": received " + s.received() + ", lost " + s.lost() + " (" + loss
                    + "%), relayed " + s.relayed() + ", silent for " + silent + " s");
        }
        for (Component line : out) source.sendSuccess(() -> line, false);
        for (String line : log) CozyVoiceChat.LOGGER.info(line);
        return 1;
    }

    private static int mute(CommandSourceStack source, Collection<ServerPlayer> ps, int seconds) {
        for (ServerPlayer p : ps) VoiceMuteManager.mute(p.getUUID(), seconds);
        Component time = seconds <= 0
                ? Component.translatable("cozyvoicechat.command.mute.forever")
                : Component.translatable("cozyvoicechat.command.mute.seconds", seconds);
        source.sendSuccess(() -> msg("cozyvoicechat.command.mute.done", ps.size(), time), true);
        return ps.size();
    }

    private static int muteList(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        List<Component> lines = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : VoiceMuteManager.view().entrySet()) {
            long left = VoiceMuteManager.remainingSeconds(e.getKey());
            if (left == 0) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            String name = p != null ? p.getGameProfile().getName() : e.getKey().toString();
            lines.add(left < 0
                    ? Component.translatable("cozyvoicechat.command.mutelist.forever", name)
                    : Component.translatable("cozyvoicechat.command.mutelist.timed", name, left));
        }
        if (lines.isEmpty()) {
            source.sendSuccess(() -> msg("cozyvoicechat.command.mutelist.empty"), false);
        } else {
            MutableComponent joined = Component.empty();
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) joined.append(", ");
                joined.append(lines.get(i));
            }
            source.sendSuccess(() -> msg("cozyvoicechat.command.mutelist.list", joined), false);
        }
        return 1;
    }

    private static int passAdd(CommandSourceStack source, ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            source.sendFailure(Component.translatable("cozyvoicechat.command.passthrough.unknown_block", id.toString()));
            return 0;
        }
        boolean added = VoicePassthroughManager.addBlock(source.getServer(), id);
        source.sendSuccess(() -> msg(added
                ? "cozyvoicechat.command.passthrough.added"
                : "cozyvoicechat.command.passthrough.already", id.toString()), true);
        return 1;
    }

    private static int passRem(CommandSourceStack source, ResourceLocation id) {
        if (VoicePassthroughManager.removeBlock(source.getServer(), id)) {
            source.sendSuccess(() -> msg("cozyvoicechat.command.passthrough.removed", id.toString()), true);
            return 1;
        }
        source.sendFailure(Component.translatable("cozyvoicechat.command.passthrough.not_found", id.toString()));
        return 0;
    }

    private static int passList(CommandSourceStack source) {
        var set = VoicePassthroughManager.getBlocks(source.getServer());
        if (set.isEmpty()) {
            source.sendSuccess(() -> msg("cozyvoicechat.command.passthrough.empty"), false);
            return 1;
        }
        String ids = set.stream().map(ResourceLocation::toString).collect(Collectors.joining(", "));
        source.sendSuccess(() -> msg("cozyvoicechat.command.passthrough.list", ids), false);
        return 1;
    }
}
