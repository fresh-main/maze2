package com.labyrinthmod.common.event;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.entity.GriverEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID)
public class DebugStickHandler {

    private static final Map<UUID, Long> lastClickTime = new HashMap<>();
    private static final long CLICK_COOLDOWN = 500;

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack hand = event.getItemStack();

        if (hand.getItem() == Items.DEBUG_STICK && (player.isCreative() || player.hasPermissions(2))) {
            UUID uuid = player.getUUID();
            long now = System.currentTimeMillis();
            if (lastClickTime.containsKey(uuid) && now - lastClickTime.get(uuid) < CLICK_COOLDOWN) return;
            lastClickTime.put(uuid, now);

            if (event.getTarget() instanceof GriverEntity g) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§6=== Гривер ==="));
                player.sendSystemMessage(Component.literal("§eID: §f" + g.getId()));
                player.sendSystemMessage(Component.literal("§eПозиция: §f" + (int) g.getX() + " " + (int) g.getY() + " " + (int) g.getZ()));
                player.sendSystemMessage(Component.literal("§eЗдоровье: §f" + (int) g.getHealth() + "/" + (int) g.getMaxHealth()));
                player.sendSystemMessage(Component.literal("§eПатрулирует: §f" + g.isPatrolling()));
                player.sendSystemMessage(Component.literal("§7Для управления используйте §fпатрульную палочку §7или §f/griver admin"));
            }
        }
    }
}
