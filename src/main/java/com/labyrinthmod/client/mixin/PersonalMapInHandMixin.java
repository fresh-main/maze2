package com.labyrinthmod.client.mixin;

import com.mazemap.client.PersonalMapItemTexture;
import com.mazemap.item.PersonalMapItem;
import com.mazemap.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class PersonalMapInHandMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void mazemap$renderTwoHanded(AbstractClientPlayer player, float partialTicks, float pitch,
                                         InteractionHand hand, float swingProgress, ItemStack stack,
                                         float equipProgress, PoseStack pose, MultiBufferSource buffer,
                                         int light, CallbackInfo ci) {
        if (!stack.is(ModItems.PERSONAL_MAP.get())) return;
        if (hand != InteractionHand.MAIN_HAND) return;      // отрисуем один раз
        if (player.isScoping()) return;

        float f  = Mth.sqrt(swingProgress);
        float f1 = -0.2F * Mth.sin(swingProgress * (float) Math.PI);
        float f2 = -0.4F * Mth.sin(f * (float) Math.PI);
        pose.translate(0.0F, -f1 / 2.0F, f2);
        float tilt = calculateMapTilt(pitch);
        pose.translate(0.0F, 0.04F + equipProgress * -1.2F + tilt * -0.5F, -0.72F);
        pose.mulPose(Axis.XP.rotationDegrees(tilt * -85.0F));

        if (!player.isInvisible()) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            renderMapHand(pose, buffer, light, HumanoidArm.RIGHT);
            renderMapHand(pose, buffer, light, HumanoidArm.LEFT);
            pose.popPose();
        }

        float f4 = Mth.sin(f * (float) Math.PI);
        pose.mulPose(Axis.XP.rotationDegrees(f4 * 20.0F));
        pose.scale(2.0F, 2.0F, 2.0F);
        renderMapQuad(stack, pose, buffer, light);

        ci.cancel();
    }

    private float calculateMapTilt(float pitch) {
        float f = 1.0F - pitch / 45.0F + 0.1F;
        f = Mth.clamp(f, 0.0F, 1.0F);
        return -Mth.cos(f * (float) Math.PI) * 0.5F + 0.5F;
    }

    private void renderMapHand(PoseStack pose, MultiBufferSource buffer, int light, HumanoidArm arm) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof PlayerRenderer pr)) return;

        pose.pushPose();
        float f = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        pose.mulPose(Axis.YP.rotationDegrees(92.0F));
        pose.mulPose(Axis.XP.rotationDegrees(45.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(f * -41.0F));
        pose.translate(f * 0.3F, -1.1F, 0.45F);

        if (arm == HumanoidArm.RIGHT) {
            pr.renderRightHand(pose, buffer, light, mc.player);
        } else {
            pr.renderLeftHand(pose, buffer, light, mc.player);
        }
        pose.popPose();
    }

    private void renderMapQuad(ItemStack stack, PoseStack pose, MultiBufferSource buffer, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.scale(0.38F, 0.38F, 0.38F);
        pose.translate(-0.5F, -0.5F, 0.0F);
        pose.scale(0.0078125F, 0.0078125F, 0.0078125F);

        ResourceLocation tex = PersonalMapItemTexture.getTexture(PersonalMapItem.getMapUUID(stack));
        VertexConsumer vc = buffer.getBuffer(RenderType.text(tex));
        Matrix4f m = pose.last().pose();
        vc.vertex(m, -64,  64, 0).color(255,255,255,255).uv(0, 0).uv2(light).endVertex();
        vc.vertex(m,  64,  64, 0).color(255,255,255,255).uv(1, 0).uv2(light).endVertex();
        vc.vertex(m,  64, -64, 0).color(255,255,255,255).uv(1, 1).uv2(light).endVertex();
        vc.vertex(m, -64, -64, 0).color(255,255,255,255).uv(0, 1).uv2(light).endVertex();
        pose.popPose();
    }
}