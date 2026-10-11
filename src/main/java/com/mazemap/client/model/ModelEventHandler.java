package com.mazemap.client.model;

import com.labyrinthmod.LabyrinthMod;
import com.mazemap.registry.ModItems;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModelEventHandler {

    private static volatile BakedModel baseModel;

    public static BakedModel getBaseModel() {
        return baseModel;
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ModelResourceLocation key = new ModelResourceLocation(ModItems.PERSONAL_MAP.getId(), "inventory");
        BakedModel original = event.getModels().get(key);
        if (original == null) {
            LabyrinthMod.LOGGER.warn("[MazeMap] baked model {} not found, using default rendering", key);
            return;
        }
        baseModel = original;
        event.getModels().put(key, new EmptyBakedModel(original));
    }
}