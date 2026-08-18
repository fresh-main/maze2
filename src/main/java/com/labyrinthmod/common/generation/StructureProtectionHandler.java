package com.labyrinthmod.common.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;

/**
 * ★ ОБРАБОТЧИК ЗАЩИТЫ СТРУКТУР ★
 * Отменяет разрушение блоков и взрывы внутри защищённых структур.
 *
 * Автоматически регистрируется благодаря @Mod.EventBusSubscriber.
 * Убедитесь, что MOD_ID совпадает с вашим.
 */
@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class StructureProtectionHandler {

    // ★ ЗАЩИТА ОТ РАЗРУШЕНИЯ ИГРОКОМ ★
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockPos pos = event.getPos();

        if (StructureGenerator.isBlockProtected(pos)) {
            event.setCanceled(true);

            // Опционально: сообщение игроку
            Player player = event.getPlayer();
            if (player != null && !player.level().isClientSide) {
                player.displayClientMessage(
                        Component.literal("§c⚠ Этот блок является частью защищённой структуры!"),
                        true // true = action bar (ненавязчивое сообщение)
                );
            }
        }
    }

    // ★ ЗАЩИТА ОТ ВЗРЫВОВ (криперы, TNT, кровати) ★
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        // Удаляем из списка затронутых блоков те, что внутри защищённых структур
        Iterator<BlockPos> iterator = event.getAffectedBlocks().iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (StructureGenerator.isBlockProtected(pos)) {
                iterator.remove();
            }
        }
    }
}