package com.mazemap.client;

import com.labyrinthmod.LabyrinthMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientSetup {
    // Код подмены модели полностью удален.
    // Игра будет сама загружать personal_map.json из папки models/item/
}