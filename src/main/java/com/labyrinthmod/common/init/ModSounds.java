package com.labyrinthmod.common.init;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LabyrinthMod.MOD_ID);

    // createFixedRangeEvent ставит ЖЁСТКИЙ радиус слышимости в блоках, не зависящий
    // от volume (volume теперь только громкость в пределах радиуса). Шаги — 16 блоков
    // (стандартный mob footstep), атака чуть больше для драматичности.
    public static final RegistryObject<SoundEvent> GRIVER_RUN = SOUNDS.register("griver_run",
            () -> SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "griver_run"), 16f));

    public static final RegistryObject<SoundEvent> GRIVER_WALK = SOUNDS.register("griver_walk",
            () -> SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "griver_walk"), 16f));

    public static final RegistryObject<SoundEvent> GRIVER_ATTACK = SOUNDS.register("griver_attack",
            () -> SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "griver_attack"), 24f));

    public static final RegistryObject<SoundEvent> GRIVER_JUMP = SOUNDS.register("griver_jump",
            () -> SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "griver_jump"), 16f));

    // Звук «печатной машинки» для побуквенного раскрытия экрана выдачи роли.
    // Проигрывается на клиенте (UI), радиус не важен — оставляем variableRange.
    public static final RegistryObject<SoundEvent> FRACTION_PECHAT = SOUNDS.register("fraction_pechat",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "fraction_pechat")));

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}