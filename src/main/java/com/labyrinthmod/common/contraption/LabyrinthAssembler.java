package com.labyrinthmod.common.contraption;

import com.labyrinthmod.common.data.LabyrinthShiftZone;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Отвечает за превращение статичных блоков лабиринта в сущность Create.
 */
public class LabyrinthAssembler {

    /**
     * Превращает зону в мире в сущность-контрапцию.
     * @return UUID созданной сущности, или null, если сборка не удалась.
     */
    public static UUID assembleZone(ServerLevel level, LabyrinthShiftZone zone) {
        if (zone.contraptionEntityId != null) {
            return zone.contraptionEntityId;
        }

        BlockPos min = zone.minPos;
        BlockPos max = zone.maxPos;

        LabyrinthContraption contraption = new LabyrinthContraption();
        contraption.captureArea(level, min, max);

        if (contraption.getBlocks().isEmpty()) {
            return null;
        }

        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);

        OrientedContraptionEntity entity = OrientedContraptionEntity.create(level, contraption, Direction.SOUTH);
        entity.setPos(min.getX() + 0.5, min.getY(), min.getZ() + 0.5);

        level.addFreshEntity(entity);

        UUID entityId = entity.getUUID();
        zone.contraptionEntityId = entityId;

        return entityId;
    }
}