package com.labyrinthmod.common.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.Rotation; // ★ ИМПОРТ ПОВОРОТА
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.LevelReader;
import java.util.List;
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

    public void place(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
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
        settings.setBoundingBox(new BoundingBox(
                chunk.getPos().getMinBlockX(), level.getMinBuildHeight(), chunk.getPos().getMinBlockZ(),
                chunk.getPos().getMaxBlockX(), level.getMaxBuildHeight() - 1, chunk.getPos().getMaxBlockZ()));
        if ("ferma".equals(name) || "banfair".equals(name)
                || "lager".equals(name) || "tower".equals(name)) {
            // В этих шаблонах нижний слой заполнен прямоугольником обычной земли.
            // Оставляем существующий рельеф вместо копирования этого слоя.
            settings.addProcessor(new GladeGroundProcessor());
        }
        if ("most".equals(name)) {
            settings.addProcessor(new BridgeTerrainProcessor());
        }
        if ("lift_2".equals(name)) {
            settings.addProcessor(new LiftOverlapProcessor());
        }

        RandomSource random = RandomSource.create(12345L);
        template.placeInWorld(level, origin, origin, settings, random, 19);
    }

    private static final class GladeGroundProcessor extends BlockIgnoreProcessor {
        private GladeGroundProcessor() {
            super(List.of());
        }

        @Override
        public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos origin,
                BlockPos reference, StructureTemplate.StructureBlockInfo original,
                StructureTemplate.StructureBlockInfo placed, StructurePlaceSettings settings) {
            if (original.pos().getY() == 0 && (
                    placed.state().is(Blocks.AIR) || placed.state().is(Blocks.GRASS_BLOCK)
                    || placed.state().is(Blocks.DIRT) || placed.state().is(Blocks.ROOTED_DIRT)
                    || placed.state().is(Blocks.PODZOL) || placed.state().is(Blocks.COARSE_DIRT))) {
                return null;
            }
            return placed;
        }
    }

    private static final class BridgeTerrainProcessor extends BlockIgnoreProcessor {
        private BridgeTerrainProcessor() {
            super(List.of());
        }

        @Override
        public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos origin,
                BlockPos reference, StructureTemplate.StructureBlockInfo original,
                StructureTemplate.StructureBlockInfo placed, StructurePlaceSettings settings) {
            if (placed.state().isAir()) return null;
            if (original.pos().getY() == 0 && (
                    placed.state().is(Blocks.WATER) || placed.state().is(Blocks.GRASS_BLOCK)
                    || placed.state().is(Blocks.DIRT) || placed.state().is(Blocks.SAND)
                    || placed.state().is(Blocks.GRAVEL) || placed.state().is(Blocks.CLAY))) {
                return null;
            }
            return placed;
        }
    }

    private static final class LiftOverlapProcessor extends BlockIgnoreProcessor {
        private LiftOverlapProcessor() {
            super(List.of());
        }

        @Override
        public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos origin,
                BlockPos reference, StructureTemplate.StructureBlockInfo original,
                StructureTemplate.StructureBlockInfo placed, StructurePlaceSettings settings) {
            // lift_1 заканчивается на Y=20, а lift_2 начинается на Y=11.
            // Воздух нижних десяти слоёв lift_2 не должен вырезать уже поставленный lift_1.
            if (original.pos().getY() <= 9 && placed.state().isAir()) return null;
            return placed;
        }
    }
}
