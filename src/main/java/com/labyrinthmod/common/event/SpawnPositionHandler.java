package com.labyrinthmod.common.event;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "labyrinthmod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpawnPositionHandler {

    private static final int SPAWN_X = 0;
    private static final int SPAWN_Y = -18;
    private static final int SPAWN_Z = 0;
    private static final BlockPos SPAWN_POS = new BlockPos(SPAWN_X, SPAWN_Y, SPAWN_Z);

    // ★ КЛЮЧ ДЛЯ ХРАНЕНИЯ В PERSISTENT DATA ИГРОКА ★
    private static final String KEY_FIRST_JOIN = "labyrinthmod_first_join";
    private static final String KEY_LIFT_EXECUTED = "labyrinthmod_lift_executed";

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (level.dimension().equals(Level.OVERWORLD)) {
                for (int dy = 0; dy < 3; dy++) {
                    BlockPos pos = SPAWN_POS.above(dy);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                level.setDefaultSpawnPos(SPAWN_POS, 0.0f);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!player.level().dimension().equals(Level.OVERWORLD)) return;

            // ★ ЧИТАЕМ ДАННЫЕ ИЗ PERSISTENT DATA ИГРОКА ★
            CompoundTag persistent = player.getPersistentData();

            boolean hasJoined = persistent.getBoolean(KEY_FIRST_JOIN);
            System.out.println("[SpawnDebug] Player " + player.getName().getString()
                    + " hasJoined=" + hasJoined);

            if (!hasJoined) {
                // ПЕРВЫЙ ЗАХОД — телепортируем
                player.teleportTo(SPAWN_X + 0.5, SPAWN_Y, SPAWN_Z + 0.5);

                // ★ ЗАПИСЫВАЕМ ФЛАГ — СОХРАНЯЕТСЯ АВТОМАТИЧЕСКИ ★
                persistent.putBoolean(KEY_FIRST_JOIN, true);

                System.out.println("[SpawnDebug] First join! Teleported to " + SPAWN_POS);
            } else {
                System.out.println("[SpawnDebug] Already joined, skipping teleport.");
            }
        }
    }
}