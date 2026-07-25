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


        // 0. SpawnTaskPacket (Клиент -> Сервер) - ДОБАВЛЕНО С id++
        CHANNEL.messageBuilder(SpawnTaskPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SpawnTaskPacket::encode)
                .decoder(SpawnTaskPacket::decode)
                .consumerMainThread(SpawnTaskPacket::handle)
                .add();

        // AddTaskPacket (Клиент -> Сервер)
        CHANNEL.messageBuilder(AddTaskPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AddTaskPacket::encode)
                .decoder(AddTaskPacket::decode)
                .consumerMainThread(AddTaskPacket::handle)
                .add();

        // RequestTimerUpdatePacket (Клиент -> Сервер)
        CHANNEL.messageBuilder(RequestTimerUpdatePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestTimerUpdatePacket::encode)
                .decoder(RequestTimerUpdatePacket::decode)
                .consumerMainThread(RequestTimerUpdatePacket::handle)
                .add();

        // ResetTimerPacket (Клиент -> Сервер)
        CHANNEL.messageBuilder(ResetTimerPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ResetTimerPacket::encode)
                .decoder(ResetTimerPacket::decode)
                .consumerMainThread(ResetTimerPacket::handle)
                .add();

        // SpawnSpecificTaskPacket (Клиент -> Сервер)
        CHANNEL.messageBuilder(SpawnSpecificTaskPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SpawnSpecificTaskPacket::encode)
                .decoder(SpawnSpecificTaskPacket::decode)
                .consumerMainThread(SpawnSpecificTaskPacket::handle)
                .add();

        // TakeTaskPacket (Клиент -> Сервер)
        CHANNEL.messageBuilder(TakeTaskPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TakeTaskPacket::encode)
                .decoder(TakeTaskPacket::decode)
                .consumerMainThread(TakeTaskPacket::handle)
                .add();

        // SyncBoardDataPacket (Сервер -> Клиент)
        CHANNEL.messageBuilder(SyncBoardDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncBoardDataPacket::encode)
                .decoder(SyncBoardDataPacket::decode)
                .consumerMainThread(SyncBoardDataPacket::handle)
                .add();

// RemoveTaskPacket (Клиент -> Сервер)
        CHANNEL.messageBuilder(RemoveTaskPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoveTaskPacket::encode)
                .decoder(RemoveTaskPacket::decode)
                .consumerMainThread(RemoveTaskPacket::handle)
                .add();
        // SyncTasksPacket (Сервер -> Клиент)
        CHANNEL.messageBuilder(SyncTasksPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncTasksPacket::encode)
                .decoder(SyncTasksPacket::decode)
                .consumerMainThread(SyncTasksPacket::handle)
                .add();

        // SetSpawnIntervalPacket (Клиент -> Сервер)
        CHANNEL.messageBuilder(SetSpawnIntervalPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetSpawnIntervalPacket::encode)
                .decoder(SetSpawnIntervalPacket::decode)
                .consumerMainThread(SetSpawnIntervalPacket::handle)
                .add();

        // 1. Клиент -> сервер
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

        // 2. Сервер -> клиент
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

        // Сервер -> клиент
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

        // Доступ фракций в лабиринт
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

        // SwitchFractionPacket
        CHANNEL.messageBuilder(SwitchFractionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SwitchFractionPacket::encode)
                .decoder(SwitchFractionPacket::decode)
                .consumerMainThread(SwitchFractionPacket::handle)
                .add();
    }
}