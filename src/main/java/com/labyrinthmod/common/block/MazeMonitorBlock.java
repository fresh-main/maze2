package com.labyrinthmod.common.block;

import com.labyrinthmod.common.block.entity.MazeMonitorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

public class MazeMonitorBlock extends Block implements EntityBlock {

    public MazeMonitorBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(3.5f).requiresCorrectToolForDrops());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MazeMonitorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level level, BlockState state, BlockEntityType<T> type) {
        return type == MazeMonitorRegistry.MAZE_MONITOR_BE.get()
                ? (lvl, pos, st, be) -> MazeMonitorBlockEntity.tick(lvl, pos, st, (MazeMonitorBlockEntity) be)
                : null;
    }
}
