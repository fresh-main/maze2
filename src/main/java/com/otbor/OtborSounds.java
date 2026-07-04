package com.otbor;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class OtborSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, LabyrinthMod.MOD_ID);

    public static final RegistryObject<SoundEvent> PAGE_FLIP =
            SOUNDS.register("page_flip",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "page_flip")));

    public static final RegistryObject<SoundEvent> PENCIL =
            SOUNDS.register("pencil",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "pencil")));

    /** Музыкальная тема финальных титров — стримится из credits_theme.ogg. */
    public static final RegistryObject<SoundEvent> CREDITS_THEME =
            SOUNDS.register("credits_theme",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "credits_theme")));

    private OtborSounds() {}

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
