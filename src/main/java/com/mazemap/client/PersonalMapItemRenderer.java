package com.mazemap.client;

import com.mazemap.client.model.ModelEventHandler;
import com.mazemap.item.PersonalMapItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class PersonalMapItemRenderer implements IClientItemExtensions {

    private static final float MAP_MIN_X = 1.75f / 16.0f;
    private static final float MAP_MAX_X = 14.325f / 16.0f;
    private static final float MAP_TOP_Y = 0.31f / 16.0f;
    private static final float MAP_MIN_Z = 0.5f / 16.0f;
    private static final float MAP_MAX_Z = 11.5f / 16.0f;
    private static final float MAP_CENTER_Z = (MAP_MIN_Z + MAP_MAX_Z) / 2.0f;
    private static final float MAP_SIZE_X = MAP_MAX_X - MAP_MIN_X;
    private static final float MAP_SIZE_Z = MAP_MAX_Z - MAP_MIN_Z;

    private static final float SHOWN = PersonalMapItemTexture.SHOWN_BLOCKS;
    private static final float UNITS_PER_BLOCK = MAP_SIZE_X / SHOWN;
    private static final float VISIBLE_Z = MAP_SIZE_Z / UNITS_PER_BLOCK;
    private static final float V0 = (1.0f - VISIBLE_Z / SHOWN) / 2.0f;
    private static final float V1 = 1.0f - V0;

    private static final int TEXELS_PER_BLOCK = 2;
    private static final int TEX = PersonalMapItemTexture.SHOWN_BLOCKS * TEXELS_PER_BLOCK;
    private static final float TEXEL = MAP_SIZE_X / TEX;
    private static final float EPS = TEXEL * 0.05f;

    private static final float PATH_Y = MAP_TOP_Y + 0.001f;
    private static final float MARKER_Y = MAP_TOP_Y + 0.002f;
    private static final float ARROW_Y = MAP_TOP_Y + 0.003f;
    private static final int PATH_SIZE = 1;

    private static final String[] MARKER = {
            "..KKK..",
            "..KbK..",
            "KKKbKKK",
            "KbbbbbK",
            "KKKbKKK",
            "..KbK..",
            "..KKK.."
    };

    private static final String[] ARROW_UP = {
            "....k....",
            "...krk...",
            "..krrrk..",
            ".krrrrrk.",
            "kkkkrkkkk",
            "...krk...",
            "...krk...",
            "...krk...",
            "...kkk..."
    };

    private static final String[][] ARROWS = buildArrows();

    private static String[][] buildArrows() {
        String[][] out = new String[4][];
        out[0] = ARROW_UP;
        for (int i = 1; i < 4; i++) out[i] = rotate(out[i - 1]);
        return out;
    }

    private static String[] rotate(String[] bmp) {
        int n = bmp.length;
        String[] out = new String[n];
        for (int r = 0; r < n; r++) {
            StringBuilder sb = new StringBuilder(n);
            for (int c = 0; c < n; c++) sb.append(bmp[n - 1 - c].charAt(r));
            out[r] = sb.toString();
        }
        return out;
    }

    private final BlockEntityWithoutLevelRenderer renderer = new MapItemBEWLR();

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return renderer;
    }

    private static class MapItemBEWLR extends BlockEntityWithoutLevelRenderer {
        public MapItemBEWLR() {
            super(null, null);
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext displayContext,
                                 PoseStack poseStack, MultiBufferSource bufferSource,
                                 int packedLight, int packedOverlay) {
            if (displayContext == ItemDisplayContext.GUI) {
                BakedModel gui = ModelEventHandler.getGuiModel();
                if (gui != null) {
                    renderModel(gui, stack, poseStack, bufferSource, packedLight, packedOverlay);
                    return;
                }
            }
            BakedModel base = ModelEventHandler.getBaseModel();
            if (base != null) {
                renderModel(base, stack, poseStack, bufferSource, packedLight, packedOverlay);
            }
            renderMapOverlay(stack, poseStack, bufferSource, packedLight, packedOverlay);
        }

        private static void renderModel(BakedModel model, ItemStack stack, PoseStack poseStack,
                                        MultiBufferSource bufferSource, int light, int overlay) {
            ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
            RenderType renderType = ItemBlockRenderTypes.getRenderType(stack, true);
            VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(bufferSource, renderType, true, stack.hasFoil());
            itemRenderer.renderModelLists(model, stack, light, overlay, poseStack, consumer);
        }

        private void renderMapOverlay(ItemStack stack, PoseStack poseStack,
                                      MultiBufferSource bufferSource, int light, int overlay) {
            if (stack.isEmpty()) return;
            UUID mapId = PersonalMapItem.getMapUUID(stack);
            if (mapId == null) return;

            PersonalMapItemTexture.View view = PersonalMapItemTexture.getView(mapId);
            PoseStack.Pose pose = poseStack.last();

            VertexConsumer mapConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(view.texture));
            vertex(mapConsumer, pose, MAP_MIN_X, MAP_TOP_Y, MAP_MIN_Z, 0f, V0, 255, 255, 255, light, overlay);
            vertex(mapConsumer, pose, MAP_MIN_X, MAP_TOP_Y, MAP_MAX_Z, 0f, V1, 255, 255, 255, light, overlay);
            vertex(mapConsumer, pose, MAP_MAX_X, MAP_TOP_Y, MAP_MAX_Z, 1f, V1, 255, 255, 255, light, overlay);
            vertex(mapConsumer, pose, MAP_MAX_X, MAP_TOP_Y, MAP_MIN_Z, 1f, V0, 255, 255, 255, light, overlay);

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            float partial = mc.getFrameTime();
            double px = Mth.lerp(partial, mc.player.xo, mc.player.getX());
            double pz = Mth.lerp(partial, mc.player.zo, mc.player.getZ());

            VertexConsumer solid = bufferSource.getBuffer(RenderType.entityCutoutNoCull(PersonalMapItemTexture.getWhite()));
            renderPath(solid, pose, view, px, pz, light, overlay);
            renderMarker(solid, pose, view, light, overlay);
            renderArrow(solid, pose, view, px, pz, mc.player.getYRot(), light, overlay);
        }

        private void renderPath(VertexConsumer c, PoseStack.Pose pose, PersonalMapItemTexture.View view,
                                double px, double pz, int light, int overlay) {
            List<int[]> path = ClientHudState.getPath();
            if (path == null || path.isEmpty()) return;

            int start = 0;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < path.size(); i++) {
                int[] p = path.get(i);
                double dx = p[0] + 0.5 - px;
                double dz = p[1] + 0.5 - pz;
                double d = dx * dx + dz * dz;
                if (d < best) {
                    best = d;
                    start = i;
                }
            }

            int prevX = (int) Math.floor((px - view.worldLeft) * TEXELS_PER_BLOCK);
            int prevZ = (int) Math.floor((pz - view.worldTop) * TEXELS_PER_BLOCK);
            for (int i = start; i < path.size(); i++) {
                int[] p = path.get(i);
                int tx = (p[0] - view.worldLeft) * TEXELS_PER_BLOCK;
                int tz = (p[1] - view.worldTop) * TEXELS_PER_BLOCK;
                line(c, pose, prevX, prevZ, tx, tz, light, overlay);
                prevX = tx;
                prevZ = tz;
            }
        }

        private void line(VertexConsumer c, PoseStack.Pose pose, int x0, int z0, int x1, int z1,
                          int light, int overlay) {
            int lo = -2;
            int hi = TEX + 2;
            if ((x0 < lo && x1 < lo) || (x0 > hi && x1 > hi) || (z0 < lo && z1 < lo) || (z0 > hi && z1 > hi)) return;

            int dx = Math.abs(x1 - x0);
            int dz = -Math.abs(z1 - z0);
            int sx = x0 < x1 ? 1 : -1;
            int sz = z0 < z1 ? 1 : -1;
            int err = dx + dz;
            while (true) {
                texel(c, pose, x0, z0, PATH_SIZE, PATH_SIZE, PATH_Y, 176, 48, 32, light, overlay);
                if (x0 == x1 && z0 == z1) break;
                int e2 = 2 * err;
                if (e2 >= dz) {
                    err += dz;
                    x0 += sx;
                }
                if (e2 <= dx) {
                    err += dx;
                    z0 += sz;
                }
            }
        }

        private void renderMarker(VertexConsumer c, PoseStack.Pose pose, PersonalMapItemTexture.View view,
                                  int light, int overlay) {
            if (!ClientHudState.hasMarker()) return;
            int tx = (ClientHudState.getMarkerX() - view.worldLeft) * TEXELS_PER_BLOCK;
            int tz = (ClientHudState.getMarkerZ() - view.worldTop) * TEXELS_PER_BLOCK;
            sprite(c, pose, MARKER, tx, tz, MARKER_Y, light, overlay);
        }

        private void renderArrow(VertexConsumer c, PoseStack.Pose pose, PersonalMapItemTexture.View view,
                                 double px, double pz, float yaw, int light, int overlay) {
            int tx = (int) Math.floor((px - view.worldLeft) * TEXELS_PER_BLOCK);
            int tz = (int) Math.floor((pz - view.worldTop) * TEXELS_PER_BLOCK);
            float rad = yaw * Mth.DEG_TO_RAD;
            float fx = -Mth.sin(rad);
            float fz = Mth.cos(rad);
            float deg = (float) Math.toDegrees(Math.atan2(fx, -fz));
            int dir = Math.floorMod(Math.round(deg / 90f), 4);
            sprite(c, pose, ARROWS[dir], tx, tz, ARROW_Y, light, overlay);
        }

        private void sprite(VertexConsumer c, PoseStack.Pose pose, String[] bmp, int centerTx, int centerTz,
                            float y, int light, int overlay) {
            int half = bmp.length / 2;
            for (int row = 0; row < bmp.length; row++) {
                String line = bmp[row];
                for (int col = 0; col < line.length(); col++) {
                    int color = spriteColor(line.charAt(col));
                    if (color == 0) continue;
                    texel(c, pose, centerTx - half + col, centerTz - half + row, 1, 1, y,
                            (color >> 16) & 255, (color >> 8) & 255, color & 255, light, overlay);
                }
            }
        }

        private static int spriteColor(char ch) {
            return switch (ch) {
                case 'k' -> 0x2A1810;
                case 'r' -> 0xB03020;
                case 'K' -> 0x142848;
                case 'b' -> 0x2670D0;
                default -> 0;
            };
        }

        private static void texel(VertexConsumer c, PoseStack.Pose pose, int tx, int tz, int w, int h,
                                  float y, int r, int g, int b, int light, int overlay) {
            if (tx < 0 || tx + w > TEX) return;
            float x0 = MAP_MIN_X + tx * TEXEL;
            float x1 = x0 + w * TEXEL;
            float z0 = MAP_CENTER_Z + (tz - TEX / 2) * TEXEL;
            float z1 = z0 + h * TEXEL;
            if (z0 < MAP_MIN_Z - EPS || z1 > MAP_MAX_Z + EPS) return;
            vertex(c, pose, x0, y, z0, 0.5f, 0.5f, r, g, b, light, overlay);
            vertex(c, pose, x0, y, z1, 0.5f, 0.5f, r, g, b, light, overlay);
            vertex(c, pose, x1, y, z1, 0.5f, 0.5f, r, g, b, light, overlay);
            vertex(c, pose, x1, y, z0, 0.5f, 0.5f, r, g, b, light, overlay);
        }

        private static void vertex(VertexConsumer c, PoseStack.Pose pose,
                                   float x, float y, float z, float u, float v,
                                   int r, int g, int b, int light, int overlay) {
            c.vertex(pose.pose(), x, y, z)
                    .color(r, g, b, 255)
                    .uv(u, v)
                    .overlayCoords(overlay)
                    .uv2(light)
                    .normal(pose.normal(), 0f, 1f, 0f)
                    .endVertex();
        }
    }
}