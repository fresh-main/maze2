package com.infection.client.overlay;

import com.infection.client.ClientInfectionCache;
import com.infection.settings.ClientSettings;
import com.infection.settings.InfectionSettings;
import com.infection.sound.InfectionModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Приступ галлюцинации (heart-attack стиль): экран темнеет, появляется несколько фраз,
 * каждую секунду — «удар сердца»: красное вспыхивающее vignette, звук heartbeat, лёгкая дрожь
 * экрана, пульс масштаба у фраз, мелкие цветовые артефакты по краям. Фразы НЕ исчезают
 * до конца приступа, имеют хроматическую аберрацию (красный/зелёный/синий сдвиг).
 *
 * Если в кэше выставлен hallucinationsSuppressedUntil > now — приступы не запускаются (морфин).
 */
public final class HallucinationOverlay implements IGuiOverlay {

    public static final HallucinationOverlay INSTANCE = new HallucinationOverlay();

    public static final List<String> MESSAGES = List.of(
            "УБЕЙ ВСЕХ",
            "ТЕБЕ ЭТО НЕ НУЖНО",
            "ОНИ СМОТРЯТ",
            "ТЫ ОДИН ИЗ НАС",
            "БЕГИ",
            "НЕТ СМЫСЛА",
            "УЖЕ ПОЗДНО",
            "ТЫ НЕ ТЫ",
            "ОНИ ВСЕ ЗНАЮТ",
            "СПРЯЧЬСЯ",
            "ОТКРОЙ ГЛАЗА",
            "ПОСМОТРИ НАЗАД",
            "СЛЫШИШЬ?",
            "ВЫХОДА НЕТ"
    );

    private static final int HEARTBEAT_INTERVAL_TICKS = 20; // 1 удар в секунду

    private final Random rng = new Random();

    private long lastAttackEndTick = 0L;
    private Attack active = null;
    private boolean wasBelowMin = true;

    /** Если true — текущий приступ запущен админом извне (ивентик), а не из-за уровня заражения. */
    private boolean forcedAttack = false;

    private HallucinationOverlay() {}

    /**
     * Полный сброс состояния — для вызова при дисконнекте/выгрузке мира.
     * Без этого активный приступ оставался в статическом INSTANCE и продолжал
     * рисоваться на следующем заходе на сервер.
     */
    public void resetState() {
        active = null;
        forcedAttack = false;
        wasBelowMin = true;
        lastAttackEndTick = 0L;
    }

    /**
     * Принудительный запуск приступа от админ-инвентика.
     * Игнорирует minLevel и suppression, использует переданный список фраз.
     */
    public void forceTrigger(boolean useDefaults, List<String> customPhrases, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        List<String> pool = new ArrayList<>();
        if (customPhrases != null) {
            for (String s : customPhrases) {
                if (s != null && !s.isEmpty()) pool.add(s);
            }
        }
        if (useDefaults || pool.isEmpty()) {
            pool.addAll(MESSAGES);
        }
        if (pool.isEmpty()) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        long now = mc.level.getGameTime();
        int dur = Math.max(20, durationTicks);

        int count = 4 + rng.nextInt(4); // 4..7 одновременных фраз — насыщенный приступ
        List<FloatingText> texts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String msg = pool.get(rng.nextInt(pool.size()));
            float x = screenW * (0.15f + rng.nextFloat() * 0.7f);
            float y = screenH * (0.15f + rng.nextFloat() * 0.7f);
            float angle = (rng.nextFloat() - 0.5f) * 0.9f;
            float scale = 1.3f + rng.nextFloat() * 1.4f;
            float shakeSeed = rng.nextFloat() * 1000f;
            int color = 0x8B0E0E;
            texts.add(new FloatingText(msg, x, y, angle, scale, shakeSeed, color));
        }
        active = new Attack(now, now + dur, texts, rng.nextFloat() * 1000f, now);
        forcedAttack = true;
        wasBelowMin = false;
        lastAttackEndTick = now + dur;
    }

    @Override
    public void render(net.minecraftforge.client.gui.overlay.ForgeGui gui,
                       GuiGraphics gfx,
                       float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        InfectionSettings s = ClientSettings.get();
        int level = ClientInfectionCache.get(mc.player.getUUID());
        long now = mc.level.getGameTime();

        // Forced-приступ от ивентика игнорирует все остальные проверки.
        if (forcedAttack) {
            if (active == null || active.endTick <= now) {
                active = null;
                forcedAttack = false;
                lastAttackEndTick = now;
                // дальше идёт обычная логика
            } else {
                renderAttack(active, gfx, mc.font, mc, now, partialTick, screenW, screenH);
                return;
            }
        }

        if (level < s.minLevel) {
            active = null;
            wasBelowMin = true;
            return;
        }

        // Морфин подавил галлюцинации?
        if (ClientInfectionCache.isHallucinationsSuppressed(mc.player.getUUID(), now)) {
            active = null;
            wasBelowMin = false;
            lastAttackEndTick = now;
            return;
        }

        if (wasBelowMin) {
            lastAttackEndTick = now;
            wasBelowMin = false;
        }

        int interval = s.computeIntervalTicks(level);

        if (active != null && active.endTick <= now) {
            lastAttackEndTick = now;
            active = null;
        }

        if (active == null && now - lastAttackEndTick >= interval) {
            active = buildAttack(s, now, level, screenW, screenH);
        }

        if (active != null) {
            renderAttack(active, gfx, mc.font, mc, now, partialTick, screenW, screenH);
        }
    }

    private Attack buildAttack(InfectionSettings s, long now, int level, int screenW, int screenH) {
        // Используем то, что выставил админ через настройки. Без скрытого «не меньше 160».
        int minD = Math.max(1, s.attackMinDurationTicks);
        int maxD = Math.max(minD + 1, s.attackMaxDurationTicks);
        int dur = minD + rng.nextInt(maxD - minD);

        int count = countFor(level);
        List<FloatingText> texts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String msg = MESSAGES.get(rng.nextInt(MESSAGES.size()));
            float x = screenW * (0.15f + rng.nextFloat() * 0.7f);
            float y = screenH * (0.15f + rng.nextFloat() * 0.7f);
            float angle = (rng.nextFloat() - 0.5f) * 0.9f;
            float scale = 1.2f + rng.nextFloat() * 1.5f;
            float shakeSeed = rng.nextFloat() * 1000f;
            int tone = rng.nextInt(10);
            int color = tone < 7 ? 0x8B0E0E : (tone < 9 ? 0xB01010 : 0x661212);
            texts.add(new FloatingText(msg, x, y, angle, scale, shakeSeed, color));
        }
        return new Attack(now, now + dur, texts, rng.nextFloat() * 1000f, now);
    }

    private int countFor(int level) {
        if (level < 20) return 1 + rng.nextInt(2);
        if (level < 40) return 2 + rng.nextInt(2);
        if (level < 60) return 3 + rng.nextInt(2);
        if (level < 80) return 4 + rng.nextInt(2);
        return 5 + rng.nextInt(3);
    }

    private void renderAttack(Attack a, GuiGraphics gfx, Font font, Minecraft mc,
                              long now, float partialTick, int screenW, int screenH) {
        float totalDur = a.endTick - a.startTick;
        float t = Mth.clamp((now - a.startTick + partialTick) / totalDur, 0f, 1f);
        float envelope = t < 0.10f ? (t / 0.10f) : (t > 0.90f ? (1f - (t - 0.90f) / 0.10f) : 1f);
        envelope = Mth.clamp(envelope, 0f, 1f);

        // Затемнение всего экрана
        int darken = (int) (140 * envelope);
        gfx.fill(0, 0, screenW, screenH, (darken << 24));

        // Heartbeat-удар: проигрываем звук + visualпульс с интервалом 20 тиков
        long sinceStart = now - a.startTick;
        // прогресс внутри текущего удара 0..1
        float beatPhase = ((sinceStart % HEARTBEAT_INTERVAL_TICKS) + partialTick) / (float) HEARTBEAT_INTERVAL_TICKS;

        // Звук на старте каждого удара (когда beatPhase возвращается к нулю — ловим переход).
        if (a.lastBeatTick + HEARTBEAT_INTERVAL_TICKS <= now) {
            a.lastBeatTick = now - (now % HEARTBEAT_INTERVAL_TICKS);
            mc.getSoundManager().play(
                    SimpleSoundInstance.forUI(InfectionModSounds.HEARTBEAT.get(), 1.0f, 0.85f * envelope + 0.15f));
        }

        // Vignette-пульс: красные полосы по краям, ярче при beatPhase < 0.2
        float beatFlash = beatPhase < 0.20f ? (1f - beatPhase / 0.20f) : 0f;
        int vignetteA = (int) ((140 + 100 * beatFlash) * envelope);
        gfx.fillGradient(0, 0, screenW, 32, (vignetteA << 24) | 0x8B0E0E, 0);
        gfx.fillGradient(0, screenH - 32, screenW, screenH, 0, (vignetteA << 24) | 0x8B0E0E);
        gfx.fillGradient(0, 0, 32, screenH, (vignetteA << 24) | 0x8B0E0E, 0);
        gfx.fillGradient(screenW - 32, 0, screenW, screenH, 0, (vignetteA << 24) | 0x8B0E0E);

        // Имитация «размытия» — лёгкая белая засветка на пике удара.
        if (beatFlash > 0.4f) {
            int wA = (int) (28 * (beatFlash - 0.4f) / 0.6f * envelope);
            gfx.fill(0, 0, screenW, screenH, (wA << 24) | 0xFFFFFF);
        }

        // Цветовые артефакты — короткие полосы случайных каналов по экрану
        if (rng.nextInt(10) < 3) {
            int n = 1 + rng.nextInt(3);
            for (int i = 0; i < n; i++) {
                int sx = rng.nextInt(screenW);
                int sy = rng.nextInt(screenH);
                int sw = 12 + rng.nextInt(60);
                int sh = 1 + rng.nextInt(2);
                int chan = rng.nextInt(3);
                int color = chan == 0 ? 0x80FF2233 : chan == 1 ? 0x8033FF44 : 0x803344FF;
                gfx.fill(sx, sy, Math.min(screenW, sx + sw), Math.min(screenH, sy + sh), color);
            }
        }

        // Глобальная дрожь экрана + пульс-сжатие при ударе
        float time = sinceStart + partialTick;
        float globalShakeX = (float) Math.sin(time * 1.9f + a.seed) * 2.3f * envelope
                + (float) Math.sin(time * 4.3f + a.seed * 0.7f) * 1.1f * envelope;
        float globalShakeY = (float) Math.cos(time * 2.2f + a.seed) * 2.0f * envelope
                + (float) Math.cos(time * 5.1f + a.seed * 1.3f) * 0.9f * envelope;
        float beatScale = 1f + 0.05f * beatFlash * envelope;

        for (FloatingText ft : a.texts) {
            renderText(ft, gfx, font, time, envelope, globalShakeX, globalShakeY, beatScale);
        }
    }

    private void renderText(FloatingText ft, GuiGraphics gfx, Font font, float time, float envelope,
                            float globalShakeX, float globalShakeY, float beatScale) {
        float jitterX = (float) Math.sin(time * 6.7f + ft.shakeSeed) * 1.6f
                + (float) Math.sin(time * 13.1f + ft.shakeSeed * 0.5f) * 0.9f;
        float jitterY = (float) Math.cos(time * 7.3f + ft.shakeSeed) * 1.6f
                + (float) Math.cos(time * 15.7f + ft.shakeSeed * 0.3f) * 0.9f;
        float wobbleAngle = ft.baseAngle + (float) Math.sin(time * 2.1f + ft.shakeSeed * 0.1f) * 0.05f;

        float x = ft.x + globalShakeX + jitterX;
        float y = ft.y + globalShakeY + jitterY;

        gfx.pose().pushPose();
        gfx.pose().translate(x, y, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotation(wobbleAngle));
        float effScale = ft.scale * beatScale;
        gfx.pose().scale(effScale, effScale, 1f);

        int alpha = (int) (255 * envelope);
        int baseColor = (alpha << 24) | (ft.color & 0xFFFFFF);
        // Хроматическая аберрация — три копии текста в R/G/B каналах со сдвигом.
        int aberrA = (int) (170 * envelope);
        int rChan = (aberrA << 24) | 0xFF2233;
        int gChan = (aberrA << 24) | 0x33FF44;
        int bChan = (aberrA << 24) | 0x3344FF;
        int shadow = (int) (180 * envelope) << 24;

        int w = font.width(ft.text);
        gfx.drawString(font, ft.text, -w / 2 + 2, 2, shadow | 0x000000, false);
        gfx.drawString(font, ft.text, -w / 2 + 2, 0, rChan, false);
        gfx.drawString(font, ft.text, -w / 2 - 2, 0, bChan, false);
        gfx.drawString(font, ft.text, -w / 2, -1, gChan, false);
        gfx.drawString(font, ft.text, -w / 2, 0, baseColor, false);

        gfx.pose().popPose();
    }

    private record FloatingText(String text, float x, float y, float baseAngle, float scale,
                                float shakeSeed, int color) {}

    private static final class Attack {
        final long startTick;
        final long endTick;
        final List<FloatingText> texts;
        final float seed;
        long lastBeatTick;

        Attack(long startTick, long endTick, List<FloatingText> texts, float seed, long lastBeatTick) {
            this.startTick = startTick;
            this.endTick = endTick;
            this.texts = texts;
            this.seed = seed;
            this.lastBeatTick = lastBeatTick - HEARTBEAT_INTERVAL_TICKS; // первый удар сразу
        }
    }
}
