package com.mazemap.client.model;

import com.labyrinthmod.LabyrinthMod;
import com.mazemap.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModelEventHandler {
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ResourceLocation modelLoc = ModItems.PERSONAL_MAP.getId();
        event.getModels().put(modelLoc, new EmptyBakedModel());
    }
}