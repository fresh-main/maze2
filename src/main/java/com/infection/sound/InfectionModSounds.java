package com.infection.sound;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class InfectionModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LabyrinthMod.MOD_ID);

    public static final RegistryObject<SoundEvent> INFESTATION_AMBIENCE =
            SOUNDS.register("infestation_ambience",
                    () -> SoundEvent.createVariableRangeEvent(LabyrinthMod.id("infestation_ambience")));

    /** One-shot удар сердца — играется во время приступа галлюцинации. */
    public static final RegistryObject<SoundEvent> HEARTBEAT =
            SOUNDS.register("heartbeat",
                    () -> SoundEvent.createVariableRangeEvent(LabyrinthMod.id("heartbeat")));

    /** Jumpscare-инвентик: один длинный звук с затуханием, длительность ~4 сек. */
    public static final RegistryObject<SoundEvent> JUMPSCARE =
            SOUNDS.register("jumpscare",
                    () -> SoundEvent.createVariableRangeEvent(LabyrinthMod.id("jumpscare")));

    private InfectionModSounds() {}
}
