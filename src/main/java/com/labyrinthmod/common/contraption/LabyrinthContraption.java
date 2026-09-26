package com.labyrinthmod.common.contraption;

import com.labyrinthmod.LabyrinthMod;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.RegisterEvent;
import org.apache.commons.lang3.tuple.Pair;

public class LabyrinthContraption extends Contraption {

    public static final ResourceLocation TYPE_ID = new ResourceLocation("labyrinthmod", "labyrinth_shift");
    public static Holder.Reference<ContraptionType> TYPE;

    public static void register(RegisterEvent event) {
        if (!event.getRegistryKey().equals(CreateBuiltInRegistries.CONTRAPTION_TYPE.key())) return;
        if (TYPE != null) return;
        TYPE = Registry.registerForHolder(
                CreateBuiltInRegistries.CONTRAPTION_TYPE,
                TYPE_ID,
                new ContraptionType(LabyrinthContraption::new)
        );
        LabyrinthMod.LOGGER.info("[LabyrinthContraption] Registered contraption type {}", TYPE_ID);
    }

    public static boolean isRegistered() {
        return TYPE != null || CreateBuiltInRegistries.CONTRAPTION_TYPE.containsKey(TYPE_ID);
    }

    public static void init() {
    }

    public void captureArea(Level world, BlockPos min, BlockPos max) {
        this.anchor = min;
        this.bounds = new AABB(min);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;
            Pair<StructureTemplate.StructureBlockInfo, BlockEntity> pair = capture(world, pos);
            addBlock(world, pos, pair);
        }

        // ★ ИСПРАВЛЕНИЕ: Убираем вызов refreshLighting отсюда.
        // На этом этапе блоки ещё не удалены из мира, поэтому обновление света бессмысленно.
        // Свет будет обновлён в LabyrinthAssembler.assembleZone строго после removeBlocksFromWorld.

        startMoving(world);
    }

    @Override
    protected boolean isAnchoringBlockAt(BlockPos pos) {
        return pos.equals(anchor);
    }

    @Override
    public ContraptionType getType() {
        if (TYPE != null) return TYPE.value();
        ContraptionType type = CreateBuiltInRegistries.CONTRAPTION_TYPE.get(TYPE_ID);
        if (type == null) {
            throw new IllegalStateException("Contraption type " + TYPE_ID + " is not registered");
        }
        return type;
    }

    @Override
    public boolean assemble(Level world, BlockPos pos) throws AssemblyException {
        return true;
    }

    @Override
    public boolean canBeStabilized(Direction direction, BlockPos localPos) {
        return false;
    }
}