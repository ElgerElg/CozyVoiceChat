package net.elgerelg.cozyvoicechat.impl.client;

import net.elgerelg.cozyvoicechat.api.client.ClientVoiceApi;
import net.elgerelg.cozyvoicechat.api.client.VoiceConnectionStatus;
import net.elgerelg.cozyvoicechat.client.CozyVoiceClient;
import net.elgerelg.cozyvoicechat.client.CozyVoiceScreen;
import net.elgerelg.cozyvoicechat.client.CozyVoiceSettings;
import net.elgerelg.cozyvoicechat.client.MicCapture;
import net.elgerelg.cozyvoicechat.client.VoicePlayback;
import net.elgerelg.cozyvoicechat.client.VoicePlayersScreen;
import net.elgerelg.cozyvoicechat.client.VoiceStateClient;
import net.minecraft.client.Minecraft;

import java.util.Set;
import java.util.UUID;

/** Internal implementation of {@link ClientVoiceApi}. Not part of the API. */
public final class ClientVoiceApiImpl implements ClientVoiceApi {

    public static final ClientVoiceApiImpl INSTANCE = new ClientVoiceApiImpl();

    private ClientVoiceApiImpl() {}

    static VoiceConnectionStatus map(CozyVoiceClient.Status s) {
        return switch (s) {
            case CONNECTED -> VoiceConnectionStatus.CONNECTED;
            case CONNECTING -> VoiceConnectionStatus.CONNECTING;
            default -> VoiceConnectionStatus.DISCONNECTED;
        };
    }

    private static boolean isLocal(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        return id != null && mc.player != null && id.equals(mc.player.getUUID());
    }

    @Override public VoiceConnectionStatus getStatus() { return map(CozyVoiceClient.status()); }
    @Override public boolean isConnected() { return CozyVoiceClient.status() == CozyVoiceClient.Status.CONNECTED; }

    @Override
    public boolean isTalking(UUID id) {
        if (id == null) return false;
        if (isLocal(id)) return MicCapture.isGateOpen();
        return CozyVoiceClient.isTalking(id) || CozyVoiceClient.isSourceActive(id);
    }

    @Override
    public float getAudioLevel(UUID id) {
        if (id == null) return 0f;
        if (isLocal(id)) return MicCapture.isGateOpen() ? MicCapture.level() : 0f;
        return VoicePlayback.levelOf(id);
    }

    @Override public Set<UUID> getTalkingPlayers() { return CozyVoiceClient.talkingNow(); }
    @Override public boolean isLocalTalking() { return MicCapture.isGateOpen(); }
    @Override public float getLocalMicLevel() { return MicCapture.level(); }

    @Override public boolean isMicMuted() { return CozyVoiceSettings.get().micMuted; }

    @Override
    public void setMicMuted(boolean muted) {
        CozyVoiceSettings cfg = CozyVoiceSettings.get();
        if (cfg.micMuted == muted) return;
        cfg.micMuted = muted;
        CozyVoiceSettings.save();
        VoiceStateClient.publish();
    }

    @Override public boolean isDeafened() { return CozyVoiceSettings.get().deafened; }

    @Override
    public void setDeafened(boolean deafened) {
        CozyVoiceSettings cfg = CozyVoiceSettings.get();
        if (cfg.deafened == deafened) return;
        cfg.deafened = deafened;
        CozyVoiceSettings.save();
        VoiceStateClient.publish();
    }

    @Override public boolean isPlayerMicMuted(UUID playerId) { return playerId != null && VoiceStateClient.micMuted(playerId); }
    @Override public boolean isPlayerDeafened(UUID playerId) { return playerId != null && VoiceStateClient.deafened(playerId); }

    @Override
    public float getPlayerVolume(UUID playerId) {
        return playerId == null ? 1f : (float) CozyVoiceSettings.playerVolume(playerId);
    }

    @Override
    public void setPlayerVolume(UUID playerId, float volume) {
        if (playerId == null) throw new NullPointerException("playerId");
        CozyVoiceSettings.setPlayerVolume(playerId, Math.max(0f, Math.min(2f, volume)));
        CozyVoiceSettings.save();
    }

    @Override public float getMasterVolume() { return (float) CozyVoiceSettings.get().masterVolume; }
    @Override public boolean isPushToTalkMode() { return !CozyVoiceSettings.get().vadMode; }
    @Override public boolean isPushToTalkPressed() { return CozyVoiceClient.isPtt(); }
    @Override public int getVoiceRange() { return CozyVoiceClient.maxDistance(); }

    @Override public void openSettingsScreen() { Minecraft.getInstance().setScreen(new CozyVoiceScreen()); }
    @Override public void openPlayerVolumeScreen() { Minecraft.getInstance().setScreen(new VoicePlayersScreen()); }
}
