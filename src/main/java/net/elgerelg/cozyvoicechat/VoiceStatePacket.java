package net.elgerelg.cozyvoicechat;

import net.elgerelg.cozyvoicechat.client.VoiceStateClient;
import net.elgerelg.cozyvoicechat.server.VoiceStateManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Состояние войсчата игрока для чужих клиентов: выключен ли микрофон и не
 * заглушил ли он чат целиком. Нужно для голограмм над головами — чужой клиент
 * сам по себе об этом знать не может.
 *
 * C2S: игрок сообщает своё состояние (uuid в пакете игнорируется).
 * S2C: сервер раздаёт состояние конкретного игрока всем остальным.
 */
public class VoiceStatePacket {

    public static final int FLAG_MIC_MUTED = 1;
    public static final int FLAG_DEAFENED = 2;

    public final UUID player;
    public final int flags;

    public VoiceStatePacket(UUID player, int flags) {
        this.player = player;
        this.flags = flags;
    }

    public VoiceStatePacket(FriendlyByteBuf buf) {
        this.player = buf.readUUID();
        this.flags = buf.readVarInt();
    }

    public static void buffer(VoiceStatePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.player);
        buf.writeVarInt(msg.flags);
    }

    public static void handler(VoiceStatePacket msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                // Верим только отправителю: uuid из пакета не используем.
                VoiceStateManager.set(sender.server, sender.getUUID(), msg.flags);
            } else if (FMLEnvironment.dist == Dist.CLIENT) {
                VoiceStateClient.set(msg.player, msg.flags);
            }
        });
        ctx.setPacketHandled(true);
    }
}
