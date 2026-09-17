package com.labyrinthmod.common.contraption;

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
import org.apache.commons.lang3.tuple.Pair;

public class LabyrinthContraption extends Contraption {

    public static final Holder.Reference<ContraptionType> TYPE = Registry.registerForHolder(
            CreateBuiltInRegistries.CONTRAPTION_TYPE,
            new ResourceLocation("labyrinthmod", "labyrinth_shift"),
            new ContraptionType(LabyrinthContraption::new)
    );

    public static void init() {
    }

    public void captureArea(Level world, BlockPos min, BlockPos max) {
        this.anchor = min;

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;

            Pair<StructureTemplate.StructureBlockInfo, BlockEntity> pair = capture(world, pos);
            addBlock(world, pos, pair);
        }

        startMoving(world);
    }

    @Override
    protected boolean isAnchoringBlockAt(BlockPos pos) {
        return pos.equals(anchor);
    }

    @Override
    public ContraptionType getType() {
        return TYPE.value();
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