package com.infection.item;

import com.infection.capability.InfectionProvider;
import com.infection.effect.InfectionEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class AntidoteSyringeItem extends Item {

    public AntidoteSyringeItem(Properties props) {
        super(props);
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        return 32;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * Все state-меняющие действия — только на сервере (потребление стака, эффекты, sync).
     * Клиент уже отрисовал анимацию питья через `use()`. Возвращаем тот же stack —
     * MC сам синхронизирует его размер из server inventory.
     */
    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity) {
        if (level.isClientSide) return stack;
        if (!(entity instanceof Player player)) return stack;

        InfectionProvider.get(player).ifPresent(d -> {
            d.setLevel(0);
            d.syncTo(player);
        });
        // ВАЖНО: чистим все инфекционные эффекты СРАЗУ (BLINDNESS/DARKNESS и т.д.).
        // Раньше они снимались только в onPlayerTick раз в 40 тиков, и за это время
        // у DARKNESS успевал «провисеть» factorIn-фактор, который вызывал затемнение
        // экрана даже после того как уровень обнулился. Игрок жаловался на «лютое
        // понижение гаммы после первого же антидота» — это и есть остаточный
        // фактор DARKNESS. Принудительный clearAll чистит всё мгновенно.
        InfectionEffects.clearAll(player);

        // Even after clearAll, у MobEffects.DARKNESS остаётся factorData с
        // непогашенным interpolated value: vanilla Camera/LightTexture продолжают
        // рисовать остаточное затемнение ~22 тика (vanilla quirk без публичного
        // API сброса). Игрок видит это как «после укола свет ВНЕЗАПНО упал».
        //
        // Перекрываем коротким NIGHT_VISION (5 сек): яркость моментально
        // подскакивает поверх любого residue, последние 10 тиков NV сами
        // фадят плавно к норме. showIcon=false, чтобы не маячил HUD-значок.
        player.addEffect(new MobEffectInstance(
                MobEffects.NIGHT_VISION,
                100,    // 5 сек (vanilla фадит за последние 10 тиков автоматически)
                0,
                true,   // ambient
                false,  // showParticles
                false   // showIcon
        ));

        // CONFUSION (NAUSEA) — убран намеренно: его волнообразное искажение игроки
        // воспринимали как «гамма поплыла» и оставалось ощущение что испорчена картинка.
        // Звук укола — достаточный feedback от шприца.
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.8f, 1.6f);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }
}
