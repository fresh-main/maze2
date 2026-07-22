package com.otbor.client;

import com.labyrinthmod.client.mixin.DisconnectedScreenAccessor;
import com.labyrinthmod.LabyrinthMod;
import com.otbor.client.widgets.PaperRender;
import com.otbor.client.widgets.PaperWidgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {

    // GUI scale больше НЕ форсится мод-классом — игроку дан слайдер в инструкции.
    // Дефолтное значение (3) ставится один раз в OtborMod на FMLClientSetupEvent.

    /**
     * Чат разрешён ТОЛЬКО в креативе или у наблюдателя.
     * OP-уровень и любые другие привилегии не учитываются — чтобы выдавать команды,
     * админ обязан сначала переключиться в /gamemode creative.
     */
    public static boolean chatAllowed() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return mc.player.isCreative() || mc.player.isSpectator();
    }

    @SubscribeEvent
    public static void onContainerMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
        if (cs.getSlotUnderMouse() == null) return;
        try {
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            com.otbor.OtborSounds.PENCIL.get(), 1.05f, 0.6f));
        } catch (Throwable ignored) {}
    }

    /** Запасной per-frame сброс камеры, если игрок нажмёт F5 — третье лицо
     *  не успеет отрисоваться даже на 1 кадр. */
    @SubscribeEvent
    public static void onRenderTick(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        if (mc.options.getCameraType() != net.minecraft.client.CameraType.FIRST_PERSON) {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        }
    }

    /** Поставлено OtborMod при смене языка — перезагрузим ресурсы в первом
     *  ClientTickEvent (вне setup-фазы, чтобы reloadResourcePacks не упал). */
    private static volatile boolean pendingLanguageReload = false;

    public static void requestLanguageReload() {
        pendingLanguageReload = true;
    }

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;

        if (pendingLanguageReload) {
            pendingLanguageReload = false;
            try {
                mc.reloadResourcePacks();
                LabyrinthMod.LOGGER.info("[otbor] resource pack reload triggered for language change");
            } catch (Throwable t) {
                LabyrinthMod.LOGGER.warn("[otbor] reloadResourcePacks failed", t);
            }
        }

        if (mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen && !chatAllowed()) {
            mc.setScreen(null);
        }

        // F5 (третье лицо) — БЕЗУСЛОВНО блокируем. Третье лицо не нужно никому, даже
        // админам в креативе (для тестов есть spectator).
        if (mc.options.getCameraType() != net.minecraft.client.CameraType.FIRST_PERSON) {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        }
        // F3 (координаты) и F3+B (хитбоксы) — БЕЗУСЛОВНО блокируем для всех режимов.
        if (mc.options.renderDebug) mc.options.renderDebug = false;
        if (mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
        }
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        var next = event.getNewScreen();

        // === ЗАМЕНА ЭКРАНА ЗАГРУЗКИ ИГРЫ ===
        if (next != null && next.getClass().getName().equals("net.minecraft.client.gui.screens.LoadingOverlay")) {
            event.setNewScreen(new OtborGameLoadingScreen());
            return;
        }
        // Перехватываем ванильное меню открытия для сети (ShareToLanScreen)
        if (next instanceof net.minecraft.client.gui.screens.ShareToLanScreen
                && !(next instanceof OtborShareToLanScreen)) {
            Screen parent = Minecraft.getInstance().screen;
            event.setNewScreen(new OtborShareToLanScreen(parent != null ? parent : new OtborTitleScreen()));
            return;
        }

        if (next instanceof TitleScreen && !(next instanceof OtborTitleScreen)) {
            event.setNewScreen(new OtborTitleScreen());
            return;
        }
        if (next instanceof PauseScreen && !(next instanceof OtborPauseScreen)) {
            event.setNewScreen(new OtborPauseScreen(true));
            return;
        }
        // Чат и команды через `/` доступны только в креативе или режиме наблюдателя —
        // OP в выживании/приключении/хардкоре нет.
        if (next instanceof net.minecraft.client.gui.screens.ChatScreen
                || next instanceof net.minecraft.client.gui.screens.inventory.CommandBlockEditScreen) {
            if (!chatAllowed()) {
                event.setCanceled(true);
                return;
            }
        }
        // Перехватываем ванильные "Options" (ESC → Настройки…): открываем нашу paper-инструкцию.
        // Вложенные экраны вроде KeyBindsScreen / VideoSettingsScreen НЕ трогаем,
        // чтобы наша инструкция могла сама открывать их при необходимости.
        if (next instanceof net.minecraft.client.gui.screens.OptionsScreen
                && !(next instanceof OtborInstructionScreen)) {
            Screen parent = Minecraft.getInstance().screen;
            event.setNewScreen(new OtborInstructionScreen(parent != null ? parent : new OtborTitleScreen()));
            return;
        }

        if (next instanceof net.minecraft.client.gui.screens.DisconnectedScreen disc
                && !(next instanceof OtborDisconnectedScreen)) {
            try {
                var acc = (DisconnectedScreenAccessor) (Object) disc;
                Screen parent = acc.otbor$getParent();
                Component title = disc.getTitle();
                Component reason = acc.otbor$getReason();
                // В ванильном DisconnectedScreen нет поля buttonText, оно всегда равно GUI_BACK
                Component buttonText = net.minecraft.network.chat.CommonComponents.GUI_BACK;
                event.setNewScreen(new OtborDisconnectedScreen(parent, title, reason, buttonText));
                return;
            } catch (Throwable t) {
                LabyrinthMod.LOGGER.warn("[otbor] failed to wrap DisconnectedScreen: {}", t.toString());
            }
        }

        // Curios: клавиша G открывает CuriosScreenV2 (свой отдельный инвентарь).
        // У нас Curios встроен в обычный E-инвентарь, поэтому переадресуем на него.
        if (next != null) {
            String cn = next.getClass().getName();
            if (cn.equals("top.theillusivec4.curios.client.gui.CuriosScreenV2")
                    || cn.equals("top.theillusivec4.curios.client.gui.CuriosScreen")) {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    event.setNewScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(player));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderPreOverlay(RenderGuiOverlayEvent.Pre event) {
        var id = event.getOverlay().id();
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();

        if (id.equals(VanillaGuiOverlay.CHAT_PANEL.id()) && !chatAllowed()) {
            event.setCanceled(true);
            return;
        }

        // Когда открыт любой экран (инвентарь, рюкзак, сундук, esc-меню) HUD не рендерим —
        // экран сам показывает нужные ячейки/состояние игрока.
        if (Minecraft.getInstance().screen != null) {
            if (id.equals(VanillaGuiOverlay.HOTBAR.id())
                    || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                    || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                    || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                    || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                    || id.equals(VanillaGuiOverlay.JUMP_BAR.id())) {
                event.setCanceled(true);
                return;
            }
        }

        if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.JUMP_BAR.id())) {
            event.setCanceled(true);
            return;
        }

        if (id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())) {
            event.setCanceled(true);
            OtborHudOverlay.renderFood(event.getGuiGraphics(), w, h);
            OtborHudOverlay.renderStamina(event.getGuiGraphics(), w, h);
            return;
        }

        if (id.equals(VanillaGuiOverlay.HOTBAR.id())) {
            event.setCanceled(true);
            OtborHudOverlay.renderHotbar(event.getGuiGraphics(), w, h, event.getPartialTick());
            return;
        }

        // ParCool регистрирует свой overlay поверх food_level — отменяем, чтобы он
        // не перекрывал наш HUD. Свою полоску выносливости мы рисуем сами.
        if ("parcool".equals(id.getNamespace())) {
            event.setCanceled(true);
            return;
        }

        if (id.equals(VanillaGuiOverlay.PLAYER_LIST.id())) {
            event.setCanceled(true);
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.keyPlayerList.isDown() && mc.player != null) {
                renderTabList(event.getGuiGraphics(), w, h);
            }
        }
    }

    /**
     * Отложенный вызов OtborTabList.render через reflection — обходим баг
     * Forge ModuleClassLoader, который иногда не может разрешить наши классы
     * при прямом вызове из event-handler'ов.
     */
    private static java.lang.reflect.Method tabListRenderMethod;
    private static boolean tabListRenderResolved = false;

    private static void renderTabList(net.minecraft.client.gui.GuiGraphics gfx, int w, int h) {
        try {
            if (!tabListRenderResolved) {
                Class<?> cls = Class.forName("com.otbor.client.OtborTabList");
                tabListRenderMethod = cls.getMethod("render",
                        net.minecraft.client.gui.GuiGraphics.class, int.class, int.class);
                tabListRenderResolved = true;
            }
            if (tabListRenderMethod != null) {
                tabListRenderMethod.invoke(null, gfx, w, h);
            }
        } catch (Throwable t) {
            // Лучше потерять одну отрисовку TAB, чем уронить весь рендер игры.
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onInventoryInitPostCleanup(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) return;
        // Curios встроен прямо в E-экран — убираем CuriosButton и прочие ImageButton-ки.
        for (GuiEventListener child : new ArrayList<>(screen.children())) {
            if (child instanceof net.minecraft.client.gui.components.ImageButton btn) {
                event.removeListener(btn);
            }
        }
    }

    @SubscribeEvent
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof ConnectScreen) && !(screen instanceof ProgressScreen)) return;

        Button vanillaCancel = null;
        for (GuiEventListener child : new ArrayList<>(screen.children())) {
            if (child instanceof Button b) {
                vanillaCancel = b;
                break;
            }
        }
        if (vanillaCancel == null) return;

        final Button captured = vanillaCancel;
        event.removeListener(captured);

        int paperH = LoadingScreenStyling.paperHeight(screen);
        int py = LoadingScreenStyling.paperY(screen);

        int btnW = 140;
        int btnH = 22;
        int btnX = screen.width / 2 - btnW / 2;
        int btnY = py + paperH - 46;

        net.minecraft.client.gui.components.AbstractWidget ours = PaperWidgets.paperButton(
                btnX, btnY, btnW, btnH,
                Component.literal("ОТМЕНА"),
                b -> captured.onPress(),
                0L, PaperRender.INK_RED, null);

        event.addListener(ours);
    }

    /**
     * Pose НЕ смещена — используем абсолютные координаты. Все вызовы BackpackScreenOverlay
     * идут через reflection: Forge ModuleClassLoader иногда не может разрешить наши классы
     * при прямом обращении из event-handler'ов.
     */
    @SubscribeEvent
    public static void onContainerBackground(ContainerScreenEvent.Render.Background event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        invokeBackpackOverlay("drawFullPaperBackground", event.getGuiGraphics(), screen);
    }

    /** Pose смещена — локальные координаты. */
    @SubscribeEvent
    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        invokeBackpackOverlay("drawRightSidePaperCover", event.getGuiGraphics(), screen);
        invokeBackpackOverlay("drawLockedCover", event.getGuiGraphics(), screen);
        if (screen instanceof net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase) {
            renderBackpackTabs(event.getGuiGraphics());
        }
    }
    private static final java.util.List<int[]> backpackTabData = new java.util.ArrayList<>();

    public static void addBackpackTabData(int x, int y, int w, int h, boolean isSelected) {
        backpackTabData.add(new int[]{x, y, w, h, isSelected ? 1 : 0});
    }

    public static void renderBackpackTabs(GuiGraphics gfx) {
        if (backpackTabData.isEmpty()) return;
        for (int[] data : backpackTabData) {
            int x = data[0];
            int y = data[1];
            int w = data[2];
            int h = data[3];
            boolean isSelected = data[4] == 1;

            int paperColor = isSelected ? PaperRender.PAPER_LIGHT : PaperRender.PAPER_BASE;
            PaperRender.drawPaperCard(gfx, x, y, w, h, 1.0f, paperColor);

            if (isSelected) {
                PaperRender.drawPin(gfx, x + w / 2, y + 4, true);
                gfx.fill(x, y, x + w, y + 1, PaperRender.INK_RED);
                gfx.fill(x, y + h - 1, x + w, y + h, PaperRender.INK_RED);
                gfx.fill(x, y, x + 1, y + h, PaperRender.INK_RED);
                gfx.fill(x + w - 1, y, x + w, y + h, PaperRender.INK_RED);
            }
        }
        backpackTabData.clear();
    }

    private static Class<?> backpackOverlayCls;
    private static final java.util.Map<String, java.lang.reflect.Method> backpackOverlayMethods =
            new java.util.HashMap<>();
    private static boolean backpackOverlayResolveFailed = false;

    private static void invokeBackpackOverlay(String methodName,
                                              net.minecraft.client.gui.GuiGraphics gfx,
                                              AbstractContainerScreen<?> screen) {
        if (backpackOverlayResolveFailed) return;
        try {
            if (backpackOverlayCls == null) {
                backpackOverlayCls = Class.forName("com.otbor.client.BackpackScreenOverlay");
            }
            java.lang.reflect.Method m = backpackOverlayMethods.get(methodName);
            if (m == null) {
                m = backpackOverlayCls.getMethod(methodName,
                        net.minecraft.client.gui.GuiGraphics.class, AbstractContainerScreen.class);
                backpackOverlayMethods.put(methodName, m);
            }
            m.invoke(null, gfx, screen);
        } catch (Throwable t) {
            // Если класс реально нельзя загрузить — отключаем оверлей навсегда,
            // чтобы не спамить exception'ами на каждом кадре.
            backpackOverlayResolveFailed = true;
        }
    }

    // ===== КЭШ ДАННЫХ ТАБОВ КРЕАТИВНОГО ИНВЕНТАРЯ =====
// Используем Object[], чтобы хранить int, boolean и CreativeModeTab
    private static final java.util.List<Object[]> creativeTabData = new java.util.ArrayList<>();

    public static void addCreativeTabData(int relX, int relY, boolean isSelected, net.minecraft.world.item.CreativeModeTab tab) {
        creativeTabData.add(new Object[]{relX, relY, isSelected, tab});
    }

    @SubscribeEvent
    public static void onScreenRenderPost(net.minecraftforge.client.event.ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen screen) {
            if (creativeTabData.isEmpty()) return;

            GuiGraphics gfx = event.getGuiGraphics();

            // Получаем абсолютные координаты левого верхнего угла GUI
            int guiLeft = screen.getGuiLeft();
            int guiTop = screen.getGuiTop();

            for (Object[] data : creativeTabData) {
                int relX = (int) data[0];
                int relY = (int) data[1];
                boolean isSelected = (boolean) data[2];
                net.minecraft.world.item.CreativeModeTab tab = (net.minecraft.world.item.CreativeModeTab) data[3];

                // Абсолютные координаты на экране
                int x = guiLeft + relX;
                int y = guiTop + relY;

                int topTabOffsetY = 7; // подберите нужное значение (по умолчанию 5)

                boolean isTop = relY < 0;
                if (isTop) {
                    y += topTabOffsetY;
                }

                // Размер таба в 1.20.1: 26x32
                int tabW = 26;
                int tabH = 26;

                int paperColor = isSelected ? PaperRender.PAPER_LIGHT : PaperRender.PAPER_BASE;

                // Рисуем бумажную карточку
                PaperRender.drawPaperCard(gfx, x, y, tabW, tabH, 1.0f, paperColor);

                if (isSelected) {
                    // Булавка
                    PaperRender.drawPin(gfx, x + tabW / 2, y + 6, true);

                    // Красная рамка
                    gfx.fill(x, y, x + tabW, y + 1, PaperRender.INK_RED);
                    gfx.fill(x, y + tabH - 1, x + tabW, y + tabH, PaperRender.INK_RED);
                    gfx.fill(x, y, x + 1, y + tabH, PaperRender.INK_RED);
                    gfx.fill(x + tabW - 1, y, x + tabW, y + tabH, PaperRender.INK_RED);
                }

                // === РУЧНАЯ ОТРИСОВКА ИКОНКИ ПРЕДМЕТА ===
                // Так как мы отменили ванильный renderTabButton, игра не рисует иконки.
                if (tab != null) {
                    net.minecraft.world.item.ItemStack icon = tab.getIconItem();
                    if (icon != null && !icon.isEmpty()) {
                        // Теперь isTop уже вычислен выше, используем его
                        int iconX = x + 5;
                        int iconY = y + 8 + (isTop ? 1 : -1);

                        gfx.renderItem(icon, iconX, iconY);
                        gfx.renderItemDecorations(screen.getMinecraft().font, icon, iconX, iconY);
                    }
                }
            }
            // Очищаем кэш после отрисовки кадра
            creativeTabData.clear();
        }
    }
    @SubscribeEvent
    public static void onCreativeInventoryInitPost(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)) return;

        Button leftBtn = null;
        Button rightBtn = null;

        // Ищем ванильные кнопки переключения страниц (< и >)
        for (GuiEventListener child : new java.util.ArrayList<>(screen.children())) {
            if (child instanceof Button btn) {
                String msg = btn.getMessage().getString();
                if ("<".equals(msg)) leftBtn = btn;
                else if (">".equals(msg)) rightBtn = btn;
            }
        }

        // Заменяем левую стрелку на нашу бумажную кнопку
        if (leftBtn != null) {
            // ★ ИСПРАВЛЕНИЕ: сохраняем ссылку в final-переменную, чтобы лямбда могла её использовать
            final Button finalLeftBtn = leftBtn;

            event.removeListener(leftBtn);
            int x = leftBtn.getX();
            int y = leftBtn.getY();
            int w = leftBtn.getWidth();
            int h = leftBtn.getHeight();

            net.minecraft.client.gui.components.AbstractWidget ours = PaperWidgets.paperButton(
                    x, y, w, h,
                    Component.literal("<"),
                    b -> finalLeftBtn.onPress(), // ★ Используем final-переменную
                    0L, PaperRender.INK_SOFT, null);
            event.addListener(ours);
        }

        // Заменяем правую стрелку на нашу бумажную кнопку
        if (rightBtn != null) {
            // ★ ИСПРАВЛЕНИЕ: сохраняем ссылку в final-переменную
            final Button finalRightBtn = rightBtn;

            event.removeListener(rightBtn);
            int x = rightBtn.getX();
            int y = rightBtn.getY();
            int w = rightBtn.getWidth();
            int h = rightBtn.getHeight();

            net.minecraft.client.gui.components.AbstractWidget ours = PaperWidgets.paperButton(
                    x, y, w, h,
                    Component.literal(">"),
                    b -> finalRightBtn.onPress(), // ★ Используем final-переменную
                    0L, PaperRender.INK_SOFT, null);
            event.addListener(ours);
        }
    }
}