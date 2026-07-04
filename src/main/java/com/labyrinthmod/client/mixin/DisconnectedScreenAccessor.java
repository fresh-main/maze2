package com.labyrinthmod.client.mixin;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Достаёт приватные поля {@link DisconnectedScreen}, чтобы мы могли пересобрать их
 * в наш {@link com.otbor.client.OtborDisconnectedScreen}.
 */
@Mixin(DisconnectedScreen.class)
public interface DisconnectedScreenAccessor {

    @Accessor("reason")     Component otbor$getReason();
    @Accessor("parent")     Screen    otbor$getParent();
    @Accessor("buttonText") Component otbor$getButtonText();
}
