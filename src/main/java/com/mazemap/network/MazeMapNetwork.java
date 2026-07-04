package com.mazemap.network;

import com.labyrinthmod.LabyrinthMod;
import com.mazemap.network.packet.C2SRequestMapPacket;
import com.mazemap.network.packet.C2STransferFragmentsPacket;
import com.mazemap.network.packet.S2CClearMapPacket;
import com.mazemap.network.packet.S2CFragmentSyncPacket;
import com.mazemap.network.packet.S2COpenMapPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class MazeMapNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("mazemap", "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int nextId = 0;

    private MazeMapNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(S2CFragmentSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CFragmentSyncPacket::encode)
                .decoder(S2CFragmentSyncPacket::decode)
                .consumerMainThread(S2CFragmentSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2COpenMapPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2COpenMapPacket::encode)
                .decoder(S2COpenMapPacket::decode)
                .consumerMainThread(S2COpenMapPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2STransferFragmentsPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2STransferFragmentsPacket::encode)
                .decoder(C2STransferFragmentsPacket::decode)
                .consumerMainThread(C2STransferFragmentsPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SRequestMapPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SRequestMapPacket::encode)
                .decoder(C2SRequestMapPacket::decode)
                .consumerMainThread(C2SRequestMapPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CClearMapPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CClearMapPacket::encode)
                .decoder(S2CClearMapPacket::decode)
                .consumerMainThread(S2CClearMapPacket::handle)
                .add();
    }
}
