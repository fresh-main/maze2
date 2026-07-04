package com.labyrinthmod.common.network;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.network.packet.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        // Клиент -> сервер
        CHANNEL.messageBuilder(AdminActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AdminActionPacket::encode)
                .decoder(AdminActionPacket::decode)
                .consumerMainThread(AdminActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(C2SSaveCraftRestrictionsPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SSaveCraftRestrictionsPacket::encode)
                .decoder(C2SSaveCraftRestrictionsPacket::decode)
                .consumerMainThread(C2SSaveCraftRestrictionsPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CSyncCraftRestrictionsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSyncCraftRestrictionsPacket::encode)
                .decoder(S2CSyncCraftRestrictionsPacket::decode)
                .consumerMainThread(S2CSyncCraftRestrictionsPacket::handle)
                .add();

        CHANNEL.messageBuilder(UpdateSettingsPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpdateSettingsPacket::encode)
                .decoder(UpdateSettingsPacket::decode)
                .consumerMainThread(UpdateSettingsPacket::handle)
                .add();

        CHANNEL.messageBuilder(RiderAttackPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RiderAttackPacket::encode)
                .decoder(RiderAttackPacket::decode)
                .consumerMainThread(RiderAttackPacket::handle)
                .add();

        CHANNEL.messageBuilder(ImposterAttackPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ImposterAttackPacket::encode)
                .decoder(ImposterAttackPacket::decode)
                .consumerMainThread(ImposterAttackPacket::handle)
                .add();

        // Сервер -> клиент. Регистрируем НА ОБЕИХ сторонах: серверу нужна
        // регистрация, чтобы кодировать пакеты при отправке. Сами handle-методы
        // защищены проверкой FMLEnvironment.dist == Dist.CLIENT / прокси.
        CHANNEL.messageBuilder(OpenAdminMenuPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenAdminMenuPacket::encode)
                .decoder(OpenAdminMenuPacket::decode)
                .consumerMainThread(OpenAdminMenuPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncAdminDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncAdminDataPacket::encode)
                .decoder(SyncAdminDataPacket::decode)
                .consumerMainThread(SyncAdminDataPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenImposterScreenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenImposterScreenPacket::encode)
                .decoder(OpenImposterScreenPacket::decode)
                .consumerMainThread(OpenImposterScreenPacket::handle)
                .add();

        CHANNEL.messageBuilder(FractionRevealPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FractionRevealPacket::encode)
                .decoder(FractionRevealPacket::decode)
                .consumerMainThread(FractionRevealPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncFractionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncFractionPacket::encode)
                .decoder(SyncFractionPacket::decode)
                .consumerMainThread(SyncFractionPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncConfigPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncConfigPacket::encode)
                .decoder(SyncConfigPacket::decode)
                .consumerMainThread(SyncConfigPacket::handle)
                .add();

        // Доступ фракций в лабиринт.
        CHANNEL.messageBuilder(C2SRequestFractionAccessPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SRequestFractionAccessPacket::encode)
                .decoder(C2SRequestFractionAccessPacket::decode)
                .consumerMainThread(C2SRequestFractionAccessPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SFractionAccessUpdatePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SFractionAccessUpdatePacket::encode)
                .decoder(C2SFractionAccessUpdatePacket::decode)
                .consumerMainThread(C2SFractionAccessUpdatePacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CFractionAccessSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CFractionAccessSyncPacket::encode)
                .decoder(S2CFractionAccessSyncPacket::decode)
                .consumerMainThread(S2CFractionAccessSyncPacket::handle)
                .add();

        CHANNEL.registerMessage(id++, SwitchFractionPacket.class,
                SwitchFractionPacket::encode,
                SwitchFractionPacket::decode,
                SwitchFractionPacket::handle);
    }
}