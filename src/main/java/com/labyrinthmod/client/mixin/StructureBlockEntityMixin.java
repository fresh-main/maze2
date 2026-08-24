package com.labyrinthmod.client.mixin;
// Примечание: StructureBlockEntity относится к логике сервера/общего кода.
// Если у вас есть пакет common.generation или similar, лучше перенести этот класс туда,
// но он будет работать и здесь, если правильно зарегистрирован.

import net.minecraft.world.level.block.entity.StructureBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(StructureBlockEntity.class)
public class StructureBlockEntityMixin {

    /**
     * В Minecraft 1.20.1 (Mojang mappings) метод, который читает размер из NBT
     * и ограничивает его через Mth.clamp(..., 1, 48), называется "load".
     * Эта аннотация найдет все три числа "48" (для X, Y и Z) и заменит их на 256.
     */
    @ModifyConstant(method = "load", constant = @Constant(intValue = 48))
    private int modifyStructureSizeLimit(int original) {
        // Вы можете поставить здесь 10000, если вам действительно нужен такой огромный размер,
        // но 256 является безопасным стандартом, который не вызовет проблем с памятью при загрузке.
        return 256;
    }
}