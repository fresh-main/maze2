package com.labyrinthmod.common.block;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import com.labyrinthmod.common.init.ModBlockEntities;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.PlaceItemPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public class BulletinBoardBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BulletinBoardBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        ItemStack heldItem = player.getItemInHand(hand);

        // ==========================================================
        // SHIFT + ПКМ СО СВИТКОМ -> ПОЛОЖИТЬ СВИТОК НА ДОСКУ
        // ==========================================================
        if (player.isSecondaryUseActive() && heldItem.getItem() instanceof com.labyrinthmod.common.item.TaskScrollItem) {
            if (!level.isClientSide) {
                if (level.getBlockEntity(pos) instanceof BulletinBoardBlockEntity board) {
                    if (board.placeScrollOnBoard(heldItem, player)) {
                        heldItem.shrink(1);

                        if (player.containerMenu != null) {
                            player.containerMenu.broadcastChanges();
                        }
                    }
                }
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // ==========================================================
        // ЕСЛИ В РУКЕ ЕСТЬ ПРЕДМЕТ — НЕ ОТКРЫВАЕМ ДОСКУ,
        // ДАЁМ ПРЕДМЕТУ ОБРАБОТАТЬ КЛИК САМОМУ
        // ==========================================================
        if (!heldItem.isEmpty()) {
            return InteractionResult.PASS;
        }

        // ==========================================================
        // ПКМ ПУСТОЙ РУКОЙ -> ОТКРЫТЬ ДОСКУ
        // ==========================================================
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof BulletinBoardBlockEntity board) {
                NetworkHooks.openScreen(serverPlayer, board, buf -> buf.writeBlockPos(pos));
                board.sendTasksToPlayer(serverPlayer);
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            level.removeBlockEntity(pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BulletinBoardBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof BulletinBoardBlockEntity board) {
            }
        };
    }
}