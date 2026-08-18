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
    private final boolean breakable; // ★ НОВОЕ: можно ли ломать структуру

    // ★ НОВЫЙ конструктор с флагом breakable
    public StructurePlacement(String name, String modid, String nbtName, BlockPos origin, boolean breakable) {
        this.name = name;
        this.nbtLocation = new ResourceLocation(modid, nbtName);
        this.origin = origin;
        this.breakable = breakable;
    }

    // ★ СТАРЫЙ конструктор (обратная совместимость — по умолчанию ломается)
    public StructurePlacement(String name, String modid, String nbtName, BlockPos origin) {
        this(name, modid, nbtName, origin, true);
    }

    public BlockPos getOrigin() { return origin; }
    public String getName() { return name; }
    public ResourceLocation getNbtLocation() { return nbtLocation; }
    public boolean isBreakable() { return breakable; } // ★ ГЕТТЕР

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
        RandomSource random = RandomSource.create(12345L);
        template.placeInWorld(level, origin, origin, settings, random, 19);
    }
}