package com.otbor.client;

import com.otbor.client.widgets.PaperRender;
import com.otbor.client.widgets.PaperWidgets;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import net.minecraft.client.gui.narration.NarrationElementOutput;

public class EmbeddiumOptionsScreen extends Screen {
    private final Screen parent;

    private static final int TAB_GENERAL = 0;
    private static final int TAB_QUALITY = 1;
    private static final int TAB_PERFORMANCE = 2;
    private static final int TAB_ADVANCED = 3;
    private static final int TAB_NOTIFICATIONS = 4;

    private static final String[] TAB_LABELS = {
            "ОБЩЕЕ ", "КАЧЕСТВО ", "СКОРОСТЬ ", "РАСШИРЕННЫЕ ", "УВЕДОМЛЕНИЯ "
    };
    private static final String[] TAB_SUBTITLES = {
            "базовые параметры ", "визуальное оформление ",
            "оптимизация рендера ", "тонкая настройка ",
            "сообщения и подсказки "
    };

    private int tab = TAB_GENERAL;
    private float scrollOffset = 0f;
    private float maxScroll = 0f;
    private boolean draggingThumb = false;
    private float dragStartY = 0f;
    private float dragStartScroll = 0f;

    private static final int TABS_W = 150;
    private static final int SLIDER_W = 10;
    private static final int CARD_W = 440;
    private static final int CARD_H = 360;
    private static final int HEADER_H = 36;
    private static final int PADDING = 14;
    private static final int TAB_H = 42;
    private static final int TAB_GAP = 6;
    private static final int BTN_H = 28;
    private static final int BTN_GAP = 8;

    private int cardY;
    private int actualCardH;

    // ===== СИСТЕМА TOOLTIP =====
    private String currentTooltip = null;
    private int tooltipMouseX = 0;
    private int tooltipMouseY = 0;

    private static Object optionsInstance = null;
    private static Object qualityObj, performanceObj, advancedObj, notificationsObj;
    private static boolean initialized = false;
    private static boolean available = false;

    public EmbeddiumOptionsScreen(Screen parent) {
        super(Component.literal("ПРОДВИНУТЫЕ НАСТРОЙКИ "));
        this.parent = parent;
        initReflection();
    }

    private static void initReflection() {
        if (initialized) return;
        initialized = true;
        try {
            if (!net.minecraftforge.fml.ModList.get().isLoaded("embeddium")) return;
            Class<?> clientModClass = Class.forName("me.jellysquid.mods.sodium.client.SodiumClientMod");
            Method optionsMethod = clientModClass.getMethod("options");
            optionsInstance = optionsMethod.invoke(null);
            if (optionsInstance == null) return;
            qualityObj = getField(optionsInstance, "quality");
            performanceObj = getField(optionsInstance, "performance");
            advancedObj = getField(optionsInstance, "advanced");
            notificationsObj = getField(optionsInstance, "notifications");
            available = true;
        } catch (Throwable t) {
            available = false;
        }
    }

    private static Object getField(Object obj, String name) {
        if (obj == null) return null;
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable t) { return null; }
    }

    private static Object getFieldValue(Object section, String name) {
        if (section == null) return null;
        try {
            Field f = section.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(section);
        } catch (Throwable t) { return null; }
    }

    private static boolean fieldExists(Object section, String name) {
        if (section == null) return false;
        try {
            section.getClass().getDeclaredField(name);
            return true;
        } catch (Throwable t) { return false; }
    }

    private static void setFieldValue(Object section, String name, Object value) {
        if (section == null) return;
        try {
            Field f = section.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(section, value);
        } catch (Throwable ignored) {}
    }

    private static int enumOrdinal(Object section, String name) {
        Object v = getFieldValue(section, name);
        if (v instanceof Enum<?> e) return e.ordinal();
        return 0;
    }

    private static void setEnumByOrdinal(Object section, String name, int ordinal) {
        if (section == null) return;
        try {
            Field f = section.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Object[] constants = f.getType().getEnumConstants();
            if (constants != null && ordinal >= 0 && ordinal < constants.length) {
                f.set(section, constants[ordinal]);
            }
        } catch (Throwable ignored) {}
    }

    private static boolean getBool(Object section, String name, boolean def) {
        Object v = getFieldValue(section, name);
        if (v instanceof Boolean b) return b;
        return def;
    }

    private static int getInt(Object section, String name, int def) {
        Object v = getFieldValue(section, name);
        if (v instanceof Number n) return n.intValue();
        return def;
    }

    // НОВЫЙ МЕТОД ДЛЯ ДРОБНЫХ ЧИСЕЛ (НУЖЕН ДЛЯ entityDistance)
    private static float getFloat(Object section, String name, float def) {
        Object v = getFieldValue(section, name);
        if (v instanceof Number n) return n.floatValue();
        return def;
    }

    public static boolean isEmbeddiumAvailable() {
        initReflection();
        return available;
    }

    private interface SettingEntry {
        float height();
        AbstractWidget createWidget(int x, int y, int w);
    }

    // ===== WRAPPER ДЛЯ TOOLTIP =====
    private class TooltipWrapper extends AbstractWidget {
        private final AbstractWidget wrapped;
        private final String tooltip;

        TooltipWrapper(AbstractWidget wrapped, String tooltip) {
            super(wrapped.getX(), wrapped.getY(), wrapped.getWidth(), wrapped.getHeight(), wrapped.getMessage());
            this.wrapped = wrapped;
            this.tooltip = tooltip;
        }

        @Override
        protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            wrapped.render(gfx, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) { return wrapped.isMouseOver(mouseX, mouseY); }
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) { return wrapped.mouseClicked(mouseX, mouseY, button); }
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return wrapped.mouseDragged(mouseX, mouseY, button, dragX, dragY); }
        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) { return wrapped.mouseReleased(mouseX, mouseY, button); }
        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return wrapped.keyPressed(keyCode, scanCode, modifiers); }
        @Override
        public boolean charTyped(char codePoint, int modifiers) { return wrapped.charTyped(codePoint, modifiers); }
        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}

        public String getTooltipText() { return tooltip; }
    }

    // ===== ENTRY КЛАССЫ =====
    private class BoolEntry implements SettingEntry {
        final Object section; final String field; final String title; final boolean def; final String tooltip;
        BoolEntry(Object section, String field, String title, boolean def, String tooltip) {
            this.section = section; this.field = field; this.title = title; this.def = def; this.tooltip = tooltip;
        }
        @Override public float height() { return 22; }
        @Override public AbstractWidget createWidget(int x, int y, int w) {
            AbstractWidget widget = PaperWidgets.paperCheckbox(x, y, w, 15, title, () -> getBool(section, field, def), v -> setFieldValue(section, field, v));
            return tooltip != null ? new TooltipWrapper(widget, tooltip) : widget;
        }
    }

    private class EnumEntry implements SettingEntry {
        final Object section; final String field; final String title; final String[] labels; final String tooltip;
        EnumEntry(Object section, String field, String title, String[] labels, String tooltip) {
            this.section = section; this.field = field; this.title = title; this.labels = labels; this.tooltip = tooltip;
        }
        @Override public float height() { return 38; }
        @Override public AbstractWidget createWidget(int x, int y, int w) {
            addRenderableWidget(new AbstractWidget(x, y, w, 14, Component.literal(title)) {
                @Override protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) { gfx.drawString(font, title, x, y + 2, PaperRender.INK, false); }
                @Override protected void updateWidgetNarration(NarrationElementOutput out) {}
                @Override public boolean isMouseOver(double mx, double my) { return false; }
                @Override public boolean mouseClicked(double mx, double my, int btn) { return false; }
            });
            AbstractWidget widget = PaperWidgets.paperSegment(x, y + 13, w, 17, labels, () -> enumOrdinal(section, field), i -> setEnumByOrdinal(section, field, i));
            return tooltip != null ? new TooltipWrapper(widget, tooltip) : widget;
        }
    }

    private class IntEntry implements SettingEntry {
        final Object section; final String field; final String title; final int min, max; final IntFunction<String> fmt; final String tooltip;
        IntEntry(Object section, String field, String title, int min, int max, IntFunction<String> fmt, String tooltip) {
            this.section = section; this.field = field; this.title = title; this.min = min; this.max = max; this.fmt = fmt; this.tooltip = tooltip;
        }
        @Override public float height() { return 30; }
        @Override public AbstractWidget createWidget(int x, int y, int w) {
            AbstractWidget widget = PaperWidgets.paperSliderDeferred(x, y, w, 19, title, (getInt(section, field, min) - min) / (float) (max - min), v -> setFieldValue(section, field, (int) Math.round(min + v * (max - min))), v -> fmt.apply((int) Math.round(min + v * (max - min))));
            return tooltip != null ? new TooltipWrapper(widget, tooltip) : widget;
        }
    }

    // НОВЫЙ КЛАСС ДЛЯ ДРОБНЫХ ЧИСЕЛ (FLOAT)
    private class FloatEntry implements SettingEntry {
        final Object section; final String field; final String title; final float min, max; final Function<Float, String> fmt; final String tooltip;
        FloatEntry(Object section, String field, String title, float min, float max, Function<Float, String> fmt, String tooltip) {
            this.section = section; this.field = field; this.title = title; this.min = min; this.max = max; this.fmt = fmt; this.tooltip = tooltip;
        }
        @Override public float height() { return 30; }
        @Override public AbstractWidget createWidget(int x, int y, int w) {
            float current = getFloat(section, field, min);
            float progress = (current - min) / (max - min);
            AbstractWidget widget = PaperWidgets.paperSliderDeferred(x, y, w, 19, title, progress,
                    v -> setFieldValue(section, field, min + v * (max - min)),
                    v -> fmt.apply((float) (min + v * (max - min))));
            return tooltip != null ? new TooltipWrapper(widget, tooltip) : widget;
        }
    }

    private class VanillaBoolEntry implements SettingEntry {
        final String title; final java.util.function.BooleanSupplier getter; final java.util.function.Consumer<Boolean> setter; final String tooltip;
        VanillaBoolEntry(String title, java.util.function.BooleanSupplier getter, java.util.function.Consumer<Boolean> setter, String tooltip) {
            this.title = title; this.getter = getter; this.setter = setter; this.tooltip = tooltip;
        }
        @Override public float height() { return 22; }
        @Override public AbstractWidget createWidget(int x, int y, int w) {
            AbstractWidget widget = PaperWidgets.paperCheckbox(x, y, w, 15, title, getter, setter);
            return tooltip != null ? new TooltipWrapper(widget, tooltip) : widget;
        }
    }

    private class VanillaEnumEntry implements SettingEntry {
        final String title; final String[] labels; final java.util.function.IntSupplier getter; final java.util.function.IntConsumer setter; final String tooltip;
        VanillaEnumEntry(String title, String[] labels, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter, String tooltip) {
            this.title = title; this.labels = labels; this.getter = getter; this.setter = setter; this.tooltip = tooltip;
        }
        @Override public float height() { return 38; }
        @Override public AbstractWidget createWidget(int x, int y, int w) {
            addRenderableWidget(new AbstractWidget(x, y, w, 14, Component.literal(title)) {
                @Override protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) { gfx.drawString(font, title, x, y + 2, PaperRender.INK, false); }
                @Override protected void updateWidgetNarration(NarrationElementOutput out) {}
                @Override public boolean isMouseOver(double mx, double my) { return false; }
                @Override public boolean mouseClicked(double mx, double my, int btn) { return false; }
            });
            AbstractWidget widget = PaperWidgets.paperSegment(x, y + 13, w, 17, labels, getter, setter);
            return tooltip != null ? new TooltipWrapper(widget, tooltip) : widget;
        }
    }

    private class VanillaIntEntry implements SettingEntry {
        final String title; final int min, max; final java.util.function.IntSupplier getter; final java.util.function.IntConsumer setter; final IntFunction<String> fmt; final String tooltip;
        VanillaIntEntry(String title, int min, int max, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter, IntFunction<String> fmt, String tooltip) {
            this.title = title; this.min = min; this.max = max; this.getter = getter; this.setter = setter; this.fmt = fmt; this.tooltip = tooltip;
        }
        @Override public float height() { return 30; }
        @Override public AbstractWidget createWidget(int x, int y, int w) {
            AbstractWidget widget = PaperWidgets.paperSliderDeferred(x, y, w, 19, title, (getter.getAsInt() - min) / (float) (max - min), v -> setter.accept((int) Math.round(min + v * (max - min))), v -> fmt.apply((int) Math.round(min + v * (max - min))));
            return tooltip != null ? new TooltipWrapper(widget, tooltip) : widget;
        }
    }

    private class HeaderEntry implements SettingEntry {
        final String text;
        HeaderEntry(String text) { this.text = text; }
        @Override public float height() { return 18; }
        @Override public AbstractWidget createWidget(int x, int y, int w) {
            return new AbstractWidget(x, y, w, 16, Component.literal(text)) {
                @Override protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
                    gfx.fill(x, y + 9, x + w, y + 10, PaperRender.withAlpha(PaperRender.INK_FADED, 0.3f));
                    gfx.drawString(font, text, x + 4, y + 2, PaperRender.INK_FADED, false);
                }
                @Override protected void updateWidgetNarration(NarrationElementOutput out) {}
                @Override public boolean isMouseOver(double mx, double my) { return false; }
                @Override public void onClick(double mx, double my) {}
            };
        }
    }

    private List<SettingEntry> getEntriesForTab(int t) {
        List<SettingEntry> list = new ArrayList<>();
        Options o = Minecraft.getInstance().options;

        switch (t) {
            case TAB_GENERAL -> {
                list.add(new HeaderEntry("ДИСПЛЕЙ "));
                list.add(new VanillaBoolEntry("ПОЛНОЭКРАННЫЙ ", () -> o.fullscreen().get(), v -> o.fullscreen().set(v), "Переключает игру в полноэкранный режим.\nПовышает FPS и снижает задержку ввода. "));
                list.add(new VanillaIntEntry("МАСШТАБ GUI ", 0, 6, () -> o.guiScale().get(), v -> o.guiScale().set(v), v -> v == 0 ? "АВТО " : v + "x ", "Размер элементов интерфейса.\n0 = автоматически (максимальный размер).\nМеньший масштаб = больше информации на экране. "));
                list.add(new VanillaIntEntry("ЯРКОСТЬ ", 0, 100, () -> (int)(o.gamma().get() * 100), v -> o.gamma().set(v / 100.0), v -> v + "% ", "Регулирует яркость игры.\n0% = стандартная яркость.\n100% = максимальная (гамма).\nПолезно для игры в тёмных пещерах. "));

                list.add(new HeaderEntry("КАДРЫ "));
                list.add(new VanillaIntEntry("МАКС. FPS ", 10, 260, () -> o.framerateLimit().get(), v -> o.framerateLimit().set(v), v -> v >= 260 ? "МАКС " : v + " к/с ", "Ограничивает максимальное количество кадров в секунду.\nВысокие значения = плавнее игра, но больше нагрузка.\n260+ = без ограничений. "));
                list.add(new VanillaBoolEntry("VSYNC ", () -> o.enableVsync().get(), v -> o.enableVsync().set(v), "Вертикальная синхронизация.\nСинхронизирует FPS с частотой монитора.\nУбирает разрывы кадров, но может добавить микро-задержку. "));

                list.add(new HeaderEntry("ИНТЕРФЕЙС "));
                list.add(new VanillaBoolEntry("ПОКАЧИВАНИЕ ", () -> o.bobView().get(), v -> o.bobView().set(v), "Покачивание камеры при ходьбе.\nВКЛ = создаёт эффект шагов персонажа.\nВЫКЛ = камера неподвижна (меньше укачивания). "));
                list.add(new VanillaEnumEntry("ИНДИКАТОР АТАКИ ", new String[]{"ВЫКЛ ", "ПРИЦЕЛ ", "ПАНЕЛЬ "}, () -> o.attackIndicator().get().ordinal(), i -> o.attackIndicator().set(AttackIndicatorStatus.values()[Math.min(2, i)]), "Показывает индикатор кулдауна атаки.\nВЫКЛ = без индикатора.\nПРИЦЕЛ = в центре перекрестия.\nПАНЕЛЬ = отдельная полоска снизу. "));
                list.add(new VanillaBoolEntry("ИНДИКАТОР СОХРАНЕНИЯ ", () -> o.showAutosaveIndicator().get(), v -> o.showAutosaveIndicator().set(v), "Показывает уведомление при автосохранении мира.\nПолезно для понимания, когда игра сохраняет данные. "));
            }
            case TAB_QUALITY -> {
                list.add(new HeaderEntry("MINECRAFT "));
                list.add(new VanillaEnumEntry("ГРАФИКА ", new String[]{"БЫСТРО ", "КРАСИВО ", "ПРЕВОСХОДНО "}, () -> o.graphicsMode().get().getId(), i -> o.graphicsMode().set(GraphicsStatus.byId(i)), "Общий режим графики Minecraft.\nБЫСТРО = упрощённая (макс. FPS).\nКРАСИВО = стандартная.\nПРЕВОСХОДНО = улучшенные эффекты (требует мощное железо). "));
                list.add(new VanillaIntEntry("ДАЛЬНОСТЬ ЧАНКОВ ", 2, 32, () -> o.renderDistance().get(), v -> o.renderDistance().set(v), v -> v + " ч ", "Дальность прорисовки чанков.\n2-8 = высокий FPS, мало видно.\n12-16 = баланс.\n32 = очень далеко, но сильно нагружает CPU/GPU. "));
                list.add(new VanillaIntEntry("СИМУЛЯЦИЯ ", 2, 32, () -> o.simulationDistance().get(), v -> o.simulationDistance().set(v), v -> v + " ч ", "Расстояние, на котором работают механизмы и мобы.\nНе влияет на видимость, только на активность.\nМеньшее значение = выше производительность. "));
                list.add(new VanillaIntEntry("МИПМАППИНГ ", 0, 4, () -> o.mipmapLevels().get(), v -> o.mipmapLevels().set(v), v -> v == 0 ? "ВЫКЛ " : v + "x ", "Уровень мипмаппинга текстур.\nВЫКЛ = текстуры чёткие вблизи, но размыты вдали.\n1-4x = постепенное улучшение качества на расстоянии.\nПовышает качество, немного снижает FPS. "));
                list.add(new VanillaEnumEntry("ОБЛАКА ", new String[]{"ВЫКЛ ", "БЫСТРО ", "КРАСИВО "}, () -> o.cloudStatus().get().ordinal(), i -> o.cloudStatus().set(CloudStatus.values()[Math.min(2, i)]), "Отображение облаков.\nВЫКЛ = нет облаков (макс. FPS).\nБЫСТРО = простые 2D облака.\nКРАСИВО = объёмные 3D облака. "));
                list.add(new VanillaEnumEntry("ЧАСТИЦЫ ", new String[]{"ВСЕ ", "УМЕНЬШ. ", "МИНИМУМ "}, () -> o.particles().get().getId(), i -> o.particles().set(ParticleStatus.byId(i)), "Количество частиц (дым, огонь, дождь).\nВСЕ = все эффекты (красиво, но нагрузка).\nУМЕНЬШ. = меньше частиц.\nМИНИМУМ = только важные частицы. "));
                list.add(new VanillaBoolEntry("ПЛАВНОЕ ОСВЕЩЕНИЕ ", () -> o.ambientOcclusion().get(), v -> o.ambientOcclusion().set(v), "Плавное освещение блоков.\nВКЛ = мягкие тени в углах (реалистично).\nВЫКЛ = резкое освещение (выше FPS). "));
                list.add(new VanillaIntEntry("СМЕШЕНИЕ БИОМОВ ", 0, 7, () -> o.biomeBlendRadius().get(), v -> o.biomeBlendRadius().set(v), v -> v == 0 ? "ВЫКЛ " : v + " ч ", "Плавность перехода цветов между биомами.\n0 = резкая граница.\n7 = максимальная плавность.\nВлияет на цвет травы, листвы, воды. "));
                list.add(new VanillaIntEntry("ДАЛЬНОСТЬ СУЩНОСТЕЙ ", 50, 500, () -> (int)(o.entityDistanceScaling().get() * 100), v -> o.entityDistanceScaling().set(v / 100.0), v -> v + "% ", "Дальность прорисовки мобов и игроков.\n50% = видны только вблизи.\n100% = стандартно.\n500% = очень далеко (нагрузка на GPU). "));
                list.add(new VanillaBoolEntry("ТЕНИ СУЩНОСТЕЙ ", () -> o.entityShadows().get(), v -> o.entityShadows().set(v), "Тени под сущностями.\nВКЛ = реалистичные тени.\nВЫКЛ = без теней (небольшой прирост FPS). "));

                list.add(new HeaderEntry("EMBEDDIUM "));
                if (qualityObj != null) {
                    addIfPresent(list, qualityObj, "weatherQuality", () -> new EnumEntry(qualityObj, "weatherQuality", "ПОГОДА ", new String[]{"ДЕФОЛТ ", "КРАСИВО ", "БЫСТРО "}, "Качество эффектов дождя и снега.\nПО УМОЛЧАНИЮ = стандарт Minecraft.\nКРАСИВО = улучшенные эффекты.\nБЫСТРО = упрощённая погода. "));
                    addIfPresent(list, qualityObj, "leavesQuality", () -> new EnumEntry(qualityObj, "leavesQuality", "ЛИСТВА ", new String[]{"ДЕФОЛТ ", "КРАСИВО ", "БЫСТРО "}, "Качество отображения листвы.\nПО УМОЛЧАНИЮ = стандарт.\nКРАСИВО = прозрачная листва.\nБЫСТРО = непрозрачная листва (быстрее). "));
                    addIfPresent(list, qualityObj, "enableVignette", () -> new BoolEntry(qualityObj, "enableVignette", "ВИНЬЕТКА ", true, "Виньетка (затемнение по краям экрана).\nВКЛ = кинематографичный эффект.\nВЫКЛ = равномерная яркость. "));
                    addIfPresent(list, qualityObj, "enableFog", () -> new BoolEntry(qualityObj, "enableFog", "ТУМАН ", true, "Туман в игре.\nВКЛ = стандартный туман (скрывает дальние чанки).\nВЫКЛ = нет тумана (видно дальше, но может быть неестественно). "));
                    addIfPresent(list, qualityObj, "enableClouds", () -> new BoolEntry(qualityObj, "enableClouds", "ОБЛАКА ", true, "Облака в Embeddium.\nВКЛ = отображаются облака.\nВЫКЛ = облака отключены.\nДублирует настройку Minecraft. "));

                    // НОВАЯ НАСТРОЙКА: Дальность сущностей Embeddium (Float)
                    addIfPresent(list, qualityObj, "entityDistance", () -> new FloatEntry(qualityObj, "entityDistance", "ДАЛЬНОСТЬ СУЩНОСТЕЙ (EMBEDDIUM) ", 0.5f, 5.0f, v -> (int)(v * 100) + "% ", "Дальность прорисовки сущностей в Embeddium.\n50% = видны только вблизи.\n100% = стандартно.\n500% = очень далеко (нагрузка на GPU).\nПерезаписывает стандартную настройку Minecraft. "));
                }
            }
            case TAB_PERFORMANCE -> {
                list.add(new HeaderEntry("ЧАНКИ "));
                if (performanceObj != null) {
                    addIfPresent(list, performanceObj, "chunkBuilderThreads", () -> new IntEntry(performanceObj, "chunkBuilderThreads", "ПОТОКИ ЧАНКОВ ", 0, 8, v -> v == 0 ? "АВТО " : v + " ", "Количество потоков для построения чанков.\n0 = автоматически (по количеству ядер CPU).\n1-8 = фиксированное число.\nБольше потоков = быстрее загрузка, но выше нагрузка на CPU. "));
                    addIfPresent(list, performanceObj, "alwaysDeferChunkUpdates", () -> new BoolEntry(performanceObj, "alwaysDeferChunkUpdates", "ОТКЛАДЫВАТЬ ОБНОВЛЕНИЯ ", true, "Всегда откладывать обновление чанков.\nВКЛ = чанки обновляются постепенно (плавнее).\nВЫКЛ = мгновенные обновления (может вызывать лаги). "));
                    addIfPresent(list, performanceObj, "useChunkFreeList", () -> new BoolEntry(performanceObj, "useChunkFreeList", "ПЕРЕИСПОЛЬЗОВАНИЕ ПАМЯТИ ", true, "Использовать список свободных чанков.\nВКЛ = переиспользование памяти (быстрее, меньше лагов).\nВЫКЛ = стандартное выделение памяти. "));

                    list.add(new HeaderEntry("ОТСЕЧЕНИЕ "));
                    addIfPresent(list, performanceObj, "useBlockFaceCulling", () -> new BoolEntry(performanceObj, "useBlockFaceCulling", "ОТСЕЧЕНИЕ ГРАНЕЙ ", true, "Отсечение невидимых граней блоков.\nВКЛ = не рендерит грани, закрытые другими блоками.\nЗначительно повышает FPS (до 20-30%). "));
                    addIfPresent(list, performanceObj, "useEntityCulling", () -> new BoolEntry(performanceObj, "useEntityCulling", "ОТСЕЧЕНИЕ СУЩНОСТЕЙ ", true, "Отсечение невидимых сущностей.\nВКЛ = не рендерит мобов за стенами.\nПовышает FPS при большом количестве мобов. "));
                    addIfPresent(list, performanceObj, "useFogOcclusion", () -> new BoolEntry(performanceObj, "useFogOcclusion", "ОТСЕЧЕНИЕ ТУМАНОМ ", true, "Отсечение объектов в тумане.\nВКЛ = не рендерит то, что скрыто туманом.\nНебольшой прирост FPS. "));

                    list.add(new HeaderEntry("РЕНДЕР "));
                    addIfPresent(list, performanceObj, "animateOnlyVisibleTextures", () -> new BoolEntry(performanceObj, "animateOnlyVisibleTextures", "АНИМАЦИЯ ТОЛЬКО ВИДИМЫХ ", true, "Анимировать только видимые текстуры.\nВКЛ = анимирует воду/лаву только если они видны.\nЭкономит ресурсы GPU. "));
                    addIfPresent(list, performanceObj, "useCompactVertexFormat", () -> new BoolEntry(performanceObj, "useCompactVertexFormat", "КОМПАКТНЫЙ ФОРМАТ ", true, "Компактный формат вершин.\nВКЛ = уменьшает размер данных для GPU.\nПовышает производительность рендера. "));
                    addIfPresent(list, performanceObj, "useTranslucentFaceSorting", () -> new BoolEntry(performanceObj, "useTranslucentFaceSorting", "СОРТИРОВКА ПРОЗРАЧНЫХ ", true, "Сортировка полупрозрачных граней.\nВКЛ = правильное отображение прозрачных блоков.\nВЫКЛ = быстрее, но возможны артефакты. "));
                    addIfPresent(list, performanceObj, "useRenderPassOptimization", () -> new BoolEntry(performanceObj, "useRenderPassOptimization", "ОПТИМИЗАЦИЯ PASS'ОВ ", true, "Оптимизация проходов рендера.\nВКЛ = группирует похожие объекты для отрисовки.\nСнижает количество вызовов OpenGL. "));
                    addIfPresent(list, performanceObj, "precompileShaders", () -> new BoolEntry(performanceObj, "precompileShaders", "ПРЕДВАРИТЕЛЬНАЯ КОМПИЛЯЦИЯ ", false, "Предварительная компиляция шейдеров.\nВКЛ = компилирует шейдеры заранее (меньше лагов при загрузке).\nМожет увеличить время загрузки мира. "));
                    addIfPresent(list, performanceObj, "smoothFps", () -> new BoolEntry(performanceObj, "smoothFps", "ПЛАВНЫЙ FPS ", false, "Плавный FPS (Smooth FPS).\nВКЛ = сглаживает колебания FPS.\nМожет снизить нагрузку на CPU. "));

                    list.add(new HeaderEntry("ПАМЯТЬ "));
                    addIfPresent(list, performanceObj, "allowDirectMemoryAccess", () -> new BoolEntry(performanceObj, "allowDirectMemoryAccess", "ПРЯМОЙ ДОСТУП К ПАМЯТИ ", true, "Прямой доступ к памяти (DMA).\nВКЛ = быстрее работа с памятью.\nВЫКЛ = безопаснее, но медленнее. "));
                }
            }
            case TAB_ADVANCED -> {
                list.add(new HeaderEntry("CPU "));
                if (advancedObj != null) {
                    addIfPresent(list, advancedObj, "cpuRenderAheadLimit", () -> new IntEntry(advancedObj, "cpuRenderAheadLimit", "ЗАПАС КАДРОВ CPU ", 1, 9, v -> v + " к/с ", "Запас кадров CPU.\n1-9 = сколько кадров CPU готовит заранее.\nВысокие значения = плавнее, но больше задержка ввода.\nНизкие значения = меньше задержка, но возможны микролаги. "));
                    addIfPresent(list, advancedObj, "useAdvancedStagingBuffers", () -> new BoolEntry(advancedObj, "useAdvancedStagingBuffers", "ПРОДВИНУТЫЕ БУФЕРЫ ", true, "Продвинутые staging-буферы.\nВКЛ = улучшенная работа с памятью.\nПовышает производительность на некоторых системах. "));

                    list.add(new HeaderEntry("СОВМЕСТИМОСТЬ "));
                    addIfPresent(list, advancedObj, "disableIncompatibleModWarnings", () -> new BoolEntry(advancedObj, "disableIncompatibleModWarnings", "ОТКЛЮЧИТЬ ПРЕДУПРЕЖДЕНИЯ ", false, "Отключить предупреждения о несовместимости.\nВКЛ = не показывает предупреждения о конфликтах модов.\nНе рекомендуется! "));
                    addIfPresent(list, advancedObj, "disableDriverBlacklist", () -> new BoolEntry(advancedObj, "disableDriverBlacklist", "ОТКЛЮЧИТЬ ЧЁРНЫЙ СПИСОК ", false, "Отключить чёрный список драйверов.\nВКЛ = позволяет запускать с устаревшими драйверами.\nМожет вызвать нестабильность! "));

                    // НОВАЯ НАСТРОЙКА: GL Context NoError
                    addIfPresent(list, advancedObj, "useNoErrorGLContext", () -> new BoolEntry(advancedObj, "useNoErrorGLContext", "БЕЗ ОШИБОК GL КОНТЕКСТ ", true, "Использовать GL контекст без ошибок (NoError).\nВКЛ = отключает проверки ошибок OpenGL на уровне драйвера.\nЗначительно повышает производительность.\nВЫКЛ = стандартные проверки (безопаснее, но медленнее). "));

                    list.add(new HeaderEntry("ОТЛАДКА "));
                    addIfPresent(list, advancedObj, "useGLDebug", () -> new BoolEntry(advancedObj, "useGLDebug", "GL DEBUG ", false, "Отладочный контекст OpenGL.\nВКЛ = выводит подробную информацию об ошибках GL.\nТолько для разработки! Сильно снижает FPS. "));
                    addIfPresent(list, advancedObj, "checkGLErrors", () -> new BoolEntry(advancedObj, "checkGLErrors", "ПРОВЕРКА ОШИБОК GL ", false, "Проверка ошибок OpenGL.\nВКЛ = проверяет каждую операцию GL на ошибки.\nТолько для отладки! Сильно снижает производительность. "));
                }
            }
            case TAB_NOTIFICATIONS -> {
                list.add(new HeaderEntry("ДОНАТЫ "));
                if (notificationsObj != null) {
                    addIfPresent(list, notificationsObj, "forceDisableDonationPrompts", () -> new BoolEntry(notificationsObj, "forceDisableDonationPrompts", "ОТКЛЮЧИТЬ ДОНАТЫ ", false, "Полностью отключить просьбы о пожертвованиях.\nВКЛ = никогда не показывать уведомления.\nВЫКЛ = показывать время от времени. "));
                    addIfPresent(list, notificationsObj, "hasClearedDonationButton", () -> new BoolEntry(notificationsObj, "hasClearedDonationButton", "СКРЫТЬ КНОПКУ ДОНАТОВ ", false, "Скрыть кнопку пожертвований.\nВКЛ = кнопка не отображается.\nВЫКЛ = кнопка видна в меню. "));
                    addIfPresent(list, notificationsObj, "hideDonationButton", () -> new BoolEntry(notificationsObj, "hideDonationButton", "СКРЫТЬ КНОПКУ (ALT) ", false, "Альтернативное скрытие кнопки пожертвований.\nВКЛ = скрыть кнопку.\nВЫКЛ = показать кнопку. "));
                    addIfPresent(list, notificationsObj, "hideUnsupportedMemoryChecker", () -> new BoolEntry(notificationsObj, "hideUnsupportedMemoryChecker", "СКРЫТЬ ПРЕДУПРЕЖДЕНИЯ О ПАМЯТИ ", false, "Скрыть предупреждение о неподдерживаемой памяти.\nВКЛ = не показывать предупреждения.\nВЫКЛ = показывать, если память не оптимальна. "));
                }
            }
        }
        return list;
    }

    private void addIfPresent(List<SettingEntry> list, Object section, String fieldName, java.util.function.Supplier<SettingEntry> factory) {
        if (fieldExists(section, fieldName)) {
            try { list.add(factory.get()); } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void init() {
        super.init();
        rebuildUi();
    }

    private void rebuildUi() {
        this.clearWidgets();

        if (!available) {
            addRenderableWidget(PaperWidgets.paperButton(this.width / 2 - 150, this.height / 2, 300, 30, Component.literal("EMBEDDIUM НЕ НАЙДЕН "), b -> this.minecraft.setScreen(parent), 0L, PaperRender.INK_RED, null));
            return;
        }

        actualCardH = Math.min(CARD_H, (int)(this.height * 0.65));

        // Пересчитываем общую высоту: высота карточки + отступ + высота одного ряда кнопок
        int buttonsBlockHeight = BTN_H;
        int totalHeight = actualCardH + 15 + buttonsBlockHeight;

        // Центрируем весь блок (карточка + кнопки) по вертикали
        this.cardY = Math.max(55, (this.height - totalHeight) / 2 + 5);

        int cx = this.width / 2;
        int totalW = TABS_W + SLIDER_W + CARD_W;
        int startX = cx - totalW / 2;
        int tabsX = startX;

        for (int i = 0; i < 5; i++) {
            final int t = i;
            int accent = this.tab == t ? PaperRender.INK_RED : PaperRender.INK_SOFT;
            int ty = this.cardY + i * (TAB_H + TAB_GAP);
            addRenderableWidget(PaperWidgets.paperButton(tabsX, ty, TABS_W, TAB_H, Component.literal(TAB_LABELS[i]), b -> {
                if (this.tab != t) { this.tab = t; this.scrollOffset = 0f; rebuildUi(); }
            }, 0L, accent, null));
        }

        int cardX = startX + TABS_W + SLIDER_W;

        // Горизонтальные кнопки по центру под карточкой
        // 2. Новые координаты для кнопок: 4 кнопки в ряд, по центру под карточкой
        int btnW = 95; // Ширина одной кнопки (уменьшили, чтобы влезло 4)
        int totalBtnW = btnW * 4 + BTN_GAP * 3; // Общая ширина блока из 4 кнопок
        int buttonsX = cardX + (CARD_W - totalBtnW) / 2; // Центрируем относительно карточки
        int buttonsY = this.cardY + actualCardH + 15; // Отступ 15 пикселей снизу от карточки

        addRenderableWidget(PaperWidgets.paperButton(buttonsX, buttonsY, btnW, BTN_H, Component.literal(" <- НАЗАД "), b -> this.minecraft.setScreen(parent), 0L, PaperRender.INK_SOFT, null));
        addRenderableWidget(PaperWidgets.paperButton(buttonsX + btnW + BTN_GAP, buttonsY, btnW, BTN_H, Component.literal("СБРОС "), b -> { resetOptions(); rebuildUi(); }, 0L, PaperRender.INK_FADED, null));

        // НОВАЯ КНОПКА: ПЕРЕХОД В ШЕЙДЕРЫ
        addRenderableWidget(PaperWidgets.paperButton(buttonsX + (btnW + BTN_GAP) * 2, buttonsY, btnW, BTN_H, Component.literal("ШЕЙДЕРЫ  "), b -> {
            try {
                // Открываем ОРИГИНАЛЬНОЕ меню шейдеров Iris/Oculus через рефлексию
                // Используем ПРАВИЛЬНЫЙ путь net.irisshaders.iris
                Class<?> shaderScreenClass = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
                java.lang.reflect.Constructor<?> constructor = shaderScreenClass.getDeclaredConstructor(Screen.class);
                constructor.setAccessible(true);
                Screen irisScreen = (Screen) constructor.newInstance(this);
                this.minecraft.setScreen(irisScreen);
            } catch (Exception e) {
                e.printStackTrace();
                // Если мод не найден, можно вывести сообщение или ничего не делать
            }
        }, 0L, PaperRender.INK, null));

        addRenderableWidget(PaperWidgets.paperButton(buttonsX + (btnW + BTN_GAP) * 3, buttonsY, btnW, BTN_H, Component.literal("СОХРАНИТЬ "), b -> { saveOptions(); this.minecraft.setScreen(parent); }, 0L, PaperRender.INK_RED, null));

        int contentX = cardX + PADDING;
        int contentW = CARD_W - PADDING * 2;
        int contentY = this.cardY + HEADER_H;
        int contentH = actualCardH - HEADER_H - PADDING;

        List<SettingEntry> entries = getEntriesForTab(tab);
        float totalH = 0f;
        for (SettingEntry e : entries) totalH += e.height() + 4f;

        maxScroll = Math.max(0f, totalH - contentH);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        float y = contentY - scrollOffset;
        for (SettingEntry e : entries) {
            float h = e.height();
            if (y + h > contentY && y < contentY + contentH) {
                addRenderableWidget(e.createWidget(contentX, (int) y, contentW));
            }
            y += h + 4f;
        }
    }

    private void resetOptions() {
        try {
            if (optionsInstance == null) return;
            Class<?> optionsClass = optionsInstance.getClass();
            Constructor<?> ctor = optionsClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object defaults = ctor.newInstance();
            for (Field f : optionsClass.getDeclaredFields()) {
                f.setAccessible(true);
                try { f.set(optionsInstance, f.get(defaults)); } catch (Throwable ignored) {}
            }
            qualityObj = getField(optionsInstance, "quality");
            performanceObj = getField(optionsInstance, "performance");
            advancedObj = getField(optionsInstance, "advanced");
            notificationsObj = getField(optionsInstance, "notifications");

            Options o = Minecraft.getInstance().options;
            o.graphicsMode().set(GraphicsStatus.FANCY);
            o.renderDistance().set(12);
            o.simulationDistance().set(12);
            o.particles().set(ParticleStatus.ALL);
            o.ambientOcclusion().set(true);
            o.biomeBlendRadius().set(2);
            o.entityDistanceScaling().set(1.0);
            o.entityShadows().set(true);
            o.cloudStatus().set(CloudStatus.FANCY);
            o.fullscreen().set(false);
            o.guiScale().set(0);
            o.framerateLimit().set(120);
            o.enableVsync().set(true);
            o.bobView().set(true);
            o.gamma().set(0.5);
            o.attackIndicator().set(AttackIndicatorStatus.HOTBAR);
            o.mipmapLevels().set(4);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    private void saveOptions() {
        try {
            Minecraft.getInstance().options.save();
            if (optionsInstance != null) {
                try { optionsInstance.getClass().getMethod("writeChanges").invoke(optionsInstance); }
                catch (NoSuchMethodException e) {
                    try { optionsInstance.getClass().getMethod("save").invoke(optionsInstance); }
                    catch (NoSuchMethodException ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int cx = this.width / 2;
        int totalW = TABS_W + SLIDER_W + CARD_W;
        int cardX = cx - totalW / 2 + TABS_W + SLIDER_W;

        if (mouseX >= cardX && mouseX <= cardX + CARD_W && mouseY >= this.cardY && mouseY <= this.cardY + actualCardH) {
            float oldScroll = scrollOffset;
            scrollOffset = Math.max(0f, Math.min(maxScroll, scrollOffset - (float) delta * 18f));
            if (scrollOffset != oldScroll) rebuildUi();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingThumb && button == 0) {
            int cx = this.width / 2;
            int totalW = TABS_W + SLIDER_W + CARD_W;
            int cardX = cx - totalW / 2 + TABS_W + SLIDER_W;
            int contentY = this.cardY + HEADER_H;
            int contentH = actualCardH - HEADER_H - PADDING;

            float trackH = contentH;
            float thumbH = Math.max(20f, trackH * (contentH / (contentH + maxScroll)));
            float usableH = trackH - thumbH;

            if (usableH > 0) {
                float dy = (float) (mouseY - dragStartY);
                float newScroll = dragStartScroll + (dy / usableH) * maxScroll;
                scrollOffset = Math.max(0f, Math.min(maxScroll, newScroll));
                rebuildUi();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean result = super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && maxScroll > 0) {
            int cx = this.width / 2;
            int totalW = TABS_W + SLIDER_W + CARD_W;
            int cardX = cx - totalW / 2 + TABS_W + SLIDER_W;
            int contentY = this.cardY + HEADER_H;
            int contentH = actualCardH - HEADER_H - PADDING;

            int sliderX = cardX - SLIDER_W;
            if (mouseX >= sliderX && mouseX <= sliderX + SLIDER_W && mouseY >= contentY && mouseY <= contentY + contentH) {
                float trackH = contentH;
                float thumbH = Math.max(20f, trackH * (contentH / (contentH + maxScroll)));
                float thumbY = contentY + (contentH - thumbH) * (scrollOffset / maxScroll);

                if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                    draggingThumb = true;
                    dragStartY = (float) mouseY;
                    dragStartScroll = scrollOffset;
                    return true;
                } else {
                    float ratio = (float) (mouseY - contentY - thumbH / 2) / (contentH - thumbH);
                    scrollOffset = Math.max(0f, Math.min(maxScroll, ratio * maxScroll));
                    rebuildUi();
                    return true;
                }
            }
        }
        return result;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingThumb) { draggingThumb = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        PaperRender.drawBoardBackground(gfx, this.width, this.height);
        currentTooltip = null;

        // 1. Фоновые атмосферные детали
        renderBackgroundDetails(gfx);

        Font font = this.font;
        String kicker = "ФАЙЛ №04 · ДВИЖОК ";
        int kw = font.width(kicker);
        gfx.drawString(font, kicker, this.width / 2 - kw / 2, 20, 0xFFB8A581, false);

        String title = "ПРОДВИНУТЫЕ НАСТРОЙКИ ";
        float ts = 1.5f;
        int tw = (int) (font.width(title) * ts);
        gfx.pose().pushPose();
        gfx.pose().translate(this.width / 2f - tw / 2f, 30, 0);
        gfx.pose().scale(ts, ts, 1f);
        gfx.drawString(font, title, 0, 0, PaperRender.PAPER_LIGHT, false);
        gfx.pose().popPose();

        if (!available) {
            super.render(gfx, mouseX, mouseY, partialTick);
            return;
        }

        int cx = this.width / 2;
        int totalW = TABS_W + SLIDER_W + CARD_W;
        int startX = cx - totalW / 2;
        int tabsX = startX;
        int cardX = startX + TABS_W + SLIDER_W;

        // 2. СНАЧАЛА рисуем ГЛАВНЫЙ ЛИСТОК (он будет ПОД настройками)
        renderMainCard(gfx, cardX, this.cardY, actualCardH);

        // 3. Рисуем базовые виджеты (настройки, кнопки) ПОВЕРХ листка
        super.render(gfx, mouseX, mouseY, partialTick);

        // 4. Рисуем ОФОРМЛЕНИЕ ВКЛАДОК ПОВЕРХ всего (красивые бумажки с гвоздиками)
        renderTabsDecor(gfx, tabsX, this.cardY);

        // 5. Скроллбар и разделитель
        if (maxScroll > 0) renderScrollbar(gfx, cardX, this.cardY, actualCardH);

        int dividerY = this.cardY + actualCardH + 6;
        PaperRender.drawHandDivider(gfx, cardX, dividerY, CARD_W, PaperRender.withAlpha(PaperRender.INK_FADED, 0.4f));

        // Поиск подсказки через TooltipWrapper
        for (var widget : this.renderables) {
            if (widget instanceof TooltipWrapper ttw && ttw.isMouseOver(mouseX, mouseY)) {
                currentTooltip = ttw.getTooltipText();
                tooltipMouseX = mouseX;
                tooltipMouseY = mouseY;
                break;
            }
        }

        if (currentTooltip != null && !currentTooltip.isEmpty()) {
            renderTooltip(gfx, currentTooltip, tooltipMouseX, tooltipMouseY);
        }
    }

    // ===== БУМАЖНЫЙ СТИЛЬ TOOLTIP =====
    private void renderTooltip(GuiGraphics gfx, String text, int mouseX, int mouseY) {
        Font font = this.font;
        String[] lines = text.split("\n");
        int maxWidth = 0;
        for (String line : lines) {
            int w = font.width(line);
            if (w > maxWidth) maxWidth = w;
        }

        int padding = 8;
        int tooltipWidth = maxWidth + padding * 2;
        int tooltipHeight = lines.length * 10 + padding * 2;

        int x = mouseX + 12;
        int y = mouseY + 12;

        if (x + tooltipWidth > this.width) x = mouseX - tooltipWidth - 12;
        if (y + tooltipHeight > this.height) y = mouseY - tooltipHeight - 12;

        gfx.pose().pushPose();
        gfx.pose().translate(x + tooltipWidth / 2f, y + tooltipHeight / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.3f));
        gfx.pose().translate(-tooltipWidth / 2f, -tooltipHeight / 2f, 0);

        PaperRender.drawPaperCard(gfx, 0, 0, tooltipWidth, tooltipHeight, 0.8f, PaperRender.PAPER_LIGHT);
        PaperRender.drawPin(gfx, tooltipWidth - 8, 4, true);

        for (int i = 0; i < lines.length; i++) {
            gfx.drawString(font, lines[i], padding, padding + i * 10, PaperRender.INK, false);
        }

        gfx.pose().popPose();
    }

    private void renderTabsDecor(GuiGraphics gfx, int x, int y) {
        for (int i = 0; i < 5; i++) {
            int ty = y + i * (TAB_H + TAB_GAP);
            boolean selected = this.tab == i;

            gfx.pose().pushPose();
            gfx.pose().translate(x + TABS_W / 2f, ty + TAB_H / 2f, 0);
            gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.3f));
            gfx.pose().translate(-TABS_W / 2f, -TAB_H / 2f, 0);

            int paper = selected ? PaperRender.PAPER_DARK : PaperRender.PAPER_LIGHT;
            PaperRender.drawPaperCard(gfx, 0, 0, TABS_W, TAB_H, 1.0f, paper);

            if (selected) {
                gfx.fill(0, 0, 4, TAB_H, PaperRender.INK_RED);
            }

            Font font = this.font;
            gfx.drawString(font, TAB_LABELS[i], 10, 8, selected ? PaperRender.INK : PaperRender.INK_FADED, false);
            gfx.drawString(font, TAB_SUBTITLES[i], 10, 22, PaperRender.withAlpha(PaperRender.INK_SOFT, 0.6f), false);

            if (selected) {
                PaperRender.drawPin(gfx, TABS_W - 12, 8, true);
            }

            gfx.pose().popPose();
        }
    }

    private void renderMainCard(GuiGraphics gfx, int x, int y, int h) {
        gfx.pose().pushPose();
        gfx.pose().translate(x + CARD_W / 2f, y + h / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.5f));
        gfx.pose().translate(-CARD_W / 2f, -h / 2f, 0);

        PaperRender.drawPaperCard(gfx, 0, 0, CARD_W, h, 1.0f, PaperRender.PAPER_LIGHT);

        // Скотч сверху по центру
        PaperRender.drawTape(gfx, (CARD_W / 2) - 35, -6, 70, 14, 0x90);

        PaperRender.drawPin(gfx, 16, 8, false);
        PaperRender.drawPin(gfx, CARD_W - 16, 8, true);

        Font font = this.font;
        String head = "§ " + (tab + 1) + " · " + TAB_LABELS[tab];
        gfx.drawString(font, head, 16, 8, PaperRender.INK_FADED, false);

        float hs = 1.25f;
        gfx.pose().pushPose();
        gfx.pose().translate(16, 16, 0);
        gfx.pose().scale(hs, hs, 1f);
        gfx.drawString(font, TAB_SUBTITLES[tab], 0, 0, PaperRender.INK, false);
        gfx.pose().popPose();

        PaperRender.drawHandDivider(gfx, 16, 16 + (int)(9 * hs) + 4, CARD_W - 32, PaperRender.withAlpha(PaperRender.INK_SOFT, 0.7f));
        gfx.pose().popPose();
    }

    private void renderScrollbar(GuiGraphics gfx, int cardX, int cardY, int h) {
        int contentY = cardY + HEADER_H;
        int contentH = h - HEADER_H - PADDING;
        int sliderX = cardX - SLIDER_W;

        gfx.fill(sliderX, contentY, sliderX + SLIDER_W, contentY + contentH, PaperRender.withAlpha(PaperRender.INK_FADED, 0.3f));

        float trackH = contentH;
        float thumbH = Math.max(20f, trackH * (contentH / (contentH + maxScroll)));
        float thumbY = contentY + (contentH - thumbH) * (scrollOffset / maxScroll);

        gfx.fill(sliderX, (int) thumbY, sliderX + SLIDER_W, (int) (thumbY + thumbH), PaperRender.INK_RED);
        gfx.fill(sliderX + 2, (int) thumbY + 2, sliderX + SLIDER_W - 2, (int) (thumbY + thumbH - 2), PaperRender.withAlpha(PaperRender.INK_RED, 0.7f));
    }

    private void renderBackgroundDetails(GuiGraphics gfx) {
        long gt = PaperRender.gameTime();

        // Пометка справа вверху
        gfx.pose().pushPose();
        gfx.pose().translate(this.width - 140, 50, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(15f));
        PaperRender.drawScribble(gfx, this.font, "не трогать", 0, 0, PaperRender.withAlpha(PaperRender.INK_RED, 0.35f));
        gfx.pose().popPose();

        // Пометка слева внизу
        gfx.pose().pushPose();
        gfx.pose().translate(50, this.height - 70, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-7f));
        PaperRender.drawScribble(gfx, this.font, "проверено отделом", 0, 0, PaperRender.withAlpha(PaperRender.INK_FADED, 0.45f));
        gfx.pose().popPose();

        // Маленький пульсирующий штамп рядом с заголовком
        gfx.pose().pushPose();
        gfx.pose().translate(this.width / 2f + 150, 45, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(12f));
        float pulse = 0.7f + 0.3f * Mth.sin((gt + 0f) * 0.03f);
        PaperRender.drawRoundStamp(gfx, this.font, 0, 0, 26, "ПРОВЕРЕНО", "ОТДЕЛ", PaperRender.withAlpha(PaperRender.INK_RED, 0.25f * pulse));
        gfx.pose().popPose();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}