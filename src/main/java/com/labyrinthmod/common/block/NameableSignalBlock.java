package com.labyrinthmod.common.block;

import com.labyrinthmod.client.screen.NameableSignalScreen;
import com.labyrinthmod.common.block.entity.NameableSignalBlockEntity;
import com.labyrinthmod.common.init.ModBlockEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class NameableSignalBlock extends Block implements EntityBlock {

    public NameableSignalBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NameableSignalBlockEntity(pos, state);
    }

    // ★ КЛЮЧЕВОЙ МЕТОД: открывает экран при клике ★
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            // Клиентская сторона — открываем экран
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof NameableSignalBlockEntity signalBe) {
                Minecraft.getInstance().setScreen(
                        new NameableSignalScreen(pos, signalBe.getCustomName())
                );
            }
            return InteractionResult.SUCCESS;
        }
        // Серверная сторона — подтверждаем действие
        return InteractionResult.CONSUME;
    }

    // ★ РЕГИСТРАЦИЯ ТИКЕРА ★
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null; // Тикаем только на сервере
        }
        return type == ModBlockEntities.NAMEABLE_SIGNAL_BE.get()
                ? (lvl, pos, st, be) -> ((NameableSignalBlockEntity) be).tick()
                : null;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof NameableSignalBlockEntity signalBe) {
            return signalBe.isPowered() ? 15 : 0;
        }
        return 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            // Блок разрушен — NBT удаляется автоматически
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}