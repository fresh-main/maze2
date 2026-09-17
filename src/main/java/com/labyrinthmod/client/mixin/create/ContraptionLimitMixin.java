package com.labyrinthmod.client.mixin.create;

import com.simibubi.create.content.contraptions.Contraption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Миксин для увеличения лимита блоков в конструкции Create с 2500 до 50000.
 * Позволяет собирать гигантские сегменты стен лабиринта без ошибки "Structure too large".
 *
 * В оригинальном коде проверка выглядит так:
 *   if (this.blocks.size() <= (Integer) AllConfigs.server().kinetics.maxBlocksMoved.get())
 * .get() возвращает generic-объект (Object), который приводится к Integer и затем
 * автоматически распаковывается через Integer.intValue() перед сравнением с int.
 * Перехватываем именно этот вызов intValue() — единственный в методе moveBlock —
 * и подставляем свой лимит вместо значения из конфига (по умолчанию 2500).
 *
 * remap = false обязателен: Contraption принадлежит моду Create, а не ванильному
 * Minecraft, поэтому для него нет записи в SRG/official маппингах.
 */
@Mixin(value = Contraption.class, remap = false)
public class ContraptionLimitMixin {

    private static final int LABYRINTHMOD_MAX_BLOCKS_MOVED = 50_000;

    @Redirect(
            method = "moveBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Direction;Ljava/util/Queue;Ljava/util/Set;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Integer;intValue()I"
            )
    )
    private int labyrinthmod$overrideMaxBlocksMoved(Integer original) {
        return LABYRINTHMOD_MAX_BLOCKS_MOVED;
    }
}