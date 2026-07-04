package com.labyrinthmod.common.event;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.capability.PossessionProvider;
import com.labyrinthmod.common.entity.GriverEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID)
public class GriverPossessionHandler {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();

        if (player.isSpectator() && target instanceof GriverEntity griver) {
            if (!griver.isVehicle() && griver.isSaddled()) {
                player.startRiding(griver);

                player.getCapability(PossessionProvider.POSSESSION).ifPresent(data -> {
                    data.setPossessing(true);
                    data.setPossessedEntityId(griver.getId());
                });

                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aВы сели на гривера!"));
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;

        // Выход из управления по SHIFT
        if (player.isShiftKeyDown() && player.isPassenger()) {
            if (player.getVehicle() instanceof GriverEntity) {
                player.stopRiding();

                player.getCapability(PossessionProvider.POSSESSION).ifPresent(data -> {
                    data.setPossessing(false);
                    data.setPossessedEntityId(-1);
                });

                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cВы слезли с гривера"));
            }
        }
    }
}