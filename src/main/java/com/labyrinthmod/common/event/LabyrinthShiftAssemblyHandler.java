package com.labyrinthmod.common.event;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.contraption.LabyrinthAssembler;
import com.labyrinthmod.common.data.LabyrinthShiftZone;
import com.labyrinthmod.common.data.LabyrinthZoneSavedData;
import com.labyrinthmod.common.generation.LabyrinthChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Materializes shift zones only after their source blocks exist in real loaded
 * chunks.  World generation itself never mutates SavedData or creates entities.
 */
@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LabyrinthShiftAssemblyHandler {

    private LabyrinthShiftAssemblyHandler() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof LabyrinthChunkGenerator mazeGenerator)) return;

        LabyrinthZoneSavedData data = LabyrinthZoneSavedData.get(level);
        if (!data.hasGeneratedFromMaze()) {
            for (LabyrinthShiftZone zone : mazeGenerator.createShiftZones()) {
                data.addZone(zone);
            }
            data.markGeneratedFromMaze();
        }

        for (LabyrinthShiftZone zone : data.getAllZones().values()) {
            if (zone.contraptionEntityId != null || !isAreaLoaded(level, zone.minPos, zone.maxPos)) continue;

            if (LabyrinthAssembler.assembleZone(level, zone) != null) {
                data.setDirty();
                LabyrinthMod.LOGGER.debug("Assembled labyrinth shift zone {} in {}", zone.id, level.dimension().location());
            }
        }
    }

    private static boolean isAreaLoaded(ServerLevel level, BlockPos min, BlockPos max) {
        for (int chunkX = min.getX() >> 4; chunkX <= max.getX() >> 4; chunkX++) {
            for (int chunkZ = min.getZ() >> 4; chunkZ <= max.getZ() >> 4; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }
}
