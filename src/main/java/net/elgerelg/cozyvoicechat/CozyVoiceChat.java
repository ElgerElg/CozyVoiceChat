package net.elgerelg.cozyvoicechat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Cozy Voice Chat — собственный голосовой чат поверх UDP (Opus/Concentus).
 * Выделен из Union-MC SCP-RP (бывший пакет umcvoice) в самостоятельный мод.
 *
 * Регистрация событий идёт через @Mod.EventBusSubscriber в самих классах,
 * здесь только логгер, сетевой канал и регистрация forge-пакетов.
 */
@Mod(CozyVoiceChat.MODID)
public class CozyVoiceChat {

    public static final String MODID = "cozyvoicechat";
    public static final String NAME = "Cozy Voice Chat";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel PACKET_HANDLER = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"),
            () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
    private static int messageID = 0;

    public CozyVoiceChat() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static synchronized <T> void addNetworkMessage(Class<T> messageType,
                                                          BiConsumer<T, FriendlyByteBuf> encoder,
                                                          Function<FriendlyByteBuf, T> decoder,
                                                          BiConsumer<T, Supplier<NetworkEvent.Context>> messageConsumer) {
        PACKET_HANDLER.registerMessage(messageID++, messageType, encoder, decoder, messageConsumer);
    }
}
