package com.infection.network;

import com.infection.network.packet.C2SAdminSetNotePacket;
import com.infection.network.packet.C2SHallucinationLaunchPacket;
import com.infection.network.packet.C2SInfectionActionPacket;
import com.infection.network.packet.C2SMiniEventActionPacket;
import com.infection.network.packet.C2STargetedEventLaunchPacket;
import com.infection.network.packet.C2SRequestInfectionListPacket;
import com.infection.network.packet.C2SUpdateSettingsPacket;
import com.infection.network.packet.S2CForceHallucinationPacket;
import com.infection.network.packet.S2CInfectionListPacket;
import com.infection.network.packet.S2CInfectionSyncPacket;
import com.infection.network.packet.S2CMiniEventStatePacket;
import com.infection.network.packet.S2CSettingsSyncPacket;
import com.labyrinthmod.LabyrinthMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {

    private static final String PROTOCOL = "1";
    public static final ResourceLocation CHANNEL_ID = LabyrinthMod.infectionId("main");

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_ID,
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private Network() {}

    public static void register() {
        int id = 0;

        CHANNEL.messageBuilder(S2CInfectionSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CInfectionSyncPacket::encode)
                .decoder(S2CInfectionSyncPacket::decode)
                .consumerMainThread(S2CInfectionSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CInfectionListPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CInfectionListPacket::encode)
                .decoder(S2CInfectionListPacket::decode)
                .consumerMainThread(S2CInfectionListPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SRequestInfectionListPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SRequestInfectionListPacket::encode)
                .decoder(C2SRequestInfectionListPacket::decode)
                .consumerMainThread(C2SRequestInfectionListPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SInfectionActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SInfectionActionPacket::encode)
                .decoder(C2SInfectionActionPacket::decode)
                .consumerMainThread(C2SInfectionActionPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CSettingsSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSettingsSyncPacket::encode)
                .decoder(S2CSettingsSyncPacket::decode)
                .consumerMainThread(S2CSettingsSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SUpdateSettingsPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SUpdateSettingsPacket::encode)
                .decoder(C2SUpdateSettingsPacket::decode)
                .consumerMainThread(C2SUpdateSettingsPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SAdminSetNotePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SAdminSetNotePacket::encode)
                .decoder(C2SAdminSetNotePacket::decode)
                .consumerMainThread(C2SAdminSetNotePacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SMiniEventActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SMiniEventActionPacket::encode)
                .decoder(C2SMiniEventActionPacket::decode)
                .consumerMainThread(C2SMiniEventActionPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CMiniEventStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CMiniEventStatePacket::encode)
                .decoder(S2CMiniEventStatePacket::decode)
                .consumerMainThread(S2CMiniEventStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SHallucinationLaunchPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SHallucinationLaunchPacket::encode)
                .decoder(C2SHallucinationLaunchPacket::decode)
                .consumerMainThread(C2SHallucinationLaunchPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CForceHallucinationPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CForceHallucinationPacket::encode)
                .decoder(S2CForceHallucinationPacket::decode)
                .consumerMainThread(S2CForceHallucinationPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2STargetedEventLaunchPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2STargetedEventLaunchPacket::encode)
                .decoder(C2STargetedEventLaunchPacket::decode)
                .consumerMainThread(C2STargetedEventLaunchPacket::handle)
                .add();
    }
}
