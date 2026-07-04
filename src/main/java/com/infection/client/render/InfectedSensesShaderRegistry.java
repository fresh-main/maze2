package com.infection.client.render;

import com.labyrinthmod.LabyrinthMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

/**
 * Регистрирует наш кастомный шейдер «infected_silhouette» при перезагрузке ресурсов.
 *
 * Шейдер живёт в assets/infection/shaders/core/infected_silhouette.{json,vsh,fsh}.
 * В отличие от vanilla {@code rendertype_eyes} (который применяет linear fog) и
 * {@code rendertype_entity_translucent} (который перемножает на lightmap), наш шейдер
 * выводит сырой {@code texture × vertexColor × ColorModulator} — без затемнения от
 * fog/blindness и без зависимости от мирового освещения.
 */
@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class InfectedSensesShaderRegistry {

    private static ShaderInstance shader;

    private InfectedSensesShaderRegistry() {}

    public static ShaderInstance getShader() {
        return shader;
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(),
                        LabyrinthMod.id("infected_silhouette"),
                        DefaultVertexFormat.NEW_ENTITY),
                inst -> shader = inst
        );
    }
}
