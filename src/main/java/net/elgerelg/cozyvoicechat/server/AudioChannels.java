package net.elgerelg.cozyvoicechat.server;

import io.github.jaredmdobson.concentus.OpusEncoder;
import net.elgerelg.cozyvoicechat.CozyVoiceChat;
import net.elgerelg.cozyvoicechat.CozyVoiceProtocol;
import net.elgerelg.cozyvoicechat.OpusCodec;
import net.elgerelg.cozyvoicechat.api.server.AudioChannel;
import net.elgerelg.cozyvoicechat.api.server.EntityAudioChannel;
import net.elgerelg.cozyvoicechat.api.server.LocationalAudioChannel;
import net.elgerelg.cozyvoicechat.api.server.StaticAudioChannel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Реализация аудиоканалов публичного API. Получателей считает серверный поток
 * раз в пару тиков (только у каналов, которые недавно что-то слали); кадры
 * рассылаются в том потоке, который их прислал, как и голос игроков.
 */
public final class AudioChannels {

    /** Сколько после последнего кадра канал считается активным. */
    private static final long ACTIVE_MS = 1000L;
    private static final long RECOMPUTE_TICKS = 2L;

    private static final Map<UUID, Base> CHANNELS = new ConcurrentHashMap<>();

    private AudioChannels() {}

    public static StaticAudioChannel createStatic(UUID id) {
        return register(new StaticImpl(id));
    }

    public static LocationalAudioChannel createLocational(UUID id, ServerLevel level, Vec3 pos) {
        return register(new LocationalImpl(id, level, pos));
    }

    public static EntityAudioChannel createEntity(UUID id, Entity entity) {
        return register(new EntityImpl(id, entity));
    }

    private static <T extends Base> T register(T c) {
        Base prev = CHANNELS.put(c.id, c);
        if (prev != null) prev.closed = true;
        return c;
    }

    /** Серверный тик. */
    public static void tick(MinecraftServer srv) {
        if (CHANNELS.isEmpty()) return;
        long now = System.currentTimeMillis();
        long gt = srv.overworld().getGameTime();
        for (Base c : CHANNELS.values()) {
            if (c.closed) continue;
            if (now - c.lastSend > ACTIVE_MS) {
                c.recipients = Set.of();
                c.computedAt = Long.MIN_VALUE;
                continue;
            }
            if (c.computedAt != Long.MIN_VALUE && gt - c.computedAt < RECOMPUTE_TICKS) continue;
            c.computedAt = gt;
            try {
                c.recipients = c.compute(srv);
            } catch (Throwable t) {
                c.recipients = Set.of();
                if (CozyVoiceServerConfig.get().debugLog) {
                    CozyVoiceChat.LOGGER.warn("[CozyVoice] Ошибка аудиоканала {}: {}", c.id, t.toString());
                }
            }
        }
    }

    public static void clearAll() {
        for (Base c : CHANNELS.values()) c.closed = true;
        CHANNELS.clear();
    }

    // -------------------- реализации --------------------

    abstract static class Base implements AudioChannel {
        final UUID id;
        private final AtomicInteger seq = new AtomicInteger();
        private final Object codecLock = new Object();
        private OpusEncoder encoder;
        private final byte[] encBuf = new byte[1275];
        volatile float gain = 1f;
        volatile Predicate<ServerPlayer> filter;
        volatile boolean closed;
        volatile long lastSend;
        volatile long computedAt = Long.MIN_VALUE;
        volatile Set<UUID> recipients = Set.of();

        Base(UUID id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        abstract Set<UUID> compute(MinecraftServer srv);

        abstract byte[] build(int seq, float gain, byte[] opus);

        boolean accepts(ServerPlayer p) {
            Predicate<ServerPlayer> f = filter;
            return f == null || f.test(p);
        }

        @Override public UUID getId() { return id; }

        @Override
        public void sendPcm(short[] pcm) {
            if (pcm == null || pcm.length != CozyVoiceProtocol.FRAME_SAMPLES) {
                throw new IllegalArgumentException("frame must have " + CozyVoiceProtocol.FRAME_SAMPLES + " samples");
            }
            if (closed || !CozyVoiceServer.isRunning()) return;
            byte[] opus;
            synchronized (codecLock) {
                if (encoder == null) {
                    encoder = OpusCodec.newEncoder(CozyVoiceServerConfig.get().bitrate);
                    if (encoder == null) return;
                }
                int n;
                try {
                    n = encoder.encode(pcm, 0, CozyVoiceProtocol.FRAME_SAMPLES, encBuf, 0, encBuf.length);
                } catch (Exception e) {
                    return;
                }
                if (n <= 0) return;
                opus = Arrays.copyOf(encBuf, n);
            }
            sendOpus(opus);
        }

        @Override
        public void sendOpus(byte[] opus) {
            if (opus == null || opus.length == 0 || closed) return;
            lastSend = System.currentTimeMillis();
            if (!CozyVoiceServer.isRunning()) return;
            Set<UUID> r = recipients;
            if (r.isEmpty()) return;
            byte[] pkt = build(seq.getAndIncrement(), gain, opus);
            if (pkt == null) return;
            for (UUID u : r) CozyVoiceServer.sendRaw(u, pkt);
        }

        @Override public float getGain() { return gain; }
        @Override public void setGain(float gain) { this.gain = Math.max(0f, Math.min(1f, gain)); }
        @Override public void setFilter(Predicate<ServerPlayer> filter) { this.filter = filter; }

        @Override
        public void flush() {
            synchronized (codecLock) {
                encoder = null; // новый энкодер — без «хвоста» предыдущего звука
            }
        }

        @Override public boolean isClosed() { return closed; }

        @Override
        public void close() {
            closed = true;
            recipients = Set.of();
            CHANNELS.remove(id, this);
        }
    }

    static final class StaticImpl extends Base implements StaticAudioChannel {
        private final Set<UUID> targets = ConcurrentHashMap.newKeySet();

        StaticImpl(UUID id) { super(id); }

        @Override
        Set<UUID> compute(MinecraftServer srv) {
            Set<UUID> out = new HashSet<>();
            for (UUID t : targets) {
                ServerPlayer p = srv.getPlayerList().getPlayer(t);
                if (p != null && accepts(p)) out.add(t);
            }
            if (filter != null) {
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    if (accepts(p)) out.add(p.getUUID());
                }
            }
            return Set.copyOf(out);
        }

        @Override
        byte[] build(int seq, float gain, byte[] opus) {
            return CozyVoiceProtocol.relaySource(id, seq, CozyVoiceProtocol.SRC_STATIC, gain, 0f, 0, 0, 0, 0, opus);
        }

        @Override public void addTarget(UUID playerId) { targets.add(Objects.requireNonNull(playerId)); computedAt = Long.MIN_VALUE; }
        @Override public boolean removeTarget(UUID playerId) { computedAt = Long.MIN_VALUE; return playerId != null && targets.remove(playerId); }
        @Override public Set<UUID> getTargets() { return Set.copyOf(targets); }
        @Override public void clearTargets() { targets.clear(); computedAt = Long.MIN_VALUE; }
    }

    private static float defaultDistance() {
        return CozyVoiceServerConfig.get().maxDistance;
    }

    static final class LocationalImpl extends Base implements LocationalAudioChannel {
        private volatile ServerLevel level;
        private volatile Vec3 pos;
        private volatile float distance = defaultDistance();

        LocationalImpl(UUID id, ServerLevel level, Vec3 pos) {
            super(id);
            this.level = Objects.requireNonNull(level, "level");
            this.pos = Objects.requireNonNull(pos, "position");
        }

        @Override
        Set<UUID> compute(MinecraftServer srv) {
            ServerLevel l = level;
            Vec3 p = pos;
            double max = (double) distance * distance;
            Set<UUID> out = new HashSet<>();
            for (ServerPlayer pl : l.players()) {
                if (pl.distanceToSqr(p) <= max && accepts(pl)) out.add(pl.getUUID());
            }
            return Set.copyOf(out);
        }

        @Override
        byte[] build(int seq, float gain, byte[] opus) {
            Vec3 p = pos;
            return CozyVoiceProtocol.relaySource(id, seq, CozyVoiceProtocol.SRC_LOCATIONAL, gain, distance,
                    p.x, p.y, p.z, 0, opus);
        }

        @Override public ServerLevel getLevel() { return level; }
        @Override public Vec3 getPosition() { return pos; }

        @Override
        public void setPosition(ServerLevel level, Vec3 position) {
            if (level != this.level) computedAt = Long.MIN_VALUE;
            this.level = Objects.requireNonNull(level, "level");
            this.pos = Objects.requireNonNull(position, "position");
        }

        @Override public float getDistance() { return distance; }
        @Override public void setDistance(float distance) { this.distance = Math.max(1f, distance); }
    }

    static final class EntityImpl extends Base implements EntityAudioChannel {
        private final Entity entity;
        private final int entityId;
        private volatile float distance = defaultDistance();

        EntityImpl(UUID id, Entity entity) {
            super(id);
            this.entity = Objects.requireNonNull(entity, "entity");
            this.entityId = entity.getId();
        }

        @Override
        Set<UUID> compute(MinecraftServer srv) {
            if (entity.isRemoved() || !(entity.level() instanceof ServerLevel l)) return Set.of();
            double max = (double) distance * distance;
            Set<UUID> out = new HashSet<>();
            for (ServerPlayer pl : l.players()) {
                if (pl.distanceToSqr(entity) <= max && accepts(pl)) out.add(pl.getUUID());
            }
            return Set.copyOf(out);
        }

        @Override
        byte[] build(int seq, float gain, byte[] opus) {
            return CozyVoiceProtocol.relaySource(id, seq, CozyVoiceProtocol.SRC_ENTITY, gain, distance,
                    0, 0, 0, entityId, opus);
        }

        @Override public Entity getEntity() { return entity; }
        @Override public float getDistance() { return distance; }
        @Override public void setDistance(float distance) { this.distance = Math.max(1f, distance); }
    }
}
