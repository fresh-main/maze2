package com.labyrinthmod.common.entity;

import com.labyrinthmod.common.patrol.PatrolManager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Кастомный NodeEvaluator — не даёт pathfind'у заходить в exclusion zones.
 * Блоки внутри зон помечаются как BLOCKED, поэтому путь будет строиться в обход.
 */
public class GriverNodeEvaluator extends WalkNodeEvaluator {

    private List<PatrolManager.ExclusionZone> exclusionZones = Collections.emptyList();

    public void setExclusionZones(List<PatrolManager.ExclusionZone> zones) {
        this.exclusionZones = zones == null ? Collections.emptyList() : new ArrayList<>(zones);
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) {
        if (!exclusionZones.isEmpty()) {
            for (PatrolManager.ExclusionZone zone : exclusionZones) {
                if (x >= zone.minX && x <= zone.maxX && z >= zone.minZ && z <= zone.maxZ) {
                    return BlockPathTypes.BLOCKED;
                }
            }
        }
        return super.getBlockPathType(level, x, y, z);
    }
}
