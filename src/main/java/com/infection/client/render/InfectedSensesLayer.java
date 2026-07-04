package com.infection.client.render;

import com.infection.client.ClientInfectionCache;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.NotNull;

/**
 * «Слух» заражённого: при level ≥ 70 локальный игрок видит силуэты других
 * игроков сквозь стены в радиусе 20 блоков.
 *
 * Силуэт пульсирует как биение сердца:
 *   - 0..150ms «удар» — alpha максимум
 *   - 150..400ms — затухает
 *   - 400..550ms «второй удар» (lub-dub) — alpha поднимается снова, но слабее
 *   - 550..1000ms — полное затухание / низкая база
 *
 * Используется кастомный {@link InfectedSensesRenderType} с NO_DEPTH_TEST,
 * чтобы силуэт был виден сквозь стены. Цвет — приглушённый кроваво-красный.
 * Чем дальше цель — тем слабее силуэт (distance-falloff).
 */
public class InfectedSensesLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final int SENSE_LEVEL_THRESHOLD = 70;
    private static final double SENSE_RADIUS = 20.0;
    private static final double SENSE_RADIUS_SQR = SENSE_RADIUS * SENSE_RADIUS;

    /** Кэш уровня заражения локального игрока на один кадр. Сбрасывается каждый
     *  RenderTickEvent чтобы не дёргать ConcurrentHashMap для КАЖДОГО другого
     *  игрока в кадре. На крупных серверах это десятки лишних lookup'ов. */
    private static int cachedLocalLevel = -1;
    private static long cachedLocalLevelFrame = -1;

    public InfectedSensesLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (player == mc.player) return;
        if (player.isInvisible()) return;

        // Кэшируем уровень локального игрока на текущий кадр (frameTime в наносекундах).
        long frame = System.nanoTime() / 1_000_000L;  // мс
        int infectionLevel;
        if (frame == cachedLocalLevelFrame) {
            infectionLevel = cachedLocalLevel;
        } else {
            infectionLevel = ClientInfectionCache.get(mc.player.getUUID());
            cachedLocalLevel = infectionLevel;
            cachedLocalLevelFrame = frame;
        }
        if (infectionLevel < SENSE_LEVEL_THRESHOLD) return;

        // Squared distance — экономим sqrt внутри distanceTo.
        double dx = mc.player.getX() - player.getX();
        double dy = mc.player.getY() - player.getY();
        double dz = mc.player.getZ() - player.getZ();
        double distSqr = dx * dx + dy * dy + dz * dz;
        if (distSqr > SENSE_RADIUS_SQR) return;
        double dist = Math.sqrt(distSqr);

        // Distance falloff: на 0 блоках = 1.0, на 20 = 0.0
        float distAlpha = (float) Math.max(0, Math.min(1.0, (SENSE_RADIUS - dist) / SENSE_RADIUS));
        // Heartbeat-пульс с lub-dub-паттерном.
        float pulse = heartbeatPulse(System.currentTimeMillis());
        float alpha = distAlpha * pulse;

        if (alpha < 0.04f) return;

        PlayerModel<AbstractClientPlayer> model = this.getParentModel();
        var consumer = buffers.getBuffer(InfectedSensesRenderType.ghost(player.getSkinTextureLocation()));

        pose.pushPose();
        pose.scale(1.005f, 1.005f, 1.005f);
        // FULL_BRIGHT (0xF000F0) вместо packedLight — силуэт сэмплит самый яркий пиксель
        // лайтмапа независимо от мирового освещения. Иначе под blindness-эффектом или
        // в полной темноте силуэт исчезает вместе с миром.
        // Текстура — белая 1×1 (см. InfectedSensesRenderType), поэтому цвет вершин даёт
        // итоговый цвет напрямую. Ярко-красный (1.0, 0.05, 0.05) — хорошо различимый силуэт.
        model.renderToBuffer(pose, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                1.0f, 0.05f, 0.05f, alpha);
        pose.popPose();
    }

    /**
     * Двухтактный пульс ~60 BPM (1 сек на цикл).
     *  0..150 мс    — сильный удар (alpha 0.3 → 1.0 → 0.6)
     *  150..400 мс  — затухание (0.6 → 0.2)
     *  400..550 мс  — второй удар, слабее (0.2 → 0.6 → 0.3)
     *  550..1000 мс — низкая база (0.15)
     */
    private static float heartbeatPulse(long nowMs) {
        long t = nowMs % 1000L;
        if (t < 150) {
            float x = t / 150f;
            // ramp up to 1.0 at t=75, down to 0.6 at t=150
            return x < 0.5f ? 0.3f + 1.4f * x : 1.0f - 0.8f * (x - 0.5f);
        }
        if (t < 400) {
            float x = (t - 150) / 250f;
            return 0.6f - 0.4f * x;
        }
        if (t < 550) {
            float x = (t - 400) / 150f;
            return x < 0.5f ? 0.2f + 0.8f * x : 0.6f - 0.6f * (x - 0.5f);
        }
        // tail
        return 0.15f;
    }
}
