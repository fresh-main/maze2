package com.otbor.client;

import com.otbor.client.widgets.PaperContainerRender;
import com.otbor.client.widgets.PaperRender;
import com.otbor.inventory.LockedSlot;
import com.labyrinthmod.client.mixin.ContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.inventory.Slot;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;

public final class BackpackScreenOverlay {

    private BackpackScreenOverlay() {}

    /**
     * Раньше определяли SB-экран по наличию LockedSlot, но AbstractContainerMenuMixin
     * теперь ставит LockedSlot ВО ВСЕХ контейнерах (сундук, печь, верстак), и они
     * ошибочно подписывались как «РЮКЗАК · ОПИСЬ ИМУЩЕСТВА».
     *
     * Поэтому проверяем по класс-имени экрана: SB и SC живут в
     * net.p3pp3rf1y.sophisticated{backpacks,core}.* — этого достаточно для отделения
     * настоящих рюкзаков от ванильных контейнеров.
     */
    public static boolean isBackpackScreen(AbstractContainerScreen<?> screen) {
        if (screen instanceof InventoryScreen) return false;
        if (screen instanceof CreativeModeInventoryScreen) return false;
        String cls = screen.getClass().getName();
        if (cls.contains("sophisticatedbackpacks") || cls.contains("sophisticatedcore")
                || cls.contains("sophisticatedstorage")) {
            return true;
        }
        return false;
    }

    public static boolean isVanillaPapered(AbstractContainerScreen<?> screen) {
        if (screen instanceof InventoryScreen) return false;
        if (screen instanceof CreativeModeInventoryScreen) return false;
        return screen instanceof AbstractFurnaceScreen
                || screen instanceof CraftingScreen
                || screen instanceof ContainerScreen
                || screen instanceof ShulkerBoxScreen
                || screen instanceof HopperScreen;
    }

    private static String kickerFor(AbstractContainerScreen<?> screen) {
        if (screen instanceof AbstractFurnaceScreen) return "ОЧАГ · ОБРАБОТКА";
        if (screen instanceof CraftingScreen) return "ВЕРСТАК · СБОРКА";
        if (screen instanceof ContainerScreen) return "СУНДУК · ОПИСЬ";
        if (screen instanceof ShulkerBoxScreen) return "ЯЩИК · СОДЕРЖИМОЕ";
        if (screen instanceof HopperScreen) return "ВОРОНКА · ПОТОК";
        return "КОНТЕЙНЕР";
    }

    private static String stampFor(AbstractContainerScreen<?> screen) {
        if (screen instanceof AbstractFurnaceScreen) return "ОЧАГ";
        if (screen instanceof CraftingScreen) return "СБОРКА";
        if (screen instanceof ContainerScreen) return "ОПИСЬ";
        if (screen instanceof ShulkerBoxScreen) return "ЯЩИК";
        if (screen instanceof HopperScreen) return "ПОТОК";
        return "УЧТЕНО";
    }

    private static final int SIDE_EXTEND = 0;

    /**
     * Расширение бумаги вниз для backpack-screen'ов (SB).
     * После наших правок SC (HEIGHT_WITHOUT_STORAGE_SLOTS=60 + StorageScreenBaseMixin)
     * player-inv area уже свёрнут до одного хотбара ВНУТРИ imageHeight, поэтому
     * лишний bleed больше не нужен — оставляем небольшой отступ под хотбаром.
     */
    private static final int BACKPACK_PLAYER_INV_BLEED = 4;

    /**
     * Расширение бумаги для vanilla-контейнеров. ChestScreenMixin отключает второй blit,
     * остальные vanilla-контейнеры делают один blit на весь imageHeight (после нашей
     * компакции — без player-inv area). Поэтому достаточно лёгкого «хвоста» под хотбар.
     */
    private static final int VANILLA_PLAYER_INV_BLEED = 4;

    public static void drawFullPaperBackground(GuiGraphics gfx, AbstractContainerScreen<?> screen) {
        if (isBackpackScreen(screen)) {
            drawBackpack(gfx, screen);
            return;
        }
        if (isVanillaPapered(screen)) {
            drawVanillaContainer(gfx, screen);
        }
    }
    /**
     * Отрисовка боковых табов апгрейдов (UpgradeSettingsTabControl) в бумажном стиле.
     * Работает через reflection, чтобы не зависеть от внутренней структуры Sophisticated Core.
     */


    private static void drawBackpack(GuiGraphics gfx, AbstractContainerScreen<?> screen) {
        int leftPos = screen.getGuiLeft();
        int topPos = screen.getGuiTop();
        int iw = ((ContainerScreenAccessor) screen).otbor$getImageWidth();
        int ih = ((ContainerScreenAccessor) screen).otbor$getImageHeight();
        int fullH = ih + BACKPACK_PLAYER_INV_BLEED;

        float t = PaperContainerRender.animProgress(screen);

        int fullX = leftPos - SIDE_EXTEND;
        int fullW = iw + SIDE_EXTEND * 2;
        PaperRender.drawPaperCard(gfx, fullX, topPos, fullW, fullH, 1.0f, PaperRender.PAPER_LIGHT);

        // Перо обводит лист
        float borderTrace = PaperContainerRender.phase(t, 0.10f, 0.42f);
        if (borderTrace > 0f && borderTrace < 1f) {
            PaperContainerRender.tracePerimeter(gfx, fullX + 4, topPos + 4,
                    fullW - 8, fullH - 8, borderTrace, PaperRender.INK, 1);
        }

        if (t > 0.18f) {
            PaperRender.drawPin(gfx, fullX + 10, topPos + 4, false);
            PaperRender.drawPin(gfx, fullX + fullW - 10, topPos + 4, true);
        }

        var font = Minecraft.getInstance().font;

        // Header — typewriter
        float headerProg = PaperContainerRender.phase(t, 0.22f, 0.50f);
        if (headerProg > 0f) {
            String head = "РЮКЗАК · ОПИСЬ ИМУЩЕСТВА";
            int headW = font.width(head);
            PaperContainerRender.typewriter(gfx, font, head,
                    fullX + fullW / 2 - headW / 2, topPos - 14,
                    PaperRender.INK_FADED, headerProg);
        }

        // Слот-рамки скетчатся каскадом
        Slot[] sortedSlots = screen.getMenu().slots.stream()
                .filter(s -> s.isActive() && !(s instanceof LockedSlot) && s.x >= 0 && s.y >= 0)
                .sorted((a, b) -> {
                    int dy = Integer.compare(a.y, b.y);
                    return dy != 0 ? dy : Integer.compare(a.x, b.x);
                })
                .toArray(Slot[]::new);
        float slotsStart = 0.40f;
        float slotsEnd = 0.92f;
        float slotSpacing = sortedSlots.length == 0 ? 0f
                : Math.min(0.04f, (slotsEnd - slotsStart) / sortedSlots.length);
        for (int i = 0; i < sortedSlots.length; i++) {
            Slot s = sortedSlots[i];
            float st = slotsStart + i * slotSpacing;
            float local = PaperContainerRender.phase(t, st, st + 0.12f);
            PaperContainerRender.sketchSlotBox(gfx, leftPos + s.x, topPos + s.y, local);
        }

        // Штамп с overshoot-плюхой
        float stampProg = PaperContainerRender.phase(t, 0.78f, 0.92f);
        if (stampProg > 0f) {
            float ease = PaperContainerRender.easeBackOut(stampProg);
            float scale = 1.6f - 0.6f * ease;
            int alpha = (int) (255 * Math.min(1f, stampProg * 1.6f));
            int color = (alpha << 24) | (PaperRender.INK_RED & 0xFFFFFF);

            gfx.pose().pushPose();
            gfx.pose().translate(fullX + fullW - 36, topPos + fullH + 8, 0);
            gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-5f));
            gfx.pose().scale(scale, scale, 1f);
            PaperRender.drawRectStamp(gfx, font, "МОЯ СУМКА", 0, 0, color);
            gfx.pose().popPose();
        }
    }

    private static void drawVanillaContainer(GuiGraphics gfx, AbstractContainerScreen<?> screen) {
        int leftPos = screen.getGuiLeft();
        int topPos = screen.getGuiTop();
        int iw = ((ContainerScreenAccessor) screen).otbor$getImageWidth();
        int ih = ((ContainerScreenAccessor) screen).otbor$getImageHeight();
        int fullH = ih + VANILLA_PLAYER_INV_BLEED;

        float t = PaperContainerRender.animProgress(screen);

        PaperRender.drawPaperCard(gfx, leftPos, topPos, iw, fullH, 1.0f, PaperRender.PAPER_LIGHT);

        // Перо обводит лист
        float borderTrace = PaperContainerRender.phase(t, 0.10f, 0.42f);
        if (borderTrace > 0f && borderTrace < 1f) {
            PaperContainerRender.tracePerimeter(gfx, leftPos + 4, topPos + 4,
                    iw - 8, fullH - 8, borderTrace, PaperRender.INK, 1);
        }

        if (t > 0.18f) {
            PaperRender.drawPin(gfx, leftPos + 10, topPos + 6, false);
            PaperRender.drawPin(gfx, leftPos + iw - 10, topPos + 6, true);
        }

        var font = Minecraft.getInstance().font;
        String kicker = kickerFor(screen);

        // Kicker — typewriter
        float kickProg = PaperContainerRender.phase(t, 0.22f, 0.50f);
        if (kickProg > 0f) {
            int kw = font.width(kicker);
            PaperContainerRender.typewriter(gfx, font, kicker,
                    leftPos + iw / 2 - kw / 2, topPos - 12,
                    PaperRender.INK_FADED, kickProg);
        }

        // Слот-рамки скетчатся каскадом
        Slot[] sortedSlots = screen.getMenu().slots.stream()
                .filter(s -> s.isActive() && s.x >= 0 && s.y >= 0)
                .sorted((a, b) -> {
                    int dy = Integer.compare(a.y, b.y);
                    return dy != 0 ? dy : Integer.compare(a.x, b.x);
                })
                .toArray(Slot[]::new);
        float slotsStart = 0.40f;
        float slotsEnd = 0.92f;
        float slotSpacing = sortedSlots.length == 0 ? 0f
                : Math.min(0.04f, (slotsEnd - slotsStart) / sortedSlots.length);
        for (int i = 0; i < sortedSlots.length; i++) {
            Slot s = sortedSlots[i];
            float st = slotsStart + i * slotSpacing;
            float local = PaperContainerRender.phase(t, st, st + 0.12f);
            PaperContainerRender.sketchSlotBox(gfx, leftPos + s.x, topPos + s.y, local);
        }

        // Штамп с overshoot
        float stampProg = PaperContainerRender.phase(t, 0.78f, 0.92f);
        if (stampProg > 0f) {
            float ease = PaperContainerRender.easeBackOut(stampProg);
            float scale = 1.6f - 0.6f * ease;
            int alpha = (int) (255 * Math.min(1f, stampProg * 1.6f));
            int color = (alpha << 24) | (PaperRender.INK_RED & 0xFFFFFF);

            gfx.pose().pushPose();
            gfx.pose().translate(leftPos + iw - 30, topPos + fullH + 6, 0);
            gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-5f));
            gfx.pose().scale(scale, scale, 1f);
            PaperRender.drawRectStamp(gfx, font, stampFor(screen), 0, 0, color);
            gfx.pose().popPose();
        }
    }

    public static void drawRightSidePaperCover(GuiGraphics gfx, AbstractContainerScreen<?> screen) {
        if (!isBackpackScreen(screen)) return;
        int iw = ((ContainerScreenAccessor) screen).otbor$getImageWidth();
        int ih = ((ContainerScreenAccessor) screen).otbor$getImageHeight();
        PaperRender.drawPaperCard(gfx, iw, 0, SIDE_EXTEND, ih, 1.0f, PaperRender.PAPER_LIGHT);
    }

    public static void drawLockedCover(GuiGraphics gfx, AbstractContainerScreen<?> screen) {
    }
    /**
     * Универсальная отрисовка ВСЕХ табов (и левых апгрейдов, и правых настроек)
     * в бумажном стиле. Находим их через перебор children() экрана.
     */
    public static void drawPaperTabs(GuiGraphics gfx, AbstractContainerScreen<?> screen) {
        if (!isBackpackScreen(screen)) return;

        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof net.minecraft.client.gui.components.Renderable)) continue;

            String className = child.getClass().getName();
            // Ищем все виджеты, в имени класса которых есть "Tab"
            if (!className.contains("Tab")) continue;

            try {
                int x = getWidgetValue(child, "getX", "x");
                int y = getWidgetValue(child, "getY", "y");
                int w = getWidgetValue(child, "getWidth", "width");
                int h = getWidgetValue(child, "getHeight", "height");

                if (w <= 0 || h <= 0) continue;

                // 1. Затирем ванильную текстуру таба непрозрачным бумажным фоном
                gfx.fill(x, y, x + w, y + h, PaperRender.PAPER_LIGHT);

                // 2. Рисуем бумажную карточку с рамкой и тенью
                PaperRender.drawPaperCard(gfx, x, y, w, h, 1.0f, PaperRender.PAPER_LIGHT);

                // 3. Пытаемся вернуть иконку таба, чтобы она не исчезла
                drawTabIcon(gfx, child, x, y, w, h);

            } catch (Exception e) {
                // Игнорируем ошибки reflection, чтобы не крашить игру
            }
        }
    }

    /** Безопасное получение координат/размеров виджета через reflection. */
    private static int getWidgetValue(Object widget, String methodName, String fieldName) {
        try {
            java.lang.reflect.Method method = widget.getClass().getMethod(methodName);
            return (int) method.invoke(widget);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field field = widget.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getInt(widget);
            } catch (Exception e2) {
                return 0;
            }
        }
    }

    /** Пытается извлечь иконку таба (ResourceLocation или ItemStack) и отрисовать её. */
    private static void drawTabIcon(GuiGraphics gfx, Object tab, int x, int y, int w, int h) {
        // Попытка 1: Ищем поле 'icon' (обычно это ResourceLocation для UpgradeTab)
        try {
            java.lang.reflect.Field iconField = tab.getClass().getDeclaredField("icon");
            iconField.setAccessible(true);
            Object icon = iconField.get(tab);

            if (icon instanceof net.minecraft.resources.ResourceLocation rl) {
                // Рисуем иконку 16x16 строго по центру таба
                gfx.blit(rl, x + (w - 16) / 2, y + (h - 16) / 2, 0, 0, 16, 16, 16, 16);
                return;
            }

            // Для некоторых табов иконка может быть ItemStack
            if (icon instanceof net.minecraft.world.item.ItemStack stack) {
                gfx.renderItem(stack, x + (w - 16) / 2, y + (h - 16) / 2);
                return;
            }
        } catch (Exception e) {
            // Поле 'icon' не найдено, идем дальше
        }

        // Попытка 2: Ищем метод отрисовки иконки (например, renderIcon или drawIcon)
        try {
            for (java.lang.reflect.Method method : tab.getClass().getDeclaredMethods()) {
                if (method.getName().toLowerCase().contains("icon") && method.getParameterCount() == 1) {
                    method.setAccessible(true);
                    method.invoke(tab, gfx);
                    return;
                }
            }
        } catch (Exception e) {
            // Метод не найден
        }
    }
}
