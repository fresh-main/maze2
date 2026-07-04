package com.otbor.client.widgets;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Фабрика для всех paper-виджетов.
 *
 * Раньше использовалась reflection-загрузка через {@code Class.forName} с явным ClassLoader,
 * но в Forge {@code ModuleClassLoader} 47.4.x при некоторых условиях это начало валиться с
 * {@code ClassNotFoundException: com.otbor.client.widgets.PaperSlider} даже при наличии
 * класса в jar. Поскольку все widget-классы находятся в ОДНОМ пакете с этой фабрикой —
 * cross-package limitation не применяется, и достаточно прямого {@code new PaperX(...)}.
 *
 * Фабрика остаётся: вызывающие экраны не должны напрямую импортировать widget-классы,
 * чтобы порядок инициализации client-only widget'ов не зависел от того, какой экран первым
 * загрузится.
 */
public final class PaperWidgets {

    private PaperWidgets() {}

    public static AbstractWidget paperButton(int x, int y, int w, int h,
                                             Component msg, Button.OnPress onPress,
                                             long delayMs,
                                             Integer accent, Float rotationDeg) {
        PaperButton btn = new PaperButton(x, y, w, h, msg, onPress, delayMs);
        if (accent != null) btn.withAccent(accent);
        if (rotationDeg != null) btn.withRotation(rotationDeg);
        return btn;
    }

    public static AbstractWidget paperButton(int x, int y, int w, int h,
                                             Component msg, Button.OnPress onPress) {
        return new PaperButton(x, y, w, h, msg, onPress, 0L);
    }

    public static AbstractWidget paperSlider(int x, int y, int w, int h,
                                             String title, double initialNormalized,
                                             DoubleConsumer setter,
                                             DoubleFunction<String> labeler) {
        return new PaperSlider(x, y, w, h, title, initialNormalized, setter, labeler);
    }

    /** Deferred-режим: setter вызовется ТОЛЬКО на onRelease (mouseUp). Для тяжёлых
     *  настроек (renderDistance, graphicsMode, framerate и т.п.) — без этого их
     *  setter дёргается на каждом пикселе drag'а и Minecraft перегружает чанки
     *  десятки раз подряд → клиент подвисает. */
    public static AbstractWidget paperSliderDeferred(int x, int y, int w, int h,
                                                     String title, double initialNormalized,
                                                     DoubleConsumer setter,
                                                     DoubleFunction<String> labeler) {
        return new PaperSlider(x, y, w, h, title, initialNormalized, setter, labeler, true);
    }

    public static AbstractWidget paperCheckbox(int x, int y, int w, int h,
                                               String label,
                                               BooleanSupplier getter,
                                               Consumer<Boolean> setter) {
        return new PaperCheckbox(x, y, w, h, label, getter, setter);
    }

    /** Варианты бумаги для {@link com.otbor.client.widgets.NoteCard.PaperVariant}. */
    public enum NoteCardVariant { BASE, LIGHT, DARK }

    public static AbstractWidget noteCard(int x, int y, int w, int h,
                                          int index,
                                          Component title, String subtitle, String sub2,
                                          String iconKey,
                                          NoteCardVariant variant,
                                          boolean brassPin,
                                          float baseRotationDeg,
                                          long appearDelayMs,
                                          Button.OnPress onPress) {
        NoteCard.PaperVariant v = switch (variant) {
            case BASE  -> NoteCard.PaperVariant.BASE;
            case LIGHT -> NoteCard.PaperVariant.LIGHT;
            case DARK  -> NoteCard.PaperVariant.DARK;
        };
        return new NoteCard(x, y, w, h, index, title, subtitle, sub2, iconKey,
                v, brassPin, baseRotationDeg, appearDelayMs, onPress);
    }

    public static AbstractWidget paperSegment(int x, int y, int w, int h,
                                              String[] options,
                                              IntSupplier getter,
                                              IntConsumer setter) {
        return new PaperSegment(x, y, w, h, options, getter, setter);
    }
}
