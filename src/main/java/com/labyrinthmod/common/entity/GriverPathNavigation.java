package com.labyrinthmod.common.entity;

import com.labyrinthmod.common.patrol.PatrolManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

public class GriverPathNavigation extends GroundPathNavigation {

    public GriverPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new GriverNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, Math.max(maxVisitedNodes * 15, 3000));
    }

    /** Обновляет snapshot зон исключения из PatrolManager. */
    public void syncExclusionZones() {
        if (this.mob.level().isClientSide) return;
        PatrolManager m = PatrolManager.get(this.mob.level());
        if (m != null && this.nodeEvaluator instanceof GriverNodeEvaluator gne) {
            gne.setExclusionZones(m.getExclusionZones());
        }
    }

    // Все entry-points в pathfind проходят через createPath — синкаем зоны здесь.
    // Без этого attack/chase ходил в зоны иммунитета: только один call-site (patrol)
    // сам вызывал syncExclusionZones, остальные использовали стейл-snapshot (пустой
    // на момент инициализации evaluator'а).
    @Override
    public Path createPath(net.minecraft.core.BlockPos pos, int accuracy) {
        syncExclusionZones();
        return super.createPath(pos, accuracy);
    }

    @Override
    public Path createPath(Entity entity, int accuracy) {
        syncExclusionZones();
        return super.createPath(entity, accuracy);
    }
}
