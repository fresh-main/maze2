package com.labyrinthmod.common.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
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

        java.util.UUID uuid = player.getUUID();

        // ★ КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ №1 ★
        // Если этот игрок сам является инициатором блокировки (то есть он первый зашёл и запустил ритуал),
        // мы НЕ должны его замораживать. Он должен нормально пройти свой ритуал.
        if (LiftLockManager.isLocked() && uuid.equals(LiftLockManager.getInitiator())) {
            System.out.println("[LiftLock] Игрок " + player.getName().getString() + " является инициатором ритуала. Пропускаем заморозку.");
            return;
        }

        CompoundTag persistent = player.getPersistentData();
        boolean isOldPlayer = persistent.getBoolean(KEY_LIFT_EXECUTED);

        // ★ ЕСЛИ МИР ЗАБЛОКИРОВАН И ЭТО НЕ ИНИЦИАТОР ★
        if (LiftLockManager.isLocked()) {
            if (isOldPlayer) {
                // Старый игрок (есть флаг) — игнорируем блокировку, заходит нормально
                System.out.println("[LiftLock] Старый игрок " + player.getName().getString() + " пропущен.");
            } else {
                // Новый игрок — замораживаем и показываем UI
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

        // ★ ЕСЛИ ВЫШЕЛ ИНИЦИАТОР РИТУАЛА ★
        if (LiftLockManager.isLocked() && uuid.equals(LiftLockManager.getInitiator())) {
            System.out.println("[LiftLock] Инициатор вышел из игры! Принудительный сброс лифта (Этап 3).");
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                LiftCommandHandler.respawnLiftStructure(server, LiftCommandHandler.LIFT_1_NAME, LiftCommandHandler.LIFT_1_POS);
                LiftCommandHandler.respawnLiftStructure(server, LiftCommandHandler.LIFT_2_NAME, LiftCommandHandler.LIFT_2_POS);
            }
            LiftLockManager.unlockAndReset(server);
        }
    }
}