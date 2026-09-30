package com.labyrinthmod.client.renderer;

import com.labyrinthmod.common.entity.GriverEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GriverRenderer extends GeoEntityRenderer<GriverEntity> {

    public GriverRenderer(EntityRendererProvider.Context context) {
        super(context, new GriverGeoModel());
        this.shadowRadius = 0.5F;
    }

    @Override
    protected float getDeathMaxRotation(GriverEntity entity) {
        return 0.0F;
    }

    @Override
    public boolean shouldShowName(GriverEntity entity) {
        return false;
    }

    @Override
    public void render(GriverEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getGroundAnimationOffset(partialTick), 0.0D);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }
}
