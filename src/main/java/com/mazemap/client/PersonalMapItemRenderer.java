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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;

import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class PersonalMapItemRenderer implements IClientItemExtensions {

    // Координаты элемента "map" из модели (делённые на 16)
    private static final float MAP_MIN_X = 1.75f / 16.0f;
    private static final float MAP_MAX_X = 14.325f / 16.0f;
    private static final float MAP_TOP_Y = 0.31f / 16.0f;
    private static final float MAP_MIN_Z = 0.5f / 16.0f;
    private static final float MAP_MAX_Z = 11.5f / 16.0f;

    private static final float MAP_CENTER_X = (MAP_MIN_X + MAP_MAX_X) / 2.0f;
    private static final float MAP_CENTER_Z = (MAP_MIN_Z + MAP_MAX_Z) / 2.0f;
    private static final float MAP_SIZE_X = MAP_MAX_X - MAP_MIN_X;
    private static final float MAP_SIZE_Z = MAP_MAX_Z - MAP_MIN_Z;

    // Масштаб: 20 блоков мира = полная ширина модели карты
    private static final float WORLD_BLOCKS_VISIBLE = 20.0f;
    private static final float SCALE_X = MAP_SIZE_X / WORLD_BLOCKS_VISIBLE;
    private static final float SCALE_Z = MAP_SIZE_Z / WORLD_BLOCKS_VISIBLE;

    // Белая текстура для цветных элементов (стрелка, путь)
    private static final ResourceLocation WHITE_TEXTURE = new ResourceLocation("minecraft", "textures/particle/generic_0.png");

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
            // Рендерим базовую 3D модель предмета
            BakedModel base = ModelEventHandler.getBaseModel();
            if (base != null) {
                ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
                RenderType renderType = ItemBlockRenderTypes.getRenderType(stack, true);
                VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(bufferSource, renderType, true, stack.hasFoil());
                itemRenderer.renderModelLists(base, stack, packedLight, packedOverlay, poseStack, consumer);
            }
            // Рендерим текстуру карты и оверлеи
            renderMapOverlay(stack, poseStack, bufferSource, packedLight, packedOverlay);
        }

        private void renderMapOverlay(ItemStack stack, PoseStack poseStack,
                                      MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
            if (stack.isEmpty()) return;
            UUID mapId = PersonalMapItem.getMapUUID(stack);
            if (mapId == null) return;

            ResourceLocation texture = PersonalMapItemTexture.getTexture(mapId);

            // ВАЖНО: entityCutoutNoCull использует DefaultVertexFormat.NEW_ENTITY
            // который требует: position, color, uv0, uv1(light), uv2(overlay), normal
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));
            Matrix4f matrix = poseStack.last().pose();

            // Рисуем квад текстуры карты (4 вершины)
            vertex(consumer, matrix, MAP_MIN_X, MAP_TOP_Y, MAP_MIN_Z, 0f, 0f, packedLight, packedOverlay);
            vertex(consumer, matrix, MAP_MIN_X, MAP_TOP_Y, MAP_MAX_Z, 0f, 1f, packedLight, packedOverlay);
            vertex(consumer, matrix, MAP_MAX_X, MAP_TOP_Y, MAP_MAX_Z, 1f, 1f, packedLight, packedOverlay);
            vertex(consumer, matrix, MAP_MAX_X, MAP_TOP_Y, MAP_MIN_Z, 1f, 0f, packedLight, packedOverlay);

            // Рисуем стрелку игрока и путь поверх карты
            renderPlayerArrow(poseStack, bufferSource, packedLight, packedOverlay);
            renderPath(poseStack, bufferSource, packedLight, packedOverlay);
        }

        private void renderPlayerArrow(PoseStack poseStack, MultiBufferSource bufferSource,
                                       int packedLight, int packedOverlay) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // Используем белую текстуру + vertex color для стрелки
            VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(WHITE_TEXTURE));
            Matrix4f matrix = poseStack.last().pose();

            float yaw = mc.player.getYRot();
            float yawRad = yaw * (float) Math.PI / 180.0f;

            // Размер стрелки
            float arrowLength = 0.04f;
            float arrowWidth = 0.02f;

            // Направление стрелки
            float dx = -arrowLength * (float) Math.sin(yawRad);
            float dz = arrowLength * (float) Math.cos(yawRad);
            float perpX = arrowWidth * (float) Math.cos(yawRad);
            float perpZ = arrowWidth * (float) Math.sin(yawRad);

            float cx = MAP_CENTER_X;
            float cz = MAP_CENTER_Z;

            // Цвет стрелки (красный)
            int r = 176, g = 48, b = 32, a = 255;
            float y = MAP_TOP_Y + 0.001f; // Чуть выше карты

            // Рисуем стрелку как ромб (2 треугольника)
            float tipX = cx + dx;
            float tipZ = cz + dz;
            float baseLeftX = cx - perpX;
            float baseLeftZ = cz - perpZ;
            float baseRightX = cx + perpX;
            float baseRightZ = cz + perpZ;
            float tailX = cx - dx * 0.5f;
            float tailZ = cz - dz * 0.5f;

            // Треугольник 1: кончик -> левое основание -> задняя часть
            consumer.vertex(matrix, tipX, y, tipZ).color(r, g, b, a).uv(0.5f, 0f).uv2(packedLight).overlayCoords(packedOverlay).normal(0, 1, 0).endVertex();
            consumer.vertex(matrix, baseLeftX, y, baseLeftZ).color(r, g, b, a).uv(0f, 1f).uv2(packedLight).overlayCoords(packedOverlay).normal(0, 1, 0).endVertex();
            consumer.vertex(matrix, tailX, y, tailZ).color(r, g, b, a).uv(1f, 1f).uv2(packedLight).overlayCoords(packedOverlay).normal(0, 1, 0).endVertex();

            // Треугольник 2: кончик -> задняя часть -> правое основание
            consumer.vertex(matrix, tipX, y, tipZ).color(r, g, b, a).uv(0.5f, 0f).uv2(packedLight).overlayCoords(packedOverlay).normal(0, 1, 0).endVertex();
            consumer.vertex(matrix, tailX, y, tailZ).color(r, g, b, a).uv(1f, 1f).uv2(packedLight).overlayCoords(packedOverlay).normal(0, 1, 0).endVertex();
            consumer.vertex(matrix, baseRightX, y, baseRightZ).color(r, g, b, a).uv(1f, 0f).uv2(packedLight).overlayCoords(packedOverlay).normal(0, 1, 0).endVertex();
        }

        private void renderPath(PoseStack poseStack, MultiBufferSource bufferSource,
                                int packedLight, int packedOverlay) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            List<int[]> path = ClientHudState.getPath();
            if (path == null || path.size() < 2) return;

            VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(WHITE_TEXTURE));
            Matrix4f matrix = poseStack.last().pose();

            float playerX = (float) mc.player.getX();
            float playerZ = (float) mc.player.getZ();

            int r = 176, g = 48, b = 32, a = 255;
            float lineWidth = 0.008f;
            float y = MAP_TOP_Y + 0.002f; // Ещё чуть выше стрелки

            for (int i = 0; i < path.size() - 1; i++) {
                int[] p1 = path.get(i);
                int[] p2 = path.get(i + 1);

                float mx1 = MAP_CENTER_X + (p1[0] - playerX) * SCALE_X;
                float mz1 = MAP_CENTER_Z + (p1[1] - playerZ) * SCALE_Z;
                float mx2 = MAP_CENTER_X + (p2[0] - playerX) * SCALE_X;
                float mz2 = MAP_CENTER_Z + (p2[1] - playerZ) * SCALE_Z;

                // Проверяем границы
                if (mx1 < MAP_MIN_X || mx1 > MAP_MAX_X || mz1 < MAP_MIN_Z || mz1 > MAP_MAX_Z) continue;
                if (mx2 < MAP_MIN_X || mx2 > MAP_MAX_X || mz2 < MAP_MIN_Z || mz2 > MAP_MAX_Z) continue;

                float segDx = mx2 - mx1;
                float segDz = mz2 - mz1;
                float segLen = (float) Math.sqrt(segDx * segDx + segDz * segDz);
                if (segLen < 0.0001f) continue;

                float perpX = (-segDz / segLen) * lineWidth;
                float perpZ = (segDx / segLen) * lineWidth;

                consumer.vertex(matrix, mx1 + perpX, y, mz1 + perpZ).color(r, g, b, a).uv(0f, 0f).uv2(packedLight).overlayCoords(packedOverlay).normal(0, 1, 0).endVertex();
                consumer.vertex(matrix, mx1 - perpX, y, mz1 - perpZ).color(r, g, b, a).uv(0f, 1f).uv2(packedLight).overlayCoords(packedOverlay).normal(0, 1, 0).endVertex();
                consumer.vertex(matrix, mx2 - perpX, y, mz2 - perpZ).color(r, g, b, a).uv(1f, 1f).uv2(packedLight).overlayCoords(packedOverlay).normal(0, 1, 0).endVertex();
                consumer.vertex(matrix, mx2 + perpX, y, mz2 + perpZ).color(r, g, b, a).uv(1f, 0f).uv2(packedLight).overlayCoords(packedOverlay).normal(0, 1, 0).endVertex();
            }
        }

        private void vertex(VertexConsumer consumer, Matrix4f matrix,
                            float x, float y, float z,
                            float u, float v, int packedLight, int packedOverlay) {
            // Заполняем ВСЕ элементы вершины для DefaultVertexFormat.NEW_ENTITY:
            // position, color, uv0, uv1(light), uv2(overlay), normal
            consumer.vertex(matrix, x, y, z)
                    .color(255, 255, 255, 255)
                    .uv(u, v)
                    .uv2(packedLight)
                    .overlayCoords(packedOverlay)  // ← ЭТО БЫЛО ПРОПУЩЕНО!
                    .normal(0, 1, 0)
                    .endVertex();
        }
    }
}