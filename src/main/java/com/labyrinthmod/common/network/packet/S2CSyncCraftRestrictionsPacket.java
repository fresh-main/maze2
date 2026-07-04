package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.client.gui.CraftRestrictionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class S2CSyncCraftRestrictionsPacket {
    private final ResourceLocation itemId;
    private final Set<String> forbiddenFactions;

    public S2CSyncCraftRestrictionsPacket(ResourceLocation itemId, Set<String> forbiddenFactions) {
        this.itemId = itemId;
        this.forbiddenFactions = forbiddenFactions;
    }

    public static void encode(S2CSyncCraftRestrictionsPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.itemId);
        buf.writeVarInt(msg.forbiddenFactions.size());
        for (String f : msg.forbiddenFactions) buf.writeUtf(f);
    }

    public static S2CSyncCraftRestrictionsPacket decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        int size = buf.readVarInt();
        Set<String> factions = new HashSet<>();
        for (int i = 0; i < size; i++) factions.add(buf.readUtf());
        return new S2CSyncCraftRestrictionsPacket(id, factions);
    }

    public static void handle(S2CSyncCraftRestrictionsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Обновляем галочки в открытом меню
            if (Minecraft.getInstance().screen instanceof CraftRestrictionScreen screen) {
                screen.updateCheckedFactions(msg.forbiddenFactions);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}