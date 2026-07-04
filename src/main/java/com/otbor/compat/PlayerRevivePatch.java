package com.otbor.compat;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Патч-мост для PlayerRevive: возвращает возможность ДОБИТЬ downed-игрока.
 *
 * Раньше PlayerRevive отменял весь урон bleeding-игроку (3 config-флага). Мы
 * патчим сам PR-jar, но если на сервере живёт старая версия, этот compat
 * un-cancel'ит damage пост-фактум.
 *
 * Слушаем {@link LivingHurtEvent} с приоритетом LOWEST и receiveCanceled=true
 * — наш handler запускается ПОСЛЕ всех остальных. Если у нас victim-игрок и
 * event кем-то отменён — снимаем cancellation БЕЗУСЛОВНО. Это работает для
 * любого источника урона (PvP/mob/projectile/прочее) — то есть downed-игрока
 * становится возможно «добить» абсолютно чем угодно.
 *
 * При каждой un-cancel операции логгируем источник для диагностики.
 */
@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID)
public final class PlayerRevivePatch {

    private static final String PLAYERREVIVE_MODID = "playerrevive";
    private static volatile boolean enabledChecked = false;
    private static volatile boolean enabled = false;

    private PlayerRevivePatch() {}

    private static boolean isEnabled() {
        if (!enabledChecked) {
            enabledChecked = true;
            enabled = ModList.get() != null && ModList.get().isLoaded(PLAYERREVIVE_MODID);
            if (enabled) {
                LabyrinthMod.LOGGER.info("[otbor] PlayerRevive detected — finish-off patch ON (any-source)");
            }
        }
        return enabled;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!isEnabled()) return;
        if (!event.isCanceled()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        // Без проверки на attacker-Player — un-cancel'им любой урон (мобы, projectile, лава…).
        var src = event.getSource();
        var attackerEntity = src.getEntity();
        LabyrinthMod.LOGGER.info("[otbor] PR-finish: un-cancel damage on {} from src={} (entity={}) amount={}",
                victim.getGameProfile().getName(),
                src.getMsgId(),
                attackerEntity != null ? attackerEntity.getType().toString() : "null",
                event.getAmount());
        event.setCanceled(false);
    }
}
