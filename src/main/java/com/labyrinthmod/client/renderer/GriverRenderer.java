package com.labyrinthmod.client.renderer;

import com.labyrinthmod.common.entity.GriverEntity;
import com.labyrinthmod.common.entity.GriverFootSolver;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import software.bernie.geckolib.cache.object.GeoBone;
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
    @Override
    protected void applyRotations(GriverEntity animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick);
        GriverFootSolver solver = animatable.footSolver;
        solver.update(animatable, partialTick);
        poseStack.translate(0.0, solver.bodyShift, 0.0);
        poseStack.mulPose(Axis.XP.rotation(solver.pitch));
        poseStack.mulPose(Axis.ZP.rotation(solver.roll));
    }

    @Override
    public void renderRecursively(PoseStack poseStack, GriverEntity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        int leg = legIndex(bone.getName());
        if (leg >= 0) {
            poseStack.pushPose();
            poseStack.translate(0.0, animatable.footSolver.legOffset[leg], 0.0);
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        if (leg >= 0) poseStack.popPose();
    }

    private static int legIndex(String name) {
        if (name.length() != 4 || !name.startsWith("leg")) return -1;
        int n = name.charAt(3) - '1';
        return n >= 0 && n < GriverFootSolver.LEGS ? n : -1;
    }
}
