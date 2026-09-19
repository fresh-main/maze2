package com.labyrinthmod.common.contraption;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.data.LabyrinthShiftZone;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.UUID;

public class LabyrinthAssembler {

    public static UUID assembleZone(ServerLevel level, LabyrinthShiftZone zone) {
        if (zone.contraptionEntityId != null) {
            return zone.contraptionEntityId;
        }

        BlockPos min = zone.minPos;
        BlockPos max = zone.maxPos;

        int minCX = min.getX() >> 4;
        int maxCX = max.getX() >> 4;
        int minCZ = min.getZ() >> 4;
        int maxCZ = max.getZ() >> 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                ChunkAccess chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    LabyrinthMod.LOGGER.debug(
                            "[LabyrinthAssembler] Chunk ({},{}) not loaded, skip zone {}",
                            cx, cz, zone.id);
                    return null;
                }
            }
        }

        LabyrinthContraption contraption = new LabyrinthContraption();
        contraption.captureArea(level, min, max);

        if (contraption.getBlocks().isEmpty()) {
            LabyrinthMod.LOGGER.warn(
                    "[LabyrinthAssembler] Zone {} — no blocks at {}..{}",
                    zone.id, min, max);
            return null;
        }

        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);

        OrientedContraptionEntity entity =
                OrientedContraptionEntity.create(level, contraption, Direction.SOUTH);
        entity.setPos(min.getX() + 0.5, min.getY(), min.getZ() + 0.5);

        level.addFreshEntity(entity);

        UUID entityId = entity.getUUID();
        zone.contraptionEntityId = entityId;

        LabyrinthMod.LOGGER.info(
                "[LabyrinthAssembler] Assembled zone {} at {} entity={}",
                zone.id, min, entityId);

        return entityId;
    }

    public static boolean moveZone(ServerLevel level, LabyrinthShiftZone zone,
                                   boolean toVariantB) {
        if (zone.contraptionEntityId == null) return false;
        if (zone.offsetY != 0) return false;
        if ((zone.offsetX == 0) == (zone.offsetZ == 0)) return false;

        Entity entity = level.getEntity(zone.contraptionEntityId);
        if (entity == null) {
            for (Entity e : level.getAllEntities()) {
                if (e.getUUID().equals(zone.contraptionEntityId)) {
                    entity = e;
                    break;
                }
            }
        }

        if (!(entity instanceof OrientedContraptionEntity oce)) {
            zone.contraptionEntityId = null;
            return false;
        }

        int dx = toVariantB ? zone.offsetX : -zone.offsetX;
        int dz = toVariantB ? zone.offsetZ : -zone.offsetZ;

        double newX = oce.getX() + dx;
        double newY = oce.getY();
        double newZ = oce.getZ() + dz;

        BlockPos srcMin = zone.minPos;
        BlockPos targetMin = new BlockPos(
                srcMin.getX() + dx, srcMin.getY(), srcMin.getZ() + dz);

        oce.setPos(newX, newY, newZ);

        zone.minPos = targetMin;
        zone.maxPos = new BlockPos(
                targetMin.getX() + (zone.maxPos.getX() - srcMin.getX()),
                targetMin.getY(),
                targetMin.getZ() + (zone.maxPos.getZ() - srcMin.getZ()));

        LabyrinthMod.LOGGER.info(
                "[LabyrinthAssembler] Moved zone {} {} dir=({},{})",
                zone.id, toVariantB ? "A->B" : "B->A", dx, dz);

        return true;
    }

    public static OrientedContraptionEntity findContraptionEntity(
            ServerLevel level, LabyrinthShiftZone zone) {
        if (zone.contraptionEntityId == null) return null;

        Entity entity = level.getEntity(zone.contraptionEntityId);
        if (entity == null) {
            for (Entity e : level.getAllEntities()) {
                if (e.getUUID().equals(zone.contraptionEntityId)) {
                    entity = e;
                    break;
                }
            }
        }

        if (entity instanceof OrientedContraptionEntity oce) {
            return oce;
        }

        LabyrinthMod.LOGGER.warn(
                "[LabyrinthAssembler] Zone {} — entity {} lost, resetting",
                zone.id, zone.contraptionEntityId);
        zone.contraptionEntityId = null;
        return null;
    }
}
