package net.elgerelg.cozyvoicechat.impl;

import net.elgerelg.cozyvoicechat.CozyVoiceChat;
import net.elgerelg.cozyvoicechat.api.CozyVoiceApi;
import net.elgerelg.cozyvoicechat.api.audio.VoiceDecoder;
import net.elgerelg.cozyvoicechat.api.audio.VoiceEncoder;
import net.elgerelg.cozyvoicechat.api.client.ClientVoiceApi;
import net.elgerelg.cozyvoicechat.api.server.ServerVoiceApi;
import net.elgerelg.cozyvoicechat.server.CozyVoiceServer;
import net.elgerelg.cozyvoicechat.server.CozyVoiceServerConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Internal implementation of {@link CozyVoiceApi}; found by {@code CozyVoiceApi.get()} via reflection.
 * Not part of the API, may change at any time.
 */
public final class CozyVoiceApiImpl implements CozyVoiceApi {

    public static final CozyVoiceApiImpl INSTANCE = new CozyVoiceApiImpl();

    private static final String CLIENT_IMPL = "net.elgerelg.cozyvoicechat.impl.client.ClientVoiceApiImpl";
    private static final int DEFAULT_BITRATE = 32000;

    private volatile ClientVoiceApi client;

    private CozyVoiceApiImpl() {}

    @Override
    public int getApiVersion() {
        return API_VERSION;
    }

    @Override
    public String getModVersion() {
        try {
            return ModList.get().getModContainerById(CozyVoiceChat.MODID)
                    .map(c -> c.getModInfo().getVersion().toString()).orElse("unknown");
        } catch (Throwable t) {
            return "unknown";
        }
    }

    @Override
    public ServerVoiceApi server() {
        return ServerVoiceApiImpl.INSTANCE;
    }

    @Override
    public ClientVoiceApi client() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            throw new IllegalStateException("The client API is only available on the physical client");
        }
        ClientVoiceApi c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    try {
                        client = (ClientVoiceApi) Class.forName(CLIENT_IMPL).getField("INSTANCE").get(null);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Cozy Voice Chat client API failed to load", e);
                    }
                }
                c = client;
            }
        }
        return c;
    }

    @Override
    public VoiceEncoder createEncoder() {
        return createEncoder(CozyVoiceServer.isRunning() ? CozyVoiceServerConfig.get().bitrate : DEFAULT_BITRATE);
    }

    @Override
    public VoiceEncoder createEncoder(int bitrate) {
        return new Codecs.Encoder(bitrate);
    }

    @Override
    public VoiceDecoder createDecoder() {
        return new Codecs.Decoder();
    }
}
