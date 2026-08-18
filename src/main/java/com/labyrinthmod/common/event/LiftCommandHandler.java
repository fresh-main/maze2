package com.labyrinthmod.common.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "labyrinthmod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LiftCommandHandler {
    // ★ ПЕРВАЯ ЗАДЕРЖКА (оставляем ваше значение 300 тиков) ★
    private static final int DELAY_TICKS = 300;

    // ★ ВТОРАЯ ЗАДЕРЖКА: 3 минуты = 180 секунд = 3600 тиков ★
    private static final int SECOND_DELAY_TICKS = 3600;

    private static final String KEY_LIFT_EXECUTED = "labyrinthmod_lift_executed";

    // Очереди игроков
    private static final Map<UUID, Integer> pendingPlayers = new HashMap<>();
    private static final Map<UUID, Integer> secondPendingPlayers = new HashMap<>(); // ★ НОВАЯ ОЧЕРЕДЬ ★

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!player.level().dimension().equals(Level.OVERWORLD)) return;

            CompoundTag persistent = player.getPersistentData();
            boolean liftDone = persistent.getBoolean(KEY_LIFT_EXECUTED);

            if (!liftDone) {
                pendingPlayers.put(player.getUUID(), 0);
                System.out.println("[LiftCommand] Player " + player.getName().getString()
                        + " queued for /lift lift (waiting for first activation...)");
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // ==========================================
        // 1. ОБРАБОТКА ПЕРВОЙ АКТИВАЦИИ
        // ==========================================
        if (!pendingPlayers.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> iterator = pendingPlayers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Integer> entry = iterator.next();
                UUID uuid = entry.getKey();
                int ticks = entry.getValue() + 1;

                if (ticks >= DELAY_TICKS) {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player != null && player.level().dimension().equals(Level.OVERWORLD)) {
                        // 1. Выполняем команду
                        server.getCommands().performPrefixedCommand(
                                player.createCommandSourceStack(),
                                "lift lift"
                        );

                        // 2. ★ ТЕЛЕПОРТАЦИЯ НА 0, -15, 0 ★
                        player.teleportTo(0, -15, 0);

                        // 3. Ставим в очередь на вторую активацию (через 3 минуты)
                        secondPendingPlayers.put(uuid, 0);

                        // 4. Сохраняем флаг, чтобы не повторять при следующем входе
                        player.getPersistentData().putBoolean(KEY_LIFT_EXECUTED, true);

                        System.out.println("[LiftCommand] Executed 1st /lift lift and teleported "
                                + player.getName().getString() + " to 0, -15, 0. Queued for 2nd activation.");
                    }
                    iterator.remove();
                } else {
                    entry.setValue(ticks);
                }
            }
        }

        // ==========================================
        // 2. ОБРАБОТКА ВТОРОЙ АКТИВАЦИИ (ЧЕРЕЗ 3 МИНУТЫ)
        // ==========================================
        if (!secondPendingPlayers.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> iterator2 = secondPendingPlayers.entrySet().iterator();
            while (iterator2.hasNext()) {
                Map.Entry<UUID, Integer> entry = iterator2.next();
                UUID uuid = entry.getKey();
                int ticks = entry.getValue() + 1;

                if (ticks >= SECOND_DELAY_TICKS) {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player != null && player.level().dimension().equals(Level.OVERWORLD)) {
                        // Выполняем команду второй раз
                        server.getCommands().performPrefixedCommand(
                                player.createCommandSourceStack(),
                                "lift lift"
                        );
                        System.out.println("[LiftCommand] Executed 2nd /lift lift for "
                                + player.getName().getString() + " after 3 minutes.");
                    }
                    iterator2.remove();
                } else {
                    entry.setValue(ticks);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        pendingPlayers.remove(uuid);
        // ★ Если игрок вышел из игры, убираем его из очереди на вторую активацию,
        // чтобы избежать утечек памяти и неожиданных срабатываний в будущем.
        secondPendingPlayers.remove(uuid);
    }
}