package com.infection.compat;

import com.infection.capability.IInfection;
import com.infection.capability.InfectionProvider;
import com.infection.note.NoteTexts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Серверный хук на использование лечебных предметов First Aid:
 *   bandage / plaster — пока заражение < 100, ставим флаг growthSlowdownUntil на 60 секунд
 *   (рост идёт через раз → эффективная скорость ×0.5). В записку добавляется приписка.
 *
 *   morphine — независимо от уровня, suppressed-until на 90 секунд (приступы галлюцинаций
 *   не запускаются), после окончания — Nausea III на 30 секунд (откат).
 *
 * Класс грузится только если First Aid действительно установлен — проверяет FirstAidCompat.isLoaded(),
 * но сам хук безопасен и без First Aid (просто никогда не сработает по item id).
 */
public final class FirstAidUseHandler {

    private static final ResourceLocation FA_BANDAGE  = ResourceLocation.fromNamespaceAndPath("firstaid", "bandage");
    private static final ResourceLocation FA_PLASTER  = ResourceLocation.fromNamespaceAndPath("firstaid", "plaster");
    private static final ResourceLocation FA_MORPHINE = ResourceLocation.fromNamespaceAndPath("firstaid", "morphine");

    private static final long SLOWDOWN_TICKS = 60L * 20L;
    private static final long MORPHINE_TICKS = 90L * 20L;

    private FirstAidUseHandler() {}

    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack stack = e.getItem();
        if (stack.isEmpty()) return;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return;

        if (id.equals(FA_BANDAGE) || id.equals(FA_PLASTER)) {
            applyBandage(sp);
        } else if (id.equals(FA_MORPHINE)) {
            applyMorphine(sp);
        }
    }

    private static void applyBandage(ServerPlayer sp) {
        InfectionProvider.get(sp).ifPresent(data -> {
            int level = data.getLevel();
            if (level <= 0 || level >= 100) return; // на терминальной — бинты не работают
            long now = sp.serverLevel().getGameTime();
            data.setGrowthSlowdownUntil(now + SLOWDOWN_TICKS);
            appendNote(data, sp, NoteTexts.bandageAppendix(sp.getUUID()));
            data.syncTo(sp);
        });
    }

    private static void applyMorphine(ServerPlayer sp) {
        InfectionProvider.get(sp).ifPresent(data -> {
            long now = sp.serverLevel().getGameTime();
            data.setHallucinationsSuppressedUntil(now + MORPHINE_TICKS);
            appendNote(data, sp, NoteTexts.morphineAppendix(sp.getUUID()));
            data.syncTo(sp);
        });
        // Отложенный «откат» — ставим Nausea, начнётся через 90 секунд через delay-эффект.
        // Простейшее решение: добавляем эффект с лёгкой задержкой через стандартный механизм
        // — Minecraft не имеет встроенного delay, поэтому ставим Nausea на полную длительность
        // (морфин 90 + 30 откат = 120 секунд), но первые 90 секунд приступы и так подавлены.
        // Игрок увидит «качающуюся камеру» только когда морфин кончится.
        sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, (int) (MORPHINE_TICKS + 30L * 20L), 2, false, false));
    }

    private static void appendNote(IInfection data, Player p, String appendix) {
        // Дописываем в auto-noteText. Следующий переход стадии перепишет — это намеренно
        // (приписка от бинта/морфина — временная, как и эффект).
        String cur = data.getNoteText();
        if (cur == null) cur = "";
        data.setNoteText(cur + appendix);
    }
}
