package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.client.screen.BulletinBoardAdminScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record OpenQuestAdminPacket() {
    public static void encode(OpenQuestAdminPacket packet, FriendlyByteBuf buf) {}
    public static OpenQuestAdminPacket decode(FriendlyByteBuf buf) { return new OpenQuestAdminPacket(); }
    public static void handle(OpenQuestAdminPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new BulletinBoardAdminScreen(null)));
        context.setPacketHandled(true);
    }
}
