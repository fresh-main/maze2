package com.labyrinthmod.common.event;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.entity.GriverEntity;
import com.labyrinthmod.common.entity.GriverEntityType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEntityEvents {

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(GriverEntityType.GRIVER.get(), GriverEntity.createAttributes().build());
    }
}