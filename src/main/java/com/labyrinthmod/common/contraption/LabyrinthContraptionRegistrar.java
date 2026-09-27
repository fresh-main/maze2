package com.labyrinthmod.common.contraption;

import com.labyrinthmod.LabyrinthMod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LabyrinthContraptionRegistrar {

    private LabyrinthContraptionRegistrar() {}

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        LabyrinthContraption.register(event);
    }
}
