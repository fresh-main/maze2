package com.infection.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Сопоставление стадии заражения и ванильных MobEffect. Эффекты применяются
 * каждые 40 тиков (см. InfectionMod.onPlayerTick).
 *
 * APPLY_DURATION = 600 тиков (30 секунд): большой запас на лаги/паузу
 * И — что важнее — гарантирует что у MobEffects.DARKNESS остаток ВСЕГДА
 * выше порога visual fade-out (около 22 тиков). При duration=100 у DARKNESS
 * экран успевал «моргнуть» от затемнения к норме за каждые 40 тиков перевыдачи —
 * пользователь видел, как «тьма пропадает на короткий промежуток».
 *
 * Все эффекты:
 *   - ambient=true    (приглушённые частицы)
 *   - showParticles=false
 *   - showIcon=false  (не показываются в HUD)
 * То есть игрок чувствует симптомы (медленно идёт, мутит, поздно — слепнет),
 * но явного индикатора «ты заражён на X%» у него нет.
 *
 * Таблица (только «косметические»/не-раздражающие эффекты — без тошноты, без HP-урона):
 *   10% → Mining Fatigue I
 *   20% → Slowness I
 *   30% → Hunger I
 *   40% → Weakness I
 *   50% → —              (визуал несут прожилки + галлюцинации)
 *   60% → Slowness II    (перезаписывает I)
 *   70% → Blindness      (зрение пропадает — компенсируется «слухом» через
 *                          {@link com.infection.mixin.client.EntityGlowingMixin})
 *   80% → Weakness II    (перезаписывает I)
 *   90% → Blindness      (продолжается — на этой стадии уже постоянно)
 *  100% → Blindness + Darkness  (почти полная слепота)
 */
public final class InfectionEffects {

    private static final int APPLY_DURATION = 600;

    /** Все эффекты, которыми мы помечаем заражённого. Используется для clearAll. */
    private static final MobEffect[] MANAGED = {
            MobEffects.DIG_SLOWDOWN,
            MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.HUNGER,
            MobEffects.WEAKNESS,
            MobEffects.BLINDNESS,
            MobEffects.DARKNESS
    };

    private InfectionEffects() {}

    public static void applyForLevel(ServerPlayer p, int level) {
        // Эффекты, которые НЕ должны быть на этом уровне — снять, иначе они
        // продолжат тикать с прошлой длительности (30 секунд для APPLY_DURATION=600).
        // Особенно критично для DARKNESS: после падения со 100% экран должен
        // моментально вернуться к норме, а не висеть тёмным полминуты.
        if (level < 10)  p.removeEffect(MobEffects.DIG_SLOWDOWN);
        if (level < 20)  p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (level < 30)  p.removeEffect(MobEffects.HUNGER);
        if (level < 40)  p.removeEffect(MobEffects.WEAKNESS);
        if (level < 70)  p.removeEffect(MobEffects.BLINDNESS);
        if (level < 100) p.removeEffect(MobEffects.DARKNESS);

        if (level >= 10)  add(p, MobEffects.DIG_SLOWDOWN, 0);
        if (level >= 20)  add(p, MobEffects.MOVEMENT_SLOWDOWN, 0);
        if (level >= 30)  add(p, MobEffects.HUNGER, 0);
        if (level >= 40)  add(p, MobEffects.WEAKNESS, 0);
        // 50% — без дополнительного ванильного эффекта (прожилки + галлюцинации).
        if (level >= 60)  add(p, MobEffects.MOVEMENT_SLOWDOWN, 1);
        // 70% — глаза «отключаются», но обостряется «слух»: see EntityGlowingMixin.
        if (level >= 70)  add(p, MobEffects.BLINDNESS, 0);
        if (level >= 80)  add(p, MobEffects.WEAKNESS, 1);
        if (level >= 90)  add(p, MobEffects.BLINDNESS, 0);
        if (level >= 100) add(p, MobEffects.DARKNESS, 0);
    }

    private static void add(ServerPlayer p, MobEffect effect, int amplifier) {
        p.addEffect(new MobEffectInstance(
                effect,
                APPLY_DURATION,
                amplifier,
                true,   // ambient
                false,  // showParticles
                false   // showIcon
        ));
    }

    /** Снимает все эффекты, которые мы могли навесить на заражённого. */
    public static void clearAll(LivingEntity entity) {
        for (MobEffect e : MANAGED) {
            entity.removeEffect(e);
        }
    }
}
