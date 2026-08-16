package com.labyrinthmod.common.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;

public class StructurePlacement {
    private final ResourceLocation nbtLocation;
    private final BlockPos origin;
    private final String name;

    public StructurePlacement(String name, String modid, String nbtName, BlockPos origin) {
        this.name = name;
        this.nbtLocation = new ResourceLocation(modid, nbtName);
        this.origin = origin;
    }

    public BlockPos getOrigin() {
        return origin;
    }

    public String getName() {
        return name;
    }

    /**
     * Размещает структуру в мире
     */
    public void place(WorldGenLevel level, StructureManager structureManager) {
        // В 1.20.1 шаблоны NBT загружаются через StructureTemplateManager
        StructureTemplateManager templateManager = level.getLevel().getServer().getStructureManager();
        Optional<StructureTemplate> optional = templateManager.get(nbtLocation);

        if (optional.isEmpty()) {
            System.err.println("[StructureGenerator] Structure not found: " + nbtLocation);
            System.err.println("[StructureGenerator] Check path: src/main/resources/data/"
                    + nbtLocation.getNamespace() + "/structures/" + nbtLocation.getPath() + ".nbt");
            return;
        }

        StructureTemplate template = optional.get();

        // ★ ОТЛАДКА: выводим размер структуры ★
        System.out.println("[StructureGenerator] Loading '" + name + "' from " + nbtLocation);
        System.out.println("[StructureGenerator] Template size: " + template.getSize() + " (X, Y, Z)");
        System.out.println("[StructureGenerator] Placing at: " + origin);

        StructurePlaceSettings settings = new StructurePlaceSettings();
        settings.setIgnoreEntities(false); // Обязательно false, чтобы сохранять BlockEntity (NBT)
        settings.setKnownShape(true);      // Сохраняет точную форму, включая воздух

        // Фиксированный seed, чтобы структура всегда была одинаковой
        RandomSource random = RandomSource.create(12345L);

        // ★ ИСПРАВЛЕНО: используем this.origin вместо def.origin ★
        template.placeInWorld(level, origin, origin, settings, random, 19);

        System.out.println("[StructureGenerator] Successfully placed '" + name + "'!");
    }
}