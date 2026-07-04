package com.otbor.common;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID)
public final class CommonEvents {

    private CommonEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        dumpBlockedSlots(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        dumpBlockedSlots(player);
    }

    private static void dumpBlockedSlots(Player player) {
        if (player.isCreative()) return;
        Inventory inv = player.getInventory();
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                inv.setItem(i, ItemStack.EMPTY);
                player.drop(stack, false);
            }
        }
    }
}
