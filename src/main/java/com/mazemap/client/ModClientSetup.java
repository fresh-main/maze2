package com.mazemap.client;

import com.labyrinthmod.LabyrinthMod;
import com.mazemap.registry.ModItems;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientSetup {
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ResourceLocation personalMapLoc = ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "personal_map");
        ResourceLocation filledMapLoc = ResourceLocation.fromNamespaceAndPath("minecraft", "item/filled_map");

        BakedModel filledMapModel = event.getModels().get(filledMapLoc);
        if (filledMapModel != null) {
            // Подменяем модель нашего предмета на ванильную модель заполненной карты
            event.getModels().put(personalMapLoc, filledMapModel);
            LabyrinthMod.LOGGER.info("[MazeMap] Replaced PersonalMap model with vanilla FilledMap model.");
        } else {
            LabyrinthMod.LOGGER.warn("[MazeMap] Could not find vanilla FilledMap model to copy.");
        }
    }
}