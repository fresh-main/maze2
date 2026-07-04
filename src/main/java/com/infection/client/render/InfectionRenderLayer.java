package com.infection.client.render;

import com.infection.capability.InfectionStage;
import com.infection.client.ClientInfectionCache;
import com.labyrinthmod.LabyrinthMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Наложение «вирусных прожилок» поверх модели игрока.
 *
 * Сначала пытается использовать PNG-оверлей, нарисованный художником под стадию
 * (stage_2..stage_7). Если ресурс отсутствует — fallback на процедурную текстуру
 * (InfectionTextureCache) с уменьшенной непрозрачностью.
 *
 * Дополнительно — лёгкий color-tint на CRITICAL+ для ощущения «трупности» поверх любого скина.
 */
public class InfectionRenderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final Map<InfectionStage, ResourceLocation> STAGE_TEX = new EnumMap<>(InfectionStage.class);

    /** Кэш проверок существования PNG-оверлея. Сбрасывается на reload ресурсов. */
    private static final Map<ResourceLocation, Boolean> EXISTS_CACHE = new HashMap<>();

    static {
        // нумерация в файле — по ordinal стадии (CLEAN=0 не используем)
        STAGE_TEX.put(InfectionStage.TRACE,    LabyrinthMod.id("textures/skin_overlay/stage_2.png"));
        STAGE_TEX.put(InfectionStage.EARLY,    LabyrinthMod.id("textures/skin_overlay/stage_3.png"));
        STAGE_TEX.put(InfectionStage.ACTIVE,   LabyrinthMod.id("textures/skin_overlay/stage_4.png"));
        STAGE_TEX.put(InfectionStage.HEAVY,    LabyrinthMod.id("textures/skin_overlay/stage_5.png"));
        STAGE_TEX.put(InfectionStage.CRITICAL, LabyrinthMod.id("textures/skin_overlay/stage_6.png"));
        STAGE_TEX.put(InfectionStage.TERMINAL, LabyrinthMod.id("textures/skin_overlay/stage_7.png"));
    }

    /** Вызывается из RegisterClientReloadListenersEvent — сбрасывает кэш. */
    public static void clearExistsCache() {
        EXISTS_CACHE.clear();
    }

    public InfectionRenderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** Дистанция, дальше которой оверлей не рисуем — на таком расстоянии разница
     *  визуально незаметна, но мы экономим целый второй model.renderToBuffer на игрока. */
    private static final double MAX_OVERLAY_DISTANCE_SQR = 48.0 * 48.0;

    @Override
    public void render(@NotNull PoseStack pose,
                       @NotNull MultiBufferSource buffers,
                       int packedLight,
                       @NotNull AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        int level = ClientInfectionCache.get(player.getUUID());
        if (level <= 0) return;

        // Дистанция до камеры — далёким игрокам оверлей не нужен (микро-FPS-выигрыш на массовке).
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double dx = mc.player.getX() - player.getX();
            double dy = mc.player.getY() - player.getY();
            double dz = mc.player.getZ() - player.getZ();
            if (dx * dx + dy * dy + dz * dz > MAX_OVERLAY_DISTANCE_SQR) return;
        }

        InfectionStage stage = InfectionStage.fromLevel(level);
        ResourceLocation pngTex = STAGE_TEX.get(stage);
        boolean usePng = pngTex != null && resourceExists(pngTex);

        ResourceLocation tex = usePng ? pngTex : InfectionTextureCache.textureFor(level);
        PlayerModel<AbstractClientPlayer> model = this.getParentModel();

        pose.pushPose();
        pose.scale(1.005f, 1.005f, 1.005f);

        // Если используем процедурный fallback — снижаем альфу до 0.6, чтобы не выглядело так агрессивно.
        // PNG-оверлей считается уже отбалансированным художником.
        float alpha = usePng ? 1.0f : 0.6f;

        // На CRITICAL+ добавляем небольшой серо-зелёный тинт для «трупного» ощущения.
        float r = 1.0f, g = 1.0f, b = 1.0f;
        if (stage == InfectionStage.CRITICAL || stage == InfectionStage.TERMINAL) {
            r = 0.85f; g = 0.92f; b = 0.85f;
        } else if (stage == InfectionStage.HEAVY) {
            r = 0.92f; g = 0.95f; b = 0.92f;
        }

        var consumer = buffers.getBuffer(RenderType.entityTranslucent(tex));
        model.renderToBuffer(
                pose,
                consumer,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                r, g, b, alpha
        );

        pose.popPose();
    }

    /**
     * Безопасная проверка наличия ресурса. Кэшируется до reload-а ресурсов
     * (см. {@link #clearExistsCache()}). Иначе I/O-проверка делалась каждый кадр.
     */
    private static boolean resourceExists(ResourceLocation rl) {
        Boolean cached = EXISTS_CACHE.get(rl);
        if (cached != null) return cached;
        boolean result;
        try {
            result = Minecraft.getInstance().getResourceManager().getResource(rl).isPresent();
        } catch (Throwable t) {
            result = false;
        }
        EXISTS_CACHE.put(rl, result);
        return result;
    }
}
