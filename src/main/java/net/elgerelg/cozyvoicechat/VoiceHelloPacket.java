package net.elgerelg.cozyvoicechat;

import net.elgerelg.cozyvoicechat.client.CozyVoiceClient;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * S2C: сервер сообщает клиенту параметры голосового UDP-сервера.
 * token — секрет клиента для handshake'а по UDP.
 * ports — все открытые порты ретранслятора, клиент выбирает свой.
 */
public class VoiceHelloPacket {

    public final UUID token;
    public final int[] ports;
    public final int bitrate;
    public final int maxDistance;
    public final float fadeStart;
    public final String host;

    public VoiceHelloPacket(UUID token, int[] ports, int bitrate, int maxDistance, float fadeStart, String host) {
        this.token = token;
        this.ports = ports;
        this.bitrate = bitrate;
        this.maxDistance = maxDistance;
        this.fadeStart = fadeStart;
        this.host = host == null ? "" : host;
    }

    public VoiceHelloPacket(FriendlyByteBuf buf) {
        this.token = buf.readUUID();
        int n = buf.readVarInt();
        this.ports = new int[n];
        for (int i = 0; i < n; i++) this.ports[i] = buf.readVarInt();
        this.bitrate = buf.readVarInt();
        this.maxDistance = buf.readVarInt();
        this.fadeStart = buf.readFloat();
        this.host = buf.readUtf(128);
    }

    public static void buffer(VoiceHelloPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.token);
        buf.writeVarInt(msg.ports.length);
        for (int p : msg.ports) buf.writeVarInt(p);
        buf.writeVarInt(msg.bitrate);
        buf.writeVarInt(msg.maxDistance);
        buf.writeFloat(msg.fadeStart);
        buf.writeUtf(msg.host, 128);
    }

    public static void handler(VoiceHelloPacket msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            if (ctx.getDirection().getReceptionSide().isClient() && FMLEnvironment.dist == Dist.CLIENT) {
                CozyVoiceClient.onHello(msg);
            }
        });
        ctx.setPacketHandled(true);
    }
}
