package com.labyrinthmod.common.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation; // ★ ИМПОРТ ПОВОРОТА
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import java.util.Optional;

public class StructurePlacement {
    private final ResourceLocation nbtLocation;
    private final BlockPos origin;
    private final String name;
    private final boolean breakable;
    private final Rotation rotation; // ★ НОВОЕ ПОЛЕ

    // ★ НОВЫЙ конструктор с поворотом
    public StructurePlacement(String name, String modid, String nbtName, BlockPos origin, boolean breakable, Rotation rotation) {
        this.name = name;
        this.nbtLocation = new ResourceLocation(modid, nbtName);
        this.origin = origin;
        this.breakable = breakable;
        this.rotation = rotation;
    }

    // ★ СТАРЫЙ конструктор (без поворота, для обратной совместимости)
    public StructurePlacement(String name, String modid, String nbtName, BlockPos origin, boolean breakable) {
        this(name, modid, nbtName, origin, breakable, Rotation.NONE);
    }

    public StructurePlacement(String name, String modid, String nbtName, BlockPos origin) {
        this(name, modid, nbtName, origin, true, Rotation.NONE);
    }

    public BlockPos getOrigin() { return origin; }
    public String getName() { return name; }
    public ResourceLocation getNbtLocation() { return nbtLocation; }
    public boolean isBreakable() { return breakable; }
    public Rotation getRotation() { return rotation; } // ★ ГЕТТЕР ПОВОРОТА

    public void place(WorldGenLevel level, StructureManager structureManager) {
        StructureTemplateManager templateManager = level.getLevel().getServer().getStructureManager();
        Optional<StructureTemplate> optional = templateManager.get(nbtLocation);

        if (optional.isEmpty()) {
            System.err.println("[StructureGenerator] Structure not found: " + nbtLocation);
            return;
        }

        StructureTemplate template = optional.get();
        StructurePlaceSettings settings = new StructurePlaceSettings();
        settings.setIgnoreEntities(false);
        settings.setKnownShape(true);
        settings.setRotation(this.rotation); // ★ ПРИМЕНЯЕМ ПОВОРОТ К НАСТРОЙКАМ

        RandomSource random = RandomSource.create(12345L);
        template.placeInWorld(level, origin, origin, settings, random, 19);
    }
}