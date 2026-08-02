package com.labyrinthmod.common.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.Map;

public class NamedBlockManager {
    // Хранилище: Координаты блока -> Его имя
    private static final Map<BlockPos, String> namedBlocks = new HashMap<>();

    public static void registerName(BlockPos pos, String name) {
        if (name != null && !name.isEmpty() && !name.equals("Без имени")) {
            namedBlocks.put(pos.immutable(), name);
        } else {
            namedBlocks.remove(pos.immutable());
        }
    }

    public static void unregisterName(BlockPos pos) {
        namedBlocks.remove(pos.immutable());
    }

    public static int activateByName(ServerLevel level, String targetName) {
        int count = 0;
        for (Map.Entry<BlockPos, String> entry : namedBlocks.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(targetName)) {
                BlockEntity be = level.getBlockEntity(entry.getKey());
                if (be instanceof NameableSignalBlockEntity signalBe) {
                    signalBe.setPowered(true);
                    count++;
                }
            }
        }
        return count;
    }

    public static int deactivateByName(ServerLevel level, String targetName) {
        int count = 0;
        for (Map.Entry<BlockPos, String> entry : namedBlocks.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(targetName)) {
                BlockEntity be = level.getBlockEntity(entry.getKey());
                if (be instanceof NameableSignalBlockEntity signalBe) {
                    signalBe.setPowered(false);
                    count++;
                }
            }
        }
        return count;
    }
}