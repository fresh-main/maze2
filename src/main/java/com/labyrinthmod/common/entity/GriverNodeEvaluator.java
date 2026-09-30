package com.labyrinthmod.common.entity;

import com.labyrinthmod.common.patrol.PatrolManager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;

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
        var state = level.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
        // Grivers deliberately push through bushes and cobwebs instead of routing
        // around them. Returning WALKABLE also prevents vanilla navigation from
        // assigning the usual hazard/sticky path type to these blocks.
        if (state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.COBWEB)) {
            return BlockPathTypes.WALKABLE;
        }
        // Decorative vegetation with an empty collision shape used to be treated as
        // open air by the custom micro path and grivers repeatedly ran into it.
        if (state.is(BlockTags.LEAVES)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(BlockTags.FENCES)
                || state.is(BlockTags.WALLS)) {
            return BlockPathTypes.BLOCKED;
        }
        return super.getBlockPathType(level, x, y, z);
    }
}
