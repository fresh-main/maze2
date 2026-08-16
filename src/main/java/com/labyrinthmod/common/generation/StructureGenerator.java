package com.labyrinthmod.common.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StructureGenerator {

    private static class StructureDef {
        String name;
        ResourceLocation nbtLocation;
        BlockPos origin;

        StructureDef(String name, String modid, String nbtName, BlockPos origin) {
            this.name = name;
            this.nbtLocation = new ResourceLocation(modid, nbtName);
            this.origin = origin;
        }
    }

    private static final List<StructureDef> structures = new ArrayList<>();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;

        // Убедитесь, что координаты (-17) соответствуют ТОЧНОМУ углу (левому нижнему)
        // структурного блока в момент сохранения NBT-файла!
        structures.add(new StructureDef("lift_1", "labyrinthmod", "lift_1", new BlockPos(-17, -28, -11)));
        structures.add(new StructureDef("lift_2", "labyrinthmod", "lift_2", new BlockPos(-17, 10, -11)));

        initialized = true;
        System.out.println("[StructureGenerator] Initialized " + structures.size() + " structures.");
    }

    public static void placeStructures(WorldGenLevel level, StructureManager structureManager, int chunkX, int chunkZ) {
        init();

        StructureTemplateManager templateManager = level.getLevel().getServer().getStructureManager();

        for (StructureDef def : structures) {
            int originChunkX = def.origin.getX() >> 4;
            int originChunkZ = def.origin.getZ() >> 4;

            // Генерируем только в "родном" чанке точки спавна, чтобы не дублировать
            if (chunkX == originChunkX && chunkZ == originChunkZ) {

                Optional<StructureTemplate> optional = templateManager.get(def.nbtLocation);

                if (optional.isPresent()) {
                    StructureTemplate template = optional.get();

                    System.out.println("[StructureGenerator] Loading '" + def.name + "' from " + def.nbtLocation);
                    System.out.println("[StructureGenerator] Template size: " + template.getSize() + " (X, Y, Z)");
                    System.out.println("[StructureGenerator] Placing at: " + def.origin);

                    StructurePlaceSettings settings = new StructurePlaceSettings();
                    settings.setIgnoreEntities(false);
                    settings.setKnownShape(true);

                    RandomSource random = RandomSource.create(12345L);

                    // Флаг 19 гарантирует применение всех NBT данных (имена, клей Create и т.д.)
                    template.placeInWorld(level, def.origin, def.origin, settings, random, 19);

                    System.out.println("[StructureGenerator] Successfully placed '" + def.name + "'!");
                } else {
                    System.err.println("[StructureGenerator] ERROR: Structure file NOT FOUND: " + def.nbtLocation);
                }
            }
        }
    }
}