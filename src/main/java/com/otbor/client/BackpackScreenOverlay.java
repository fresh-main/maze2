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
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Inventory;
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
        // Sophisticated Core reserves 94 px for the full player inventory.
        // This pack exposes only the hotbar, placed just below the storage slots.
        int fullH = ih + BACKPACK_PLAYER_INV_BLEED;
        if (screen instanceof StorageScreenBase<?> storageScreen) {
            int rows = storageScreen.getMenu().getNumberOfRows();
            int hotbarBottom = 18 + rows * 18 + 4 + 18;
            fullH = Math.min(fullH, hotbarBottom + 12);
        }

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
                .filter(s -> s.isActive() && !(s instanceof LockedSlot) && s.x >= 0 && s.y >= 0
                        && !(screen instanceof StorageScreenBase<?> && s.container instanceof Inventory
                        && s.getContainerSlot() < 9))
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

        if (screen instanceof StorageScreenBase<?> storageScreen) {
            int hotbarY = 18 + storageScreen.getMenu().getNumberOfRows() * 18 + 4;
            for (int i = 0; i < 9; i++) {
                PaperContainerRender.sketchSlotBox(gfx, leftPos + 8 + i * 18, topPos + hotbarY,
                        PaperContainerRender.phase(t, 0.55f + i * 0.025f, 0.75f + i * 0.025f));
            }
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

        if (screen instanceof AbstractFurnaceScreen<?> furnaceScreen) {
            drawVanillaFurnaceProgress(gfx, furnaceScreen, leftPos, topPos);
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
        if (!isBackpackScreen(screen) || SIDE_EXTEND <= 0) return;
        int iw = ((ContainerScreenAccessor) screen).otbor$getImageWidth();
        int ih = ((ContainerScreenAccessor) screen).otbor$getImageHeight();
        PaperRender.drawPaperCard(gfx, iw, 0, SIDE_EXTEND, ih, 1.0f, PaperRender.PAPER_LIGHT);
    }

    public static void drawLockedCover(GuiGraphics gfx, AbstractContainerScreen<?> screen) {
        if (!(screen instanceof StorageScreenBase<?> storageScreen)) return;
        int rows = storageScreen.getMenu().getNumberOfRows();
        int hotbarY = 18 + rows * 18 + 4;
        int iw = ((ContainerScreenAccessor) screen).otbor$getImageWidth();
        int ih = ((ContainerScreenAccessor) screen).otbor$getImageHeight();
        int panelBottom = Math.min(ih + BACKPACK_PLAYER_INV_BLEED, hotbarY + 18 + 12);
        // Cover the remnant of Sophisticated's hidden player-inventory texture below the hotbar.
        if (hotbarY + 18 < panelBottom - 3) {
            gfx.fill(4, hotbarY + 18, iw - 4, panelBottom - 3, PaperRender.PAPER_LIGHT);
        }
    }

    /** Стандартные ванильные индикаторы с реальным прогрессом горения и готовки. */
    private static void drawVanillaFurnaceProgress(GuiGraphics gfx,
                                                    AbstractFurnaceScreen<?> screen,
                                                    int leftPos, int topPos) {
        AbstractFurnaceMenu menu = screen.getMenu();
        int flameX = leftPos + 56;
        int flameY = topPos + 36;
        int arrowX = leftPos + 79;
        int arrowY = topPos + 34;

        drawFlameIcon(gfx, flameX, flameY, PaperRender.INK_FADED, 14);
        drawArrowIcon(gfx, arrowX, arrowY, PaperRender.INK_FADED, 24);

        if (menu.isLit()) {
            drawFlameIcon(gfx, flameX, flameY, PaperRender.INK_RED,
                    Math.min(14, menu.getLitProgress() + 1));
        }
        int burn = menu.getBurnProgress();
        if (burn > 0) {
            drawArrowIcon(gfx, arrowX, arrowY, PaperRender.INK_RED,
                    Math.min(24, burn + 1));
        }
    }

    private static void drawFlameIcon(GuiGraphics gfx, int x, int y, int color, int fillHeight) {
        int clipTop = y + 14 - Math.max(0, Math.min(14, fillHeight));
        drawClipped(gfx, x + 1, y + 5, x + 4, y + 14, clipTop, x + 14, color);
        drawClipped(gfx, x + 2, y + 2, x + 4, y + 7, clipTop, x + 14, color);
        drawClipped(gfx, x + 5, y + 3, x + 9, y + 14, clipTop, x + 14, color);
        drawClipped(gfx, x + 7, y, x + 9, y + 5, clipTop, x + 14, color);
        drawClipped(gfx, x + 10, y + 5, x + 13, y + 14, clipTop, x + 14, color);
        drawClipped(gfx, x + 11, y + 2, x + 13, y + 7, clipTop, x + 14, color);
    }

    private static void drawArrowIcon(GuiGraphics gfx, int x, int y, int color, int fillWidth) {
        int clipRight = x + Math.max(0, Math.min(24, fillWidth));
        // Узкое древко и построчно сужающийся треугольный наконечник.
        drawClipped(gfx, x, y + 6, x + 16, y + 10, y, clipRight, color);
        for (int row = 1; row <= 14; row++) {
            int tipRight = x + 24 - Math.abs(7 - row);
            drawClipped(gfx, x + 14, y + row, tipRight, y + row + 1,
                    y, clipRight, color);
        }
    }

    private static void drawClipped(GuiGraphics gfx, int x1, int y1, int x2, int y2,
                                    int clipTop, int clipRight, int color) {
        int left = x1;
        int top = Math.max(y1, clipTop);
        int right = Math.min(x2, clipRight);
        if (right > left && y2 > top) {
            gfx.fill(left, top, right, y2, color);
        }
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

                // 3. Внутренняя ячейка вкладки выглядит как остальные слоты рюкзака.
                int slotX = x + (w - 16) / 2;
                int slotY = y + (h - 16) / 2;
                PaperContainerRender.sketchSlotBox(gfx, slotX, slotY, 1.0f);

                // 4. Возвращаем иконку поверх тёмной ячейки, чтобы она не исчезла.
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
        int iconX = x + (w - 16) / 2;
        int iconY = y + (h - 16) / 2;
        // Значок может храниться в базовом классе конкретной вкладки.
        try {
            for (Class<?> type = tab.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    java.lang.reflect.Field iconField = type.getDeclaredField("icon");
                    iconField.setAccessible(true);
                    Object icon = iconField.get(tab);

                    if (icon instanceof net.minecraft.resources.ResourceLocation rl) {
                        gfx.blit(rl, iconX, iconY, 0, 0, 16, 16, 16, 16);
                        return;
                    }

                    if (icon instanceof net.minecraft.world.item.ItemStack stack) {
                        gfx.renderItem(stack, iconX, iconY);
                        return;
                    }
                } catch (NoSuchFieldException ignored) {
                    // Проверяем базовый класс.
                }
            }
        } catch (Exception e) {
            // Если поле не подходит, попробуем метод отрисовки.
        }

        // Попытка 2: ищем метод иконки в иерархии классов вкладки.
        for (Class<?> type = tab.getClass(); type != null; type = type.getSuperclass()) {
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                if (method.getName().toLowerCase(java.util.Locale.ROOT).contains("icon")
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(gfx)) {
                    try {
                        method.setAccessible(true);
                        method.invoke(tab, gfx);
                        return;
                    } catch (Exception ignored) {
                        // Продолжаем поиск совместимого метода.
                    }
                }
            }
        }
    }
}
