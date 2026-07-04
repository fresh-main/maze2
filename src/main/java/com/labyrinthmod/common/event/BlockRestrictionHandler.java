package com.labyrinthmod.common.event;

import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class BlockRestrictionHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        BlockPos pos = event.getPos();

        if (level.isClientSide) return;

        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        // Получаем ID блока в формате "modid:block_name"
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
        if (blockId == null) return;

        String blockIdString = blockId.toString();

        // Проверяем по конфигу (теперь синхронизированному с сервером)
        if (ModConfig.isBlockRestricted(blockIdString)) {
            // Только операторы могут использовать эти блоки
            boolean isOperator = player.getCapability(FractionProvider.FRACTION)
                    .map(data -> data.getFraction() == FractionType.OPERATOR)
                    .orElse(false);

            if (!isOperator) {
                event.setCanceled(true);
                event.setUseBlock(Event.Result.DENY);

                // Опционально: сообщение игроку
                if (!event.isCanceled()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§cЭтот блок запрещён для вашей фракции!"));
                }
            }
        }
    }
}