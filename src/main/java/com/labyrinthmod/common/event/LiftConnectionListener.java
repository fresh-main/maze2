package com.labyrinthmod.common.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = "labyrinthmod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LiftConnectionListener {

    private static final String KEY_LIFT_EXECUTED = "labyrinthmod_lift_executed";

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.level().dimension().equals(Level.OVERWORLD)) return;

        ServerLevel level = (ServerLevel) player.level();

        // Если лифт не сгенерирован — ничего не делаем
        if (!LiftCommandHandler.isLiftGenerated(level)) {
            return;
        }

        java.util.UUID uuid = player.getUUID();

        // Если этот игрок — инициатор стандартного ритуала, пропускаем
        if (LiftLockManager.isLocked() && uuid.equals(LiftLockManager.getInitiator())) {
            System.out.println("[LiftLock] Игрок " + player.getName().getString() + " является инициатором. Пропускаем.");
            return;
        }

        CompoundTag persistent = player.getPersistentData();
        boolean isOldPlayer = persistent.getBoolean(KEY_LIFT_EXECUTED);

        // ★ ЕСЛИ МИР ЗАБЛОКИРОВАН (стандартный ритуал ИЛИ квест-ивент) ★
        if (LiftLockManager.isLocked()) {
            if (isOldPlayer) {
                System.out.println("[LiftLock] Старый игрок " + player.getName().getString() + " пропущен.");
            } else {
                System.out.println("[LiftLock] Новый игрок " + player.getName().getString() + " заморожен.");
                LiftLockManager.freezeAndHold(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        java.util.UUID uuid = player.getUUID();
        LiftLockManager.removeWaiting(uuid);

        // Если вышел инициатор стандартного ритуала — сброс
        if (LiftLockManager.isLocked() && uuid.equals(LiftLockManager.getInitiator())) {
            System.out.println("[LiftLock] Инициатор вышел! Принудительный сброс лифта.");
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                LiftCommandHandler.respawnLiftStructure(server, LiftCommandHandler.LIFT_1_NAME, LiftCommandHandler.LIFT_1_POS);
                LiftCommandHandler.respawnLiftStructure(server, LiftCommandHandler.LIFT_2_NAME, LiftCommandHandler.LIFT_2_POS);
            }
            LiftLockManager.unlockAndReset(server);
        }
    }
}