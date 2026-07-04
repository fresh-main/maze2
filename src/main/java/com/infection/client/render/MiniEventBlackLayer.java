package com.infection.client.render;

import com.infection.client.minievent.ClientMiniEventState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.NotNull;

/**
 * Поверх модели админа (если он PREPARING/ACTIVE) рисуется полностью чёрный силуэт.
 * Используется текстура самого скина — но мультиплицируется на (0,0,0,1), что даёт
 * pure-black форму. Слегка увеличенный масштаб (1.01) перекрывает тонкие зазоры от armor-слоёв.
 */
public class MiniEventBlackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public MiniEventBlackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(@NotNull PoseStack pose,
                       @NotNull MultiBufferSource buffers,
                       int packedLight,
                       @NotNull AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!ClientMiniEventState.shouldRenderBlack(player.getUUID())) return;

        PlayerModel<AbstractClientPlayer> model = this.getParentModel();

        pose.pushPose();
        // Чуть-чуть больше штатной модели — чтобы прятать броню/мелкие выступы.
        pose.scale(1.01f, 1.01f, 1.01f);

        var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(player.getSkinTextureLocation()));
        // Color (0,0,0,1) — текстура отображается, но цвет перемножается на чёрный.
        model.renderToBuffer(
                pose, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                0.0f, 0.0f, 0.0f, 1.0f
        );

        pose.popPose();
    }
}
