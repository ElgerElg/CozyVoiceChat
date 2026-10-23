package net.elgerelg.cozyvoicechat.impl;

import net.elgerelg.cozyvoicechat.VoiceStatePacket;
import net.elgerelg.cozyvoicechat.api.CozyVoiceApi;
import net.elgerelg.cozyvoicechat.api.VoiceRouteHook;
import net.elgerelg.cozyvoicechat.api.server.AudioChannel;
import net.elgerelg.cozyvoicechat.api.server.AudioPlayer;
import net.elgerelg.cozyvoicechat.api.server.EntityAudioChannel;
import net.elgerelg.cozyvoicechat.api.server.LocationalAudioChannel;
import net.elgerelg.cozyvoicechat.api.server.ServerVoiceApi;
import net.elgerelg.cozyvoicechat.api.server.StaticAudioChannel;
import net.elgerelg.cozyvoicechat.api.server.VoiceEffect;
import net.elgerelg.cozyvoicechat.api.server.VoiceGroup;
import net.elgerelg.cozyvoicechat.server.AudioChannels;
import net.elgerelg.cozyvoicechat.server.AudioPlayerImpl;
import net.elgerelg.cozyvoicechat.server.CozyVoiceServer;
import net.elgerelg.cozyvoicechat.server.CozyVoiceServerConfig;
import net.elgerelg.cozyvoicechat.server.SelfMonitorManager;
import net.elgerelg.cozyvoicechat.server.VoiceEffectManager;
import net.elgerelg.cozyvoicechat.server.VoiceGroupManager;
import net.elgerelg.cozyvoicechat.server.VoiceMuteManager;
import net.elgerelg.cozyvoicechat.server.VoiceStateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Internal implementation of {@link ServerVoiceApi}. Not part of the API. */
public final class ServerVoiceApiImpl implements ServerVoiceApi {

    public static final ServerVoiceApiImpl INSTANCE = new ServerVoiceApiImpl();

    private ServerVoiceApiImpl() {}

    @Override public boolean isRunning() { return CozyVoiceServer.isRunning(); }
    @Override public boolean isConnected(UUID playerId) { return CozyVoiceServer.isConnected(playerId); }
    @Override public Set<UUID> getConnectedPlayers() { return CozyVoiceServer.connectedPlayers(); }
    @Override public boolean isTalking(UUID playerId) { return CozyVoiceServer.isTalking(playerId); }
    @Override public int getVoiceRange() { return CozyVoiceServerConfig.get().maxDistance; }
    @Override public double getFadeStart() { return CozyVoiceServerConfig.get().fadeStart; }
    @Override public int getBitrate() { return CozyVoiceServerConfig.get().bitrate; }

    @Override public boolean isMuted(UUID playerId) { return VoiceMuteManager.isMuted(playerId); }
    @Override public long getMuteRemainingSeconds(UUID playerId) { return VoiceMuteManager.remainingSeconds(playerId); }
    @Override public void mute(UUID playerId, long seconds) { VoiceMuteManager.mute(req(playerId), seconds); }
    @Override public boolean unmute(UUID playerId) { return VoiceMuteManager.unmute(playerId); }

    @Override
    public boolean isMicMuted(UUID playerId) {
        return (VoiceStateManager.flags(playerId) & VoiceStatePacket.FLAG_MIC_MUTED) != 0;
    }

    @Override
    public boolean isDeafened(UUID playerId) {
        return (VoiceStateManager.flags(playerId) & VoiceStatePacket.FLAG_DEAFENED) != 0;
    }

    @Override
    public boolean hasEffect(UUID playerId, VoiceEffect effect) {
        return playerId != null && effect != null && VoiceEffectManager.hasEffect(playerId, effect.id());
    }

    @Override
    public void setEffect(UUID playerId, VoiceEffect effect, boolean enabled) {
        if (effect == null) throw new NullPointerException("effect");
        VoiceEffectManager.setEffect(req(playerId), effect.id(), enabled);
    }

    @Override
    public Set<VoiceEffect> getEffects(UUID playerId) {
        EnumSet<VoiceEffect> out = EnumSet.noneOf(VoiceEffect.class);
        for (VoiceEffect e : VoiceEffect.values()) if (hasEffect(playerId, e)) out.add(e);
        return out;
    }

    @Override public boolean isHearingSelf(UUID playerId) { return playerId != null && SelfMonitorManager.isEnabled(playerId); }
    @Override public void setHearSelf(UUID playerId, boolean enabled) { SelfMonitorManager.set(req(playerId), enabled); }

    @Override public StaticAudioChannel createStaticChannel(UUID id) { return AudioChannels.createStatic(id); }

    @Override
    public LocationalAudioChannel createLocationalChannel(UUID id, ServerLevel level, Vec3 position) {
        return AudioChannels.createLocational(id, level, position);
    }

    @Override public EntityAudioChannel createEntityChannel(UUID id, Entity entity) { return AudioChannels.createEntity(id, entity); }

    @Override
    public AudioPlayer createAudioPlayer(AudioChannel channel, Supplier<short[]> frames) {
        return new AudioPlayerImpl(channel, frames);
    }

    @Override public VoiceGroup createGroup(String name, boolean exclusive) { return VoiceGroupManager.create(name, exclusive); }
    @Override public Optional<VoiceGroup> getGroup(UUID groupId) { return Optional.ofNullable(VoiceGroupManager.get(groupId)); }
    @Override public Optional<VoiceGroup> getGroupOf(UUID playerId) { return Optional.ofNullable(VoiceGroupManager.groupOf(playerId)); }
    @Override public Collection<VoiceGroup> getGroups() { return VoiceGroupManager.all(); }

    @Override public void registerRouteHook(VoiceRouteHook hook) { CozyVoiceApi.registerRouteHook(hook); }
    @Override public void unregisterRouteHook(VoiceRouteHook hook) { CozyVoiceApi.unregisterRouteHook(hook); }

    private static UUID req(UUID id) {
        if (id == null) throw new NullPointerException("playerId");
        return id;
    }
}
