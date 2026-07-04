package com.labyrinthmod.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {

    @Accessor("imageWidth")
    int otbor$getImageWidth();

    @Accessor("imageHeight")
    int otbor$getImageHeight();

    @Accessor("imageHeight")
    void otbor$setImageHeight(int v);

    @Accessor("topPos")
    void otbor$setTopPos(int v);
}
