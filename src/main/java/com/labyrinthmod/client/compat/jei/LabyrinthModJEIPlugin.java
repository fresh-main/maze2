package com.labyrinthmod.client.compat.jei;

import com.labyrinthmod.common.data.CraftRestrictionManager;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.client.Minecraft;

import java.util.Map;

@JeiPlugin
public class LabyrinthModJEIPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("labyrinthmod", "main");
    }

    // Этот метод вызывается при старте JEI.
    // Но так как ограничения динамические, нам нужно обновлять их позже.
    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        // Сохраняем ссылку на runtime, чтобы потом динамически скрывать/показывать
        JEIHelper.setRuntime(jeiRuntime);
    }
}