package com.infection.client.render;

import com.labyrinthmod.LabyrinthMod;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Кастомный RenderType для «слуха» заражённого: рендерит ENTITY-модель с
 * отключённой проверкой глубины — игрок видит силуэт сквозь стены.
 *
 * Раньше нужные protected-static поля {@code RenderStateShard} (TRANSLUCENT_TRANSPARENCY,
 * NO_DEPTH_TEST, и т.д.) брались через reflection по имени. В production-jar после
 * reobfuscation эти поля получают SRG-имена ({@code f_110131_} и т.п.) — getDeclaredField
 * падает с NoSuchFieldException, и весь рендер слуха крашит игру.
 *
 * Здесь мы инстанцируем shard'ы напрямую — у всех нужных под-классов RenderStateShard
 * (TransparencyStateShard, DepthTestStateShard, etc.) конструкторы публичные.
 * Логика дублируется из vanilla 1.20.1 RenderStateShard. Никакой reflection.
 */
public final class InfectedSensesRenderType {

    private InfectedSensesRenderType() {}

    private static final RenderStateShard.TransparencyStateShard TRANSLUCENT_TRANSPARENCY =
            new RenderStateShard.TransparencyStateShard("translucent_transparency",
                    () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFuncSeparate(
                                GlStateManager.SourceFactor.SRC_ALPHA,
                                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                GlStateManager.SourceFactor.ONE,
                                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                    },
                    () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    });

    /** false = НЕ использовать лайтмап. Силуэт светится сам по себе и виден даже под
     *  blindness-эффектом и в полной темноте — иначе мир чернеет и силуэт уходит с ним. */
    private static final RenderStateShard.LightmapStateShard NO_LIGHTMAP =
            new RenderStateShard.LightmapStateShard(false);

    private static final RenderStateShard.OverlayStateShard OVERLAY =
            new RenderStateShard.OverlayStateShard(true);

    private static final RenderStateShard.CullStateShard NO_CULL =
            new RenderStateShard.CullStateShard(false);

    /** GL_ALWAYS = 519 — всегда проходим depth-тест (силуэт виден сквозь стены). */
    private static final RenderStateShard.DepthTestStateShard NO_DEPTH_TEST =
            new RenderStateShard.DepthTestStateShard("always", 519);

    /** writeColor=true, writeDepth=false — пишем только цвет, не трогаем z-буфер,
     *  чтобы силуэт не «протыкивал» геометрию у игрока за стеной. */
    private static final RenderStateShard.WriteMaskStateShard COLOR_WRITE =
            new RenderStateShard.WriteMaskStateShard(true, false);

    /** 1×1 белая текстура — позволяет цвету вершин полностью контролировать итоговый
     *  цвет силуэта. Если использовать скин игрока (тёмные пиксели), результат
     *  texture×color получается тусклый и силуэт почти не видно сквозь стены. */
    private static final ResourceLocation WHITE_TEX =
            LabyrinthMod.id("textures/effect/silhouette_white.png");

    private static final RenderType GHOST = build();

    private static RenderType build() {
        // Кастомный шейдер «infected_silhouette» (см. InfectedSensesShaderRegistry) —
        // не применяет ни lightmap, ни linear fog. Vanilla rendertype_eyes применяет
        // fog → под blindness fog становится плотным и затемняет силуэт.
        RenderStateShard.ShaderStateShard shader = new RenderStateShard.ShaderStateShard(
                InfectedSensesShaderRegistry::getShader);
        RenderStateShard.TextureStateShard texState =
                new RenderStateShard.TextureStateShard(WHITE_TEX, false, false);

        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(shader)
                .setTextureState(texState)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setDepthTestState(NO_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false);

        return RenderType.create("infection_senses_ghost",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256, false, true, state);
    }

    /** Сохранили сигнатуру с tex-параметром, чтобы не ломать вызывающий код,
     *  но текстура скина игнорируется — используется белая 1×1. */
    public static RenderType ghost(ResourceLocation ignoredPlayerSkin) {
        return GHOST;
    }
}
