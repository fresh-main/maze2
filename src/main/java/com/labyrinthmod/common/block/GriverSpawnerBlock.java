package com.labyrinthmod.common.block;

import com.labyrinthmod.common.block.entity.GriverSpawnerBlockEntity;
import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.entity.GriverEntity;
import com.labyrinthmod.common.entity.GriverEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class GriverSpawnerBlock extends Block implements EntityBlock {

    public GriverSpawnerBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GriverSpawnerBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        // Проверяем фракцию OPERATOR (вместо hasPermissions)
        boolean isOperator = player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction() == FractionType.OPERATOR)
                .orElse(false);

        if (!isOperator) {
            player.sendSystemMessage(Component.literal("§kЭтот блок могут использовать только операторы!"));
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof GriverSpawnerBlockEntity spawnerBE)) {
                return InteractionResult.FAIL;
            }

            UUID existingGriverId = spawnerBE.getGriverUUID();
            boolean griverExists = false;

            if (existingGriverId != null) {
                // Ищем гривера по UUID в мире
                for (var e : serverLevel.getEntitiesOfClass(GriverEntity.class,
                        new net.minecraft.world.phys.AABB(
                                serverLevel.getWorldBorder().getMinX(), -64, serverLevel.getWorldBorder().getMinZ(),
                                serverLevel.getWorldBorder().getMaxX(), 320, serverLevel.getWorldBorder().getMaxZ()))) {
                    if (e.getUUID().equals(existingGriverId) && e.isAlive()) {
                        griverExists = true;
                        player.sendSystemMessage(Component.literal(
                                "§cС этого спавнера уже был вызван гривер! Он всё ещё жив (ID: " + e.getId() +
                                        ", позиция: " + (int)e.getX() + " " + (int)e.getY() + " " + (int)e.getZ() + ")"));
                        break;
                    }
                }

                // Если гривер мёртв — очищаем запись
                if (!griverExists) {
                    spawnerBE.clearGriver();
                }
            }

            if (griverExists) {
                return InteractionResult.FAIL;
            }

            // Спавним нового гривера
            BlockPos spawnAt = pos.above();
            GriverEntity griver = GriverEntityType.GRIVER.get().create(serverLevel);
            if (griver != null) {
                griver.setPos(spawnAt.getX() + 0.5, spawnAt.getY(), spawnAt.getZ() + 0.5);
                griver.setSaddled(true);
                griver.setHomePos(spawnAt);
                griver.setSpawnerBlockPos(pos); // Запоминаем позицию спавнера
                serverLevel.addFreshEntity(griver);

                // Сохраняем UUID в BlockEntity (автоматически сохранится в NBT мира)
                spawnerBE.setGriverUUID(griver.getUUID());

                player.sendSystemMessage(Component.literal(
                        "§aГривер спавнен на " + spawnAt.getX() + " " + spawnAt.getY() + " " + spawnAt.getZ() +
                                " (ID: " + griver.getId() + ")"));
            }
        }
        return InteractionResult.SUCCESS;
    }
}