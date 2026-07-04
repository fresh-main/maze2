package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.data.CraftRestrictionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class C2SSaveCraftRestrictionsPacket {
    private final ResourceLocation itemId;
    private final Set<String> forbiddenFactions;

    public C2SSaveCraftRestrictionsPacket(ResourceLocation itemId, Set<String> forbiddenFactions) {
        this.itemId = itemId;
        this.forbiddenFactions = forbiddenFactions;
    }

    public static void encode(C2SSaveCraftRestrictionsPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.itemId);
        buf.writeVarInt(msg.forbiddenFactions.size());
        for (String f : msg.forbiddenFactions) buf.writeUtf(f);
    }

    public static C2SSaveCraftRestrictionsPacket decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        int size = buf.readVarInt();
        Set<String> factions = new HashSet<>();
        for (int i = 0; i < size; i++) factions.add(buf.readUtf());
        return new C2SSaveCraftRestrictionsPacket(id, factions);
    }

    public static void handle(C2SSaveCraftRestrictionsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) { // Только для админов
                Item item = ForgeRegistries.ITEMS.getValue(msg.itemId);
                if (item != null) {
                    CraftRestrictionManager.setRestrictions(item, msg.forbiddenFactions);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}