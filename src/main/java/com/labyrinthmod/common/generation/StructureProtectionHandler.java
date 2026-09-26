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
    private static boolean protectionEnabled = true;

    public static boolean isProtectionEnabled() { return protectionEnabled; }
    public static void setProtectionEnabled(boolean enabled) { protectionEnabled = enabled; }

    // ★ ЗАЩИТА ОТ РАЗРУШЕНИЯ ИГРОКОМ ★
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockPos pos = event.getPos();

        Player player = event.getPlayer();
        boolean isOperator = player != null && player.getCapability(
                com.labyrinthmod.common.capability.FractionProvider.FRACTION)
                .map(data -> data.getFraction() == com.labyrinthmod.common.capability.FractionType.OPERATOR)
                .orElse(false);
        if (protectionEnabled && StructureGenerator.isBlockProtected(pos) && !isOperator) {
            event.setCanceled(true);

            // Опционально: сообщение игроку
            if (player != null && !player.level().isClientSide) {
                player.displayClientMessage(
                        Component.literal("§c⚠ Этот блок является частью защищённой структуры!"),
                        true // true = action bar (ненавязчивое сообщение)
                );
            }
        }
    }

    // ★ ЗАЩИТА ОТ УСТАНОВКИ БЛОКОВ ★
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!protectionEnabled || !StructureGenerator.isBlockProtected(event.getPos())) return;
        if (!(event.getEntity() instanceof Player player)) {
            event.setCanceled(true);
            return;
        }
        boolean isOperator = player.getCapability(
                com.labyrinthmod.common.capability.FractionProvider.FRACTION)
                .map(data -> data.getFraction() == com.labyrinthmod.common.capability.FractionType.OPERATOR)
                .orElse(false);
        if (!isOperator) {
            event.setCanceled(true);
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.literal("§c⚠ В защищённой структуре нельзя ставить блоки!"), true);
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
            if (protectionEnabled && StructureGenerator.isBlockProtected(pos)) {
                iterator.remove();
            }
        }
    }
}
