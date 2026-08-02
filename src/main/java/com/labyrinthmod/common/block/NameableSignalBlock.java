package com.labyrinthmod.common.block;

import com.labyrinthmod.client.screen.NameableSignalScreen;
import com.labyrinthmod.common.block.entity.NameableSignalBlockEntity;
import com.labyrinthmod.common.block.entity.NamedBlockManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

public class NameableSignalBlock extends BaseEntityBlock {

    public NameableSignalBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NameableSignalBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof NameableSignalBlockEntity signalBe) {
            return signalBe.isPowered() ? 15 : 0;
        }
        return 0;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        openNameScreen(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    @OnlyIn(Dist.CLIENT)
    private void openNameScreen(Level level, BlockPos pos, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof NameableSignalBlockEntity signalBe) {
            Minecraft.getInstance().setScreen(new NameableSignalScreen(pos, signalBe.getCustomName()));
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            // Если блок разрушен, удаляем его из менеджера имен
            NamedBlockManager.unregisterName(pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}