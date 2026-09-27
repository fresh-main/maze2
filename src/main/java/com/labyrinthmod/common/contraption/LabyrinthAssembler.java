package com.labyrinthmod.common.contraption;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.data.LabyrinthShiftZone;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class LabyrinthAssembler {

    public static void refreshLighting(ServerLevel level, BlockPos min, BlockPos max) {
        BlockPos lightMin = min.offset(-1, -1, -1);
        BlockPos lightMax = max.offset(1, 1, 1);

        // 1. Сообщаем LightEngine о необходимости пересчета для каждого блока в зоне и вокруг неё
        for (BlockPos pos : BlockPos.betweenClosed(lightMin, lightMax)) {
            level.getChunkSource().getLightEngine().checkBlock(pos);
        }

        // 2. Принудительно и мгновенно запускаем распространение света для затронутых чанков
        int minCX = lightMin.getX() >> 4;
        int maxCX = lightMax.getX() >> 4;
        int minCZ = lightMin.getZ() >> 4;
        int maxCZ = lightMax.getZ() >> 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                // ★ ИСПРАВЛЕНИЕ: используем ChunkPos для принудительного пересчета света ★
                ChunkPos chunkPos = new ChunkPos(cx, cz);
                level.getChunkSource().getLightEngine().propagateLightSources(chunkPos);
            }
        }

        // 3. Отправляем обновления блоков клиентам.
        // Теперь, когда свет на сервере уже гарантированно пересчитан,
        // клиенты получат корректные данные об освещении.
        for (BlockPos pos : BlockPos.betweenClosed(lightMin, lightMax)) {
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

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

        // Удаляем блоки из мира
        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);

        // ★ ИСПРАВЛЕНИЕ: обновляем свет СТРОГО ПОСЛЕ удаления блоков ★
        refreshLighting(level, min, max);

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

    public static void tick(ServerLevel level) {
        if (activeMoves.isEmpty()) return;

        Iterator<Map.Entry<UUID, ActiveMove>> iterator = activeMoves.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveMove move = iterator.next().getValue();
            if (move.entity.isRemoved()) {
                move.zone.contraptionEntityId = null;
                iterator.remove();
                continue;
            }

            move.ticksRemaining--;
            if (move.ticksRemaining <= 0) {
                move.entity.setPos(move.targetX, move.entity.getY(), move.targetZ);
                move.zone.minPos = move.targetMin;
                move.zone.maxPos = move.targetMax;

                // ★ ИСПРАВЛЕНИЕ: после завершения движения обновляем свет
                // как в старой позиции (там теперь пусто), так и в новой ★
                refreshLighting(level, move.sourceMin, move.sourceMax);
                refreshLighting(level, move.targetMin, move.targetMax);

                iterator.remove();
                LabyrinthMod.LOGGER.info(
                        "[LabyrinthAssembler] Zone {} movement finished", move.zone.id);
            } else {
                move.entity.setPos(
                        move.entity.getX() + move.stepX,
                        move.entity.getY(),
                        move.entity.getZ() + move.stepZ);
            }
        }
    }

    private static final int MOVE_DURATION_TICKS = 100;
    private static final Map<UUID, ActiveMove> activeMoves = new HashMap<>();

    private static final class ActiveMove {
        final LabyrinthShiftZone zone;
        final OrientedContraptionEntity entity;
        final double stepX;
        final double stepZ;
        final double targetX;
        final double targetZ;
        final BlockPos targetMin;
        final BlockPos targetMax;
        // ★ НОВОЕ: запоминаем исходную область для обновления света после движения ★
        final BlockPos sourceMin;
        final BlockPos sourceMax;
        int ticksRemaining;

        ActiveMove(LabyrinthShiftZone zone, OrientedContraptionEntity entity,
                   double stepX, double stepZ, double targetX, double targetZ,
                   BlockPos targetMin, BlockPos targetMax, int ticksRemaining) {
            this.zone = zone;
            this.entity = entity;
            this.stepX = stepX;
            this.stepZ = stepZ;
            this.targetX = targetX;
            this.targetZ = targetZ;
            this.targetMin = targetMin;
            this.targetMax = targetMax;
            this.sourceMin = zone.minPos;
            this.sourceMax = zone.maxPos;
            this.ticksRemaining = ticksRemaining;
        }
    }

    public static boolean moveZone(ServerLevel level, LabyrinthShiftZone zone,
                                   boolean toVariantB) {
        if (zone.contraptionEntityId == null) return false;
        if (zone.offsetY != 0) return false;
        if ((zone.offsetX == 0) == (zone.offsetZ == 0)) return false;
        if (activeMoves.containsKey(zone.id)) return false;

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

        BlockPos srcMin = zone.minPos;
        BlockPos targetMin = new BlockPos(
                srcMin.getX() + dx, srcMin.getY(), srcMin.getZ() + dz);
        BlockPos targetMax = new BlockPos(
                targetMin.getX() + (zone.maxPos.getX() - srcMin.getX()),
                targetMin.getY(),
                targetMin.getZ() + (zone.maxPos.getZ() - srcMin.getZ()));

        double targetX = oce.getX() + dx;
        double targetZ = oce.getZ() + dz;
        double stepX = dx / (double) MOVE_DURATION_TICKS;
        double stepZ = dz / (double) MOVE_DURATION_TICKS;

        activeMoves.put(zone.id, new ActiveMove(zone, oce, stepX, stepZ,
                targetX, targetZ, targetMin, targetMax, MOVE_DURATION_TICKS));

        LabyrinthMod.LOGGER.info(
                "[LabyrinthAssembler] Zone {} {} started, dir=({},{})",
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