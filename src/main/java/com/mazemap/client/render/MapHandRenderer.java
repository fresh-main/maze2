package com.mazemap.client.render;

import com.mazemap.client.ClientMazeMapHandlers;
import com.mazemap.registry.ModItems;
import com.mazemap.storage.PlayerMapData;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class MapHandRenderer {
    private static HandMapInstance mapInstance;

    public static void forceMapTextureUpload() {
        if (mapInstance != null) mapInstance.forceUpload();
    }

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.PERSONAL_MAP.get())) return;

        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        int light = event.getPackedLight();

        boolean left = event.getHand() == InteractionHand.OFF_HAND;
        ItemDisplayContext ctx = left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        pose.pushPose();

        // Берём модель самого предмета, чтобы трансформации совпали с ванильной 2D-бумагой
        BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, light);
        model.getTransforms().getTransform(ctx).apply(left, pose);

        renderCustomMapTexture(pose, buffer, light);
        pose.popPose();
    }

    private void renderCustomMapTexture(PoseStack pose, MultiBufferSource buffer, int light) {
        if (mapInstance == null) mapInstance = new HandMapInstance();
        mapInstance.draw(pose, buffer, light);
    }

    @OnlyIn(Dist.CLIENT)
    private static class HandMapInstance implements AutoCloseable {
        private final DynamicTexture texture;
        private final RenderType renderType;
        private boolean needsUpload = true;
        private long lastKey = Long.MIN_VALUE;

        public HandMapInstance() {
            texture = new DynamicTexture(128, 128, true);
            ResourceLocation loc = Minecraft.getInstance().textureManager.register("mazemap/hand_map", texture);
            // textSeeThrough уже содержит NO_DEPTH_TEST — карта будет поверх всего
            renderType = RenderType.textSeeThrough(loc);
        }

        public void forceUpload() { needsUpload = true; }

        private void update() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            int cx = (int) Math.floor(mc.player.getX() / PlayerMapData.FRAGMENT_SIZE_BLOCKS);
            int cz = (int) Math.floor(mc.player.getZ() / PlayerMapData.FRAGMENT_SIZE_BLOCKS);
            long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);

            PlayerMapData.Fragment frag = ClientMazeMapHandlers.getFragments().get(key);
            NativeImage pixels = texture.getPixels();
            if (pixels == null) return;

            // 1. Заполняем внутреннюю область (координаты 9..118, всего 110x110)
            int innerSize = 110;
            int offset = 9; // Отступ рамки

            if (frag == null) {
                // Неисследовано
                for (int y = 0; y < innerSize; y++) {
                    for (int x = 0; x < innerSize; x++) {
                        pixels.setPixelRGBA(x + offset, y + offset, 0xFF404040);
                    }
                }
            } else {
                // Безопасное масштабирование 64x64 -> 110x110
                byte[] data = frag.pixels;
                for (int y = 0; y < innerSize; y++) {
                    for (int x = 0; x < innerSize; x++) {
                        // Обратное преобразование координат для выборки из массива 64x64
                        int px = (x * 64) / innerSize;
                        int py = (y * 64) / innerSize;

                        // Защита от выхода за пределы массива (на всякий случай)
                        px = Math.max(0, Math.min(63, px));
                        py = Math.max(0, Math.min(63, py));

                        byte p = data[py * 64 + px];
                        int color = switch (p) {
                            case 0 -> 0xFF404040;
                            case 1 -> 0xFFE0E0E0;
                            default -> MapColor.getColorFromPackedId(Math.max(0, Math.min(63, (p & 0xFF) - 2)));
                        };

                        pixels.setPixelRGBA(x + offset, y + offset, color);
                    }
                }
            }

            // 2. 【ИСПРАВЛЕНИЕ ВЫЛЕТА】 Безопасная отрисовка рамки
            int borderColor = 0xFF8B5A2B;
            int innerBorderColor = 0xFFD9B77B;

            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    // Внешняя рамка (0-7 и 120-127)
                    if (x < 8 || x >= 120 || y < 8 || y >= 120) {
                        pixels.setPixelRGBA(x, y, borderColor);
                    }
                    // Внутренняя светлая граница (8 и 119)
                    else if (x == 8 || x == 119 || y == 8 || y == 119) {
                        pixels.setPixelRGBA(x, y, innerBorderColor);
                    }
                }
            }

            texture.upload();
            needsUpload = false;
        }

        public void draw(PoseStack pose, MultiBufferSource buffer, int light) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                int cx = (int) Math.floor(mc.player.getX() / PlayerMapData.FRAGMENT_SIZE_BLOCKS);
                int cz = (int) Math.floor(mc.player.getZ() / PlayerMapData.FRAGMENT_SIZE_BLOCKS);
                long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                if (key != lastKey) { needsUpload = true; lastKey = key; }
            }
            if (needsUpload) update();

            Matrix4f mat = pose.last().pose();
            VertexConsumer vc = buffer.getBuffer(renderType);

            // Координаты квада — точно как в ванильной карте
            float min = -0.42F, max = 0.42F, z = 0.001F;
            vc.vertex(mat, min, max, z).color(255, 255, 255, 255).uv(0, 1).uv2(light).endVertex();
            vc.vertex(mat, max, max, z).color(255, 255, 255, 255).uv(1, 1).uv2(light).endVertex();
            vc.vertex(mat, max, min, z).color(255, 255, 255, 255).uv(1, 0).uv2(light).endVertex();
            vc.vertex(mat, min, min, z).color(255, 255, 255, 255).uv(0, 0).uv2(light).endVertex();
        }

        @Override public void close() { texture.close(); }
    }
}