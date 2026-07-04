package com.infection.capability;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

public final class InfectionAttacher {

    public static final ResourceLocation KEY = LabyrinthMod.id("infection");

    private InfectionAttacher() {}

    public static void onAttachEntity(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player)) return;
        if (event.getCapabilities().containsKey(KEY)) return;
        InfectionProvider provider = new InfectionProvider();
        event.addCapability(KEY, provider);
        event.addListener(provider::invalidate);
    }

    @Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class RegisterEvents {
        @SubscribeEvent
        public static void registerCap(RegisterCapabilitiesEvent e) {
            e.register(IInfection.class);
        }
    }
}
