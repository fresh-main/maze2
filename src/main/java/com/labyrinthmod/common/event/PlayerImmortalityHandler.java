package com.labyrinthmod.common.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class PlayerImmortalityHandler {

    private static boolean isSavingPlayer = false;

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (isSavingPlayer) return;

        LivingEntity entity = event.getEntity();

        if (entity instanceof Player player && !player.isCreative()) {
            // Если игрок НЕ в белом списке — может умереть как обычно
            if (!ImmortalityWhitelist.isWhitelisted(player)) return;

            float damage = event.getAmount();
            float currentHealth = player.getHealth();
            boolean isFireDamage = isFireDamage(event.getSource());

            // Если урон смертельный
            if (currentHealth <= damage) {
                event.setAmount(0);
                event.setCanceled(true);

                isSavingPlayer = true;
                player.setHealth(1.0f);
                player.invulnerableTime = 20;
                player.hurtTime = 0;
                isSavingPlayer = false;

                if (!isFireDamage) {
                    // РЕГЕНЕРАЦИЯ: 2 секунды (40 тиков), уровень 3 (amplifier 2)
                    player.addEffect(new MobEffectInstance(
                            MobEffects.REGENERATION, 40, 2, false, false, true
                    ));
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§c§lВЫ НЕ МОЖЕТЕ УМЕРЕТЬ! §r§aРегенерация активирована"
                    ));
                } else {
                    // Костёр/огонь: не умирает, но БЕЗ регенерации
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§6Урон от огня заблокирован, но регенерация не даётся"
                    ));
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity instanceof Player player && !player.isCreative()) {
            if (!ImmortalityWhitelist.isWhitelisted(player)) return;

            event.setCanceled(true);
            player.setHealth(1.0f);
            player.invulnerableTime = 40;

            // РЕГЕНЕРАЦИЯ: 2 секунды, уровень 3
            player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, 40, 2, false, false, true
            ));

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§c§lВЫ НЕ МОЖЕТЕ УМЕРЕТЬ! §r§aРегенерация активирована"
            ));
        }
    }

    // Проверяем урон от огня/костра (работает в любой версии)
    private static boolean isFireDamage(DamageSource source) {
        String msgId = source.getMsgId();
        return msgId.equals("inFire") || msgId.equals("onFire") ||
                msgId.equals("lava") || msgId.equals("hotFloor") ||
                msgId.equals("campfire");
    }
}