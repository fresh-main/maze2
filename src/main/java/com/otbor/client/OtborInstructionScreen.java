package com.otbor.client;

import com.otbor.client.widgets.PaperRender;
import com.otbor.client.widgets.PaperWidgets;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OtborInstructionScreen extends Screen {
    private static final int TAB_GAMEPLAY = 0;
    private static final int TAB_GRAPHICS = 1;
    private static final int TAB_CONTROLS = 2;
    private static final int TAB_SOUND    = 3;
    private static final int TAB_RESOURCES = 4; // НОВАЯ ВКЛАДКА

    private static final String[] TAB_LABELS = { "ПРАВИЛА ИГРЫ ", "ГРАФИКА ", "УПРАВЛЕНИЕ ", "ЗВУК ", "РЕСУРСЫ " };

    private final Screen parent;
    private int tab = TAB_GAMEPLAY;

    private static final long TAB_TRANSITION_MS = 280L;
    private int pendingTab = -1;
    private long transitionStart = 0L;
    private int transitionDir = 1;

    // Списки для управления ресурс-паками
    private List<String> selectedPackIds = null;
    private List<String> unselectedPackIds = null;
    // Константы для зоны прокрутки ресурс-паков (внутри карточки 360x260)
    private static final int RES_LIST_TOP = 188;    // 138 (cardY) + 50 (отступ под заголовок)
    private static final int RES_LIST_BOTTOM = 328; // 138 + 260 - 70 (место под кнопки)
    private static final int RES_LIST_HEIGHT = RES_LIST_BOTTOM - RES_LIST_TOP;

    private float resScrollOffset = 0f;
    private float resMaxScroll = 0f;
    private boolean draggingResThumb = false;
    private float resDragStartY = 0f;
    private float resDragStartScroll = 0f;

    private java.util.List<Object> resEntries = new java.util.ArrayList<>();

    // Внутренний класс для хранения данных о строке ресурс-пака
    private class PackRow {
        String id;
        String title;
        boolean isSelected;
        int listIndex;
        net.minecraft.resources.ResourceLocation icon;

        // Координаты кнопок (вычисляются при рендере)
        int btnUpX, btnUpY;
        int btnDownX, btnDownY;
        int btnActionX, btnActionY;
        boolean showUp, showDown;
    }
    // Кэш иконок ресурс-паков
    private final java.util.Map<String, net.minecraft.resources.ResourceLocation> packIcons = new java.util.HashMap<>();

    // ИСПРАВЛЕНИЕ: Используем fromNamespaceAndPath вместо устаревшего конструктора
    private static final net.minecraft.resources.ResourceLocation DEFAULT_PACK_ICON = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/unknown_pack.png");

    public OtborInstructionScreen(Screen parent) {
        super(Component.literal("ИНСТРУКЦИЯ О.Т.Б.О.Р "));
        this.parent = parent;
        forceGuiScale();
    }

    private void forceGuiScale() {
        try {
            Options options = Minecraft.getInstance().options;
            if (options.guiScale().get() != 2) {
                options.guiScale().set(2);
                options.save();
                Minecraft mc = Minecraft.getInstance();
                if (mc.getWindow() != null) {
                    mc.resizeDisplay();
                }
                com.labyrinthmod.LabyrinthMod.LOGGER.info("[otbor] gui scale forced to 2 ");
            }
        } catch (Throwable t) {
            com.labyrinthmod.LabyrinthMod.LOGGER.warn("[otbor] failed to force gui scale ", t);
        }
    }

    private void startTabTransition(int target) {
        if (target == tab || pendingTab != -1) return;
        this.pendingTab = target;
        this.transitionStart = System.currentTimeMillis();
        this.transitionDir = target > tab ? 1 : -1;
        if (target == TAB_RESOURCES) {
            refreshPackLists();
        }
    }

    private boolean isTransitioning() {
        if (transitionStart == 0L) return false;
        return (System.currentTimeMillis() - transitionStart) < TAB_TRANSITION_MS;
    }

    private static void openKeyBindsScreen(Screen parentScreen) {
        try {
            Class<?> cls = Class.forName("com.otbor.client.OtborKeyBindsScreen ");
            Object screen = cls.getConstructor(Screen.class).newInstance(parentScreen);
            Minecraft.getInstance().setScreen((Screen) screen);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    protected void init() {
        super.init();
        // Инициализируем списки паков при открытии меню
        PackRepository repo = Minecraft.getInstance().getResourcePackRepository();
        selectedPackIds = new ArrayList<>(repo.getSelectedIds());
        unselectedPackIds = new ArrayList<>();
        for (Pack pack : repo.getAvailablePacks()) {
            if (!selectedPackIds.contains(pack.getId())) {
                unselectedPackIds.add(pack.getId());
            }
        }
        refreshPackLists();
        rebuildUi();
    }
    private void refreshPackLists() {
        net.minecraft.server.packs.repository.PackRepository repo = Minecraft.getInstance().getResourcePackRepository();

        // КРИТИЧЕСКИ ВАЖНО: Заставляем репозиторий перечитать папку resourcepacks
        repo.reload();

        List<String> newSelected = new java.util.ArrayList<>();
        List<String> newUnselected = new java.util.ArrayList<>();

        // Сначала добавляем уже выбранные паки (сохраняем их порядок)
        for (String id : repo.getSelectedIds()) {
            if (repo.getPack(id) != null) {
                newSelected.add(id);
            }
        }

        // Затем добавляем все доступные, но не выбранные паки
        for (net.minecraft.server.packs.repository.Pack pack : repo.getAvailablePacks()) {
            if (!newSelected.contains(pack.getId())) {
                newUnselected.add(pack.getId());
            }
        }

        this.selectedPackIds = newSelected;
        this.unselectedPackIds = newUnselected;

        // Сбрасываем кэш иконок, чтобы новые паки успели загрузить свои pack.png
        this.packIcons.clear();
    }

    private void rebuildUi() {
        this.clearWidgets();

        addRenderableWidget(PaperWidgets.paperButton(
                30, 30, 100, 22,
                Component.literal(" <- НАЗАД "),
                b -> this.minecraft.setScreen(parent),
                0L, PaperRender.INK_SOFT, null));

        int cx = this.width / 2;
        int tabW = 140;
        int gap = 14;
        int totalW = tabW * 5 + gap * 4; // Теперь 5 вкладок
        int startX = cx - totalW / 2;
        int tabY = 98;
        for (int i = 0; i < 5; i++) {
            final int t = i;
            int accent = this.tab == t ? PaperRender.INK_RED : PaperRender.INK_SOFT;
            addRenderableWidget(PaperWidgets.paperButton(
                    startX + i * (tabW + gap), tabY,
                    tabW, 26,
                    Component.literal(TAB_LABELS[i]),
                    b -> startTabTransition(t),
                    0L, accent, null));
        }

        int contentY = 150;
        switch (tab) {
            case TAB_GAMEPLAY -> buildGameplay(cx, contentY);
            case TAB_GRAPHICS -> buildGraphics(cx, contentY);
            case TAB_CONTROLS -> buildControls(cx, contentY);
            case TAB_SOUND    -> buildSound(cx, contentY);
            case TAB_RESOURCES -> buildResources(cx, contentY);
        }
    }

    private void buildGameplay(int cx, int y) {
        Options o = Minecraft.getInstance().options;
        int cardW = 360;
        int cardX = cx - cardW / 2;
        int inX = cardX + 20;
        int inW = cardW - 40;
        int sy = y + 46;

        addRenderableWidget(PaperWidgets.paperSlider(
                inX, sy, inW, 22,
                "угол обзора (FOV) ",
                (o.fov().get() - 30) / 80.0,
                v -> o.fov().set((int) Math.round(30 + v * 80)),
                v -> Math.round(30 + v * 80) + "° "
        ));
        sy += 34;

        addRenderableWidget(PaperWidgets.paperCheckbox(
                inX, sy, inW, 16,
                "автопрыжок ",
                () -> o.autoJump().get(),
                o.autoJump()::set
        ));
        sy += 22;

        addRenderableWidget(PaperWidgets.paperCheckbox(
                inX, sy, inW, 16,
                "субтитры ",
                () -> o.showSubtitles().get(),
                o.showSubtitles()::set
        ));
    }

    private void buildGraphics(int cx, int y) {
        Options o = Minecraft.getInstance().options;
        int cardW = 360;
        int cardX = cx - cardW / 2;
        int inX = cardX + 20;
        int inW = cardW - 40;
        int sy = y + 46;

        addRenderableWidget(PaperWidgets.paperSliderDeferred(
                inX, sy, inW, 22,
                "дальность прорисовки ",
                (o.renderDistance().get() - 2) / 30.0,
                v -> o.renderDistance().set((int) Math.round(2 + v * 30)),
                v -> Math.round(2 + v * 30) + " чанков "
        ));
        sy += 28;

        addRenderableWidget(PaperWidgets.paperSliderDeferred(
                inX, sy, inW, 22,
                "яркость (гамма) ",
                Math.min(1.0, Math.max(0.0, o.gamma().get())),
                v -> o.gamma().set(v),
                v -> Math.round(v * 100) + "% "
        ));
        sy += 28;

        addRenderableWidget(PaperWidgets.paperSliderDeferred(
                inX, sy, inW, 22,
                "макс. FPS ",
                (o.framerateLimit().get() - 10) / 250.0,
                v -> o.framerateLimit().set((int) Math.round(10 + v * 250)),
                v -> {
                    int f = (int) Math.round(10 + v * 250);
                    return f >= 260 ? "макс " : f + " к/с ";
                }
        ));
        sy += 28;

        addRenderableWidget(PaperWidgets.paperCheckbox(
                inX, sy, inW / 2 - 4, 16,
                "VSync ",
                () -> o.enableVsync().get(),
                o.enableVsync()::set
        ));

        addRenderableWidget(PaperWidgets.paperCheckbox(
                inX + inW / 2 + 4, sy, inW / 2 - 4, 16,
                "тени сущностей ",
                () -> o.entityShadows().get(),
                o.entityShadows()::set
        ));
        sy += 26;

        if (o.graphicsMode().get().getId() > 1) {
            o.graphicsMode().set(GraphicsStatus.FANCY);
        }
        addRenderableWidget(PaperWidgets.paperSegment(
                inX, sy, inW, 18,
                new String[]{"быстро ", "красиво "},
                () -> Math.min(1, o.graphicsMode().get().getId()),
                i -> o.graphicsMode().set(GraphicsStatus.byId(Math.min(1, i)))
        ));
        sy += 24;

        addRenderableWidget(PaperWidgets.paperSegment(
                inX, sy, inW, 18,
                new String[]{"все частицы ", "меньше ", "минимум "},
                () -> o.particles().get().getId(),
                i -> o.particles().set(ParticleStatus.byId(i))
        ));
        sy += 24;

        addRenderableWidget(PaperWidgets.paperSegment(
                inX, sy, inW, 18,
                new String[]{"облака выкл ", "быстро ", "красиво "},
                () -> o.cloudStatus().get().ordinal(),
                i -> o.cloudStatus().set(CloudStatus.values()[Math.max(0, Math.min(CloudStatus.values().length - 1, i))])
        ));
        sy += 22;

        addRenderableWidget(PaperWidgets.paperButton(
                inX, sy, inW, 20,
                Component.literal("→ ПРОДВИНУТЫЕ НАСТРОЙКИ ← "),
                b -> {
                    if (com.otbor.client.EmbeddiumOptionsScreen.isEmbeddiumAvailable()) {
                        Minecraft.getInstance().setScreen(new com.otbor.client.EmbeddiumOptionsScreen(this));
                    } else {
                        com.labyrinthmod.LabyrinthMod.LOGGER.info("[otbor] Embeddium not available ");
                    }
                },
                0L, PaperRender.INK_RED, null
        ));
    }

    private void buildControls(int cx, int y) {
        Options o = Minecraft.getInstance().options;
        int cardW = 360;
        int cardX = cx - cardW / 2;
        int inX = cardX + 20;
        int inW = cardW - 40;
        int sy = y + 46;

        addRenderableWidget(PaperWidgets.paperSlider(
                inX, sy, inW, 22,
                "чувствительность мыши ",
                o.sensitivity().get(),
                v -> o.sensitivity().set(v),
                v -> Math.round(v * 200) + "% "
        ));
        sy += 28;

        addRenderableWidget(PaperWidgets.paperCheckbox(
                inX, sy, inW, 16,
                "инверсия взгляда ",
                () -> o.invertYMouse().get(),
                o.invertYMouse()::set
        ));
        sy += 22;

        addRenderableWidget(PaperWidgets.paperCheckbox(
                inX, sy, inW, 16,
                "дрожание камеры ",
                () -> o.bobView().get(),
                o.bobView()::set
        ));
        sy += 28;

        addRenderableWidget(PaperWidgets.paperButton(
                inX + inW / 2 - 100, sy, 200, 24,
                Component.literal("-> ПЕРЕНАЗНАЧИТЬ КЛАВИШИ  <-"),
                b -> openKeyBindsScreen(this),
                0L, PaperRender.INK_RED, null));
    }

    private void buildSound(int cx, int y) {
        Options o = Minecraft.getInstance().options;
        int cardW = 360;
        int cardX = cx - cardW / 2;
        int inX = cardX + 20;
        int inW = cardW - 40;
        int sy = y + 46;

        sy = addVolumeSlider(inX, sy, inW, o, SoundSource.MASTER, "общая громкость ");
        sy = addVolumeSlider(inX, sy, inW, o, SoundSource.MUSIC, "музыка ");
        sy = addVolumeSlider(inX, sy, inW, o, SoundSource.AMBIENT, "окружение ");
        sy = addVolumeSlider(inX, sy, inW, o, SoundSource.BLOCKS, "эффекты ");
        sy = addVolumeSlider(inX, sy, inW, o, SoundSource.PLAYERS, "шаги (важно!) ");
    }

    private void buildResources(int cx, int y) {
        int cardW = 360;
        int cardX = cx - cardW / 2;
        int inX = cardX + 20;
        int inW = cardW - 40;

        // Собираем данные для списка (без создания виджетов)
        buildResourceEntries();

        // Фиксированные кнопки внизу карточки
        int btnY = RES_LIST_BOTTOM + 10;
        int btnW = (inW - 16) / 3;

        addRenderableWidget(PaperWidgets.paperButton(inX, btnY, btnW, 20,
                Component.literal("ОБНОВИТЬ"),
                b -> { refreshPackLists(); rebuildUi(); }, 0L, PaperRender.INK_SOFT, null));

        addRenderableWidget(PaperWidgets.paperButton(inX + btnW + 8, btnY, btnW, 20,
                Component.literal("ПАПКА"),
                b -> {
                    try {
                        java.nio.file.Path packDir = Minecraft.getInstance().getResourcePackDirectory();
                        net.minecraft.Util.getPlatform().openUri(packDir.toUri());
                    } catch (Exception e) {}
                }, 0L, PaperRender.INK_SOFT, null));

        addRenderableWidget(PaperWidgets.paperButton(inX + (btnW + 8) * 2, btnY, btnW, 20,
                Component.literal("ПРИМЕНИТЬ"),
                b -> applyResourcePacks(), 0L, PaperRender.INK_RED, null));
    }
    private void drawSmallButton(GuiGraphics gfx, int x, int y, int w, int h, String text, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int color = hovered ? PaperRender.INK_RED : PaperRender.INK_SOFT;
        gfx.fill(x, y, x + w, y + h, PaperRender.withAlpha(color, 0.2f));
        gfx.fill(x, y, x + w, y + 1, color);
        gfx.fill(x, y + h - 1, x + w, y + h, color);
        gfx.fill(x, y, x + 1, y + h, color);
        gfx.fill(x + w - 1, y, x + w, y + h, color);

        int textW = this.font.width(text);
        gfx.drawString(this.font, text, x + (w - textW) / 2, y + (h - 8) / 2, PaperRender.INK, false);
    }

    private boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // НОВЫЙ МЕТОД: Подготавливает список паков для ручного рендера
    private void buildResourceEntries() {
        resEntries.clear();
        net.minecraft.server.packs.repository.PackRepository repo = Minecraft.getInstance().getResourcePackRepository();

        resEntries.add("АКТИВНЫЕ РЕСУРС-ПАКИ");
        for (int i = 0; i < selectedPackIds.size(); i++) {
            String id = selectedPackIds.get(i);
            net.minecraft.server.packs.repository.Pack pack = repo.getPack(id);
            if (pack == null) continue;
            PackRow row = new PackRow();
            row.id = id;
            row.title = pack.getTitle().getString();
            if (row.title.length() > 20) row.title = row.title.substring(0, 17) + "...";
            row.isSelected = true;
            row.listIndex = i;
            row.icon = getPackIcon(pack);
            row.showUp = i > 0;
            row.showDown = i < selectedPackIds.size() - 1;
            resEntries.add(row);
        }

        resEntries.add("ДОСТУПНЫЕ РЕСУРС-ПАКИ");
        for (int i = 0; i < unselectedPackIds.size(); i++) {
            String id = unselectedPackIds.get(i);
            net.minecraft.server.packs.repository.Pack pack = repo.getPack(id);
            if (pack == null) continue;
            PackRow row = new PackRow();
            row.id = id;
            row.title = pack.getTitle().getString();
            if (row.title.length() > 25) row.title = row.title.substring(0, 22) + "...";
            row.isSelected = false;
            row.listIndex = i;
            row.icon = getPackIcon(pack);
            row.showUp = false;
            row.showDown = false;
            resEntries.add(row);
        }

        // Считаем максимальный скролл
        float totalH = 0;
        for (Object e : resEntries) {
            totalH += (e instanceof String) ? 26 : 22;
        }
        resMaxScroll = Math.max(0, totalH - RES_LIST_HEIGHT);
        resScrollOffset = Math.min(resScrollOffset, resMaxScroll);
    }

    // Метод для получения иконки пака (с кэшированием)
    private net.minecraft.resources.ResourceLocation getPackIcon(net.minecraft.server.packs.repository.Pack pack) {
        String id = pack.getId();
        if (packIcons.containsKey(id)) {
            return packIcons.get(id);
        }

        net.minecraft.resources.ResourceLocation icon = loadPackIcon(pack);
        packIcons.put(id, icon);
        return icon;
    }

    // Метод для загрузки иконки пака из pack.png
    private net.minecraft.resources.ResourceLocation loadPackIcon(net.minecraft.server.packs.repository.Pack pack) {
        try {
            net.minecraft.server.packs.PackResources packResources = pack.open();
            if (packResources == null) return DEFAULT_PACK_ICON;

            net.minecraft.server.packs.resources.IoSupplier<java.io.InputStream> iconSupplier = packResources.getRootResource("pack.png");
            if (iconSupplier == null) {
                packResources.close();
                return DEFAULT_PACK_ICON;
            }

            String safeId = net.minecraft.Util.sanitizeName(pack.getId(), net.minecraft.resources.ResourceLocation::validPathChar);
            net.minecraft.resources.ResourceLocation textureId = new net.minecraft.resources.ResourceLocation(
                    "minecraft",
                    "pack/" + safeId + "/" + com.google.common.hash.Hashing.sha1().hashUnencodedChars(pack.getId()) + "/icon"
            );

            try (java.io.InputStream inputstream = iconSupplier.get()) {
                com.mojang.blaze3d.platform.NativeImage nativeimage = com.mojang.blaze3d.platform.NativeImage.read(inputstream);
                Minecraft.getInstance().getTextureManager().register(textureId, new net.minecraft.client.renderer.texture.DynamicTexture(nativeimage));
                packResources.close();
                return textureId;
            }
        } catch (Exception exception) {
            com.labyrinthmod.LabyrinthMod.LOGGER.warn("[otbor] Failed to load icon from pack {}", pack.getId(), exception);
            return DEFAULT_PACK_ICON;
        }
    }

    private void applyResourcePacks() {
        if (selectedPackIds == null) return;
        PackRepository repo = Minecraft.getInstance().getResourcePackRepository();
        repo.setSelected(selectedPackIds); // Устанавливаем новый порядок и состав
        Minecraft.getInstance().reloadResourcePacks(); // Перезагружаем текстуры

        // Обновляем списки после перезагрузки
        selectedPackIds = new ArrayList<>(repo.getSelectedIds());
        unselectedPackIds = new ArrayList<>();
        for (Pack pack : repo.getAvailablePacks()) {
            if (!selectedPackIds.contains(pack.getId())) {
                unselectedPackIds.add(pack.getId());
            }
        }
        rebuildUi();
    }

    private int addVolumeSlider(int x, int y, int w, Options o, SoundSource src, String label) {
        addRenderableWidget(PaperWidgets.paperSlider(
                x, y, w, 22, label,
                o.getSoundSourceVolume(src),
                v -> {
                    o.getSoundSourceOptionInstance(src).set((double) v);
                    o.save();
                },
                v -> Math.round(v * 100) + "% "
        ));
        return y + 28;
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        PaperRender.drawBoardBackground(gfx, this.width, this.height);

        Font font = this.font;
        String kicker = "ФАЙЛ №03 · РУКОВОДСТВО БЕГУЩЕГО ";
        int kw = font.width(kicker);
        gfx.drawString(font, kicker, this.width / 2 - kw / 2, 34, 0xFFB8A581, false);

        String title = "ИНСТРУКЦИЯ О.Т.Б.О.Р ";
        float ts = 2.0f;
        int tw = (int) (font.width(title) * ts);
        gfx.pose().pushPose();
        gfx.pose().translate(this.width / 2f - tw / 2f, 44, 0);
        gfx.pose().scale(ts, ts, 1f);
        gfx.drawString(font, title, 0, 0, PaperRender.PAPER_LIGHT, false);
        gfx.pose().popPose();

        int cx = this.width / 2;
        int cardW = 360;
        int cardH = 260;
        int cardX = cx - cardW / 2;
        int cardY = 138;

        renderPaperCard(gfx, cardX, cardY, cardW, cardH);
        // ===== РЕНДЕР СПИСКА РЕСУРС-ПАКОВ СО СКРОЛЛОМ =====
        if (tab == TAB_RESOURCES) {
            // ИСПРАВЛЕНИЕ: используем уже существующую переменную cardX, не объявляем её заново
            int inX = cardX + 20;
            int inW = 320;

            // Включаем "ножницы", чтобы паки не залезали на заголовок карточки
            gfx.enableScissor(inX, RES_LIST_TOP, inX + inW, RES_LIST_BOTTOM);
            float currentY = RES_LIST_TOP - resScrollOffset;

            for (Object e : resEntries) {
                float h = (e instanceof String) ? 18 : 20;
                if (currentY + h > RES_LIST_TOP && currentY < RES_LIST_BOTTOM) {
                    if (e instanceof String header) {
                        gfx.fill(inX, (int) currentY + 9, inX + inW, (int) currentY + 10, PaperRender.withAlpha(PaperRender.INK_FADED, 0.3f));
                        gfx.drawString(font, header, inX + 4, (int) currentY + 2, PaperRender.INK_FADED, false);
                    } else {
                        PackRow row = (PackRow) e;
                        int rowY = (int) currentY;

                        // Фон строки
                        gfx.fill(inX, rowY, inX + inW, rowY + 20, PaperRender.withAlpha(PaperRender.PAPER_LIGHT, 0.3f));
                        gfx.fill(inX, rowY, inX + inW, rowY + 1, PaperRender.withAlpha(PaperRender.INK_FADED, 0.5f));
                        gfx.fill(inX, rowY + 19, inX + inW, rowY + 20, PaperRender.withAlpha(PaperRender.INK_FADED, 0.5f));
                        gfx.fill(inX, rowY, inX + 1, rowY + 20, PaperRender.withAlpha(PaperRender.INK_FADED, 0.5f));
                        gfx.fill(inX + inW - 1, rowY, inX + inW, rowY + 20, PaperRender.withAlpha(PaperRender.INK_FADED, 0.5f));

                        // Иконка
                        gfx.blit(row.icon, inX + 2, rowY + 2, 0, 0, 16, 16, 16, 16);

                        // Название
                        gfx.drawString(font, row.title, inX + 22, rowY + 6, PaperRender.INK, false);

                        // Кнопки управления
                        int btnY = rowY + 2;
                        if (row.isSelected) {
                            if (row.showUp) { row.btnUpX = inX + inW - 60; row.btnUpY = btnY; drawSmallButton(gfx, row.btnUpX, btnY, 18, 16, "▲", mouseX, mouseY); }
                            if (row.showDown) { row.btnDownX = inX + inW - 40; row.btnDownY = btnY; drawSmallButton(gfx, row.btnDownX, btnY, 18, 16, "▼", mouseX, mouseY); }
                            row.btnActionX = inX + inW - 20; row.btnActionY = btnY;
                            drawSmallButton(gfx, row.btnActionX, btnY, 20, 16, "X", mouseX, mouseY);
                        } else {
                            row.btnActionX = inX + inW - 20; row.btnActionY = btnY;
                            drawSmallButton(gfx, row.btnActionX, btnY, 20, 16, "+", mouseX, mouseY);
                        }
                    }
                }
                currentY += (e instanceof String) ? 26 : 22;
            }
            gfx.disableScissor(); // Выключаем ножницы

            // Скроллбар
            if (resMaxScroll > 0) {
                int sbX = inX + inW - 6;
                gfx.fill(sbX, RES_LIST_TOP, sbX + 6, RES_LIST_BOTTOM, PaperRender.withAlpha(PaperRender.INK_FADED, 0.3f));
                float thumbH = Math.max(20f, RES_LIST_HEIGHT * (RES_LIST_HEIGHT / (RES_LIST_HEIGHT + resMaxScroll)));
                float thumbY = RES_LIST_TOP + (RES_LIST_HEIGHT - thumbH) * (resScrollOffset / resMaxScroll);
                gfx.fill(sbX, (int) thumbY, sbX + 6, (int) (thumbY + thumbH), PaperRender.INK_RED);
            }
        }

        float tProgress = 1f;
        if (transitionStart != 0L) {
            tProgress = Math.min(1f, (System.currentTimeMillis() - transitionStart) / (float) TAB_TRANSITION_MS);
            if (tProgress >= 0.5f && pendingTab != -1) {
                this.tab = pendingTab;
                this.pendingTab = -1;
                rebuildUi();
            }
            if (tProgress >= 1f) {
                transitionStart = 0L;
            }
        }

        super.render(gfx, mouseX, mouseY, partialTick);

        if (transitionStart != 0L) {
            renderTabTransitionSweep(gfx, cardX, cardY, cardW, cardH, tProgress);
        }
    }

    private void renderTabTransitionSweep(GuiGraphics gfx, int cardX, int cardY, int cardW, int cardH, float p) {
        int sweepTop = cardY + 38;
        int sweepBottom = cardY + cardH + 10;
        int sweepLeft = cardX - 4;
        int sweepRight = cardX + cardW + 4;
        int sweepW = sweepRight - sweepLeft;

        boolean outPhase = p < 0.5f;
        float half = outPhase ? (p / 0.5f) : ((p - 0.5f) / 0.5f);
        float eased = half * half * (3f - 2f * half);

        int dir = transitionDir;
        int x1, x2;
        if (outPhase) {
            int covered = (int) (sweepW * eased);
            if (dir > 0) {
                x1 = sweepLeft;
                x2 = sweepLeft + covered;
            } else {
                x1 = sweepRight - covered;
                x2 = sweepRight;
            }
        } else {
            int uncovered = (int) (sweepW * eased);
            if (dir > 0) {
                x1 = sweepLeft + uncovered;
                x2 = sweepRight;
            } else {
                x1 = sweepLeft;
                x2 = sweepRight - uncovered;
            }
        }

        if (x2 <= x1) return;

        int paper = PaperRender.PAPER_LIGHT;
        gfx.fill(x1, sweepTop, x2, sweepBottom, paper);
        int border = PaperRender.PAPER_EDGE;
        gfx.fill(x1, sweepTop, x2, sweepTop + 1, border);
        gfx.fill(x1, sweepBottom - 1, x2, sweepBottom, border);
        gfx.fill(x1, sweepTop, x1 + 1, sweepBottom, border);
        gfx.fill(x2 - 1, sweepTop, x2, sweepBottom, border);

        boolean leadOnRight = (outPhase && dir > 0) || (!outPhase && dir < 0);
        int leadColor = PaperRender.INK_RED;
        if (leadOnRight) {
            gfx.fill(x2 - 2, sweepTop, x2, sweepBottom, leadColor);
        } else {
            gfx.fill(x1, sweepTop, x1 + 2, sweepBottom, leadColor);
        }

        int hatch = PaperRender.withAlpha(PaperRender.INK_FADED, 0.35f);
        for (int dy = 0; dy < (sweepBottom - sweepTop); dy += 6) {
            int hy = sweepTop + dy;
            int hxStart = Math.max(x1 + 4, x1 + 4 + (dy / 6) * 2 % 12);
            int hxEnd = Math.min(x2 - 4, hxStart + 14);
            if (hxEnd > hxStart) {
                gfx.fill(hxStart, hy, hxEnd, hy + 1, hatch);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (isTransitioning()) return true;

        // Обработка кликов по списку ресурс-паков и скроллбару
        if (tab == TAB_RESOURCES && button == 0) {
            int cardX = this.width / 2 - 180;
            int inX = cardX + 20;
            int inW = 320;
            int sbX = inX + inW - 6;

            // Клик по скроллбару
            if (resMaxScroll > 0 && mx >= sbX && mx <= sbX + 6 && my >= RES_LIST_TOP && my <= RES_LIST_BOTTOM) {
                float thumbH = Math.max(20f, RES_LIST_HEIGHT * (RES_LIST_HEIGHT / (RES_LIST_HEIGHT + resMaxScroll)));
                float thumbY = RES_LIST_TOP + (RES_LIST_HEIGHT - thumbH) * (resScrollOffset / resMaxScroll);
                if (my >= thumbY && my <= thumbY + thumbH) {
                    draggingResThumb = true;
                    resDragStartY = (float) my;
                    resDragStartScroll = resScrollOffset;
                    return true;
                } else {
                    float ratio = (float) (my - RES_LIST_TOP - thumbH / 2) / (RES_LIST_HEIGHT - thumbH);
                    resScrollOffset = Math.max(0f, Math.min(resMaxScroll, ratio * resMaxScroll));
                    return true;
                }
            }

            // Клик по кнопкам паков
            for (Object e : resEntries) {
                if (e instanceof PackRow row) {
                    if (row.isSelected) {
                        if (row.showUp && isInside(mx, my, row.btnUpX, row.btnUpY, 18, 16)) {
                            java.util.Collections.swap(selectedPackIds, row.listIndex, row.listIndex - 1);
                            buildResourceEntries(); return true;
                        }
                        if (row.showDown && isInside(mx, my, row.btnDownX, row.btnDownY, 18, 16)) {
                            java.util.Collections.swap(selectedPackIds, row.listIndex, row.listIndex + 1);
                            buildResourceEntries(); return true;
                        }
                        if (isInside(mx, my, row.btnActionX, row.btnActionY, 20, 16)) {
                            selectedPackIds.remove(row.listIndex);
                            unselectedPackIds.add(row.id);
                            buildResourceEntries(); return true;
                        }
                    } else {
                        if (isInside(mx, my, row.btnActionX, row.btnActionY, 20, 16)) {
                            unselectedPackIds.remove(row.listIndex);
                            selectedPackIds.add(row.id);
                            buildResourceEntries(); return true;
                        }
                    }
                }
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingResThumb && button == 0) {
            float dy = (float) (mouseY - resDragStartY);
            float trackH = RES_LIST_HEIGHT;
            float thumbH = Math.max(20f, trackH * (trackH / (trackH + resMaxScroll)));
            float usableH = trackH - thumbH;
            if (usableH > 0) {
                float newScroll = resDragStartScroll + (dy / usableH) * resMaxScroll;
                resScrollOffset = Math.max(0f, Math.min(resMaxScroll, newScroll));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingResThumb) {
            draggingResThumb = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == TAB_RESOURCES) {
            int cardX = this.width / 2 - 180;
            int inX = cardX + 20;
            int inW = 320;
            if (mouseX >= inX && mouseX <= inX + inW && mouseY >= RES_LIST_TOP && mouseY <= RES_LIST_BOTTOM) {
                resScrollOffset = Math.max(0f, Math.min(resMaxScroll, resScrollOffset - (float) delta * 18f));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isTransitioning()) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void renderPaperCard(GuiGraphics gfx, int x, int y, int w, int h) {
        gfx.pose().pushPose();
        gfx.pose().translate(x + w / 2f, y + h / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.5f));
        gfx.pose().translate(-w / 2f, -h / 2f, 0);

        PaperRender.drawPaperCard(gfx, 0, 0, w, h, 1.0f, PaperRender.PAPER_LIGHT);
        PaperRender.drawPin(gfx, 22, 10, false);
        PaperRender.drawPin(gfx, w - 22, 10, true);

        Font font = this.font;
        String head = switch (tab) {
            case TAB_GAMEPLAY -> "§1 · ОСНОВНЫЕ ";
            case TAB_GRAPHICS -> "§2 · ГРАФИКА ";
            case TAB_CONTROLS -> "§3 · КЛАВИШИ ";
            case TAB_SOUND    -> "§4 · МИКШЕР ";
            case TAB_RESOURCES -> "§5 · РЕСУРСЫ ";
            default            -> " ";
        };
        String headTitle = switch (tab) {
            case TAB_GAMEPLAY -> "правила игры ";
            case TAB_GRAPHICS -> "графика ";
            case TAB_CONTROLS -> "управление ";
            case TAB_SOUND    -> "звук ";
            case TAB_RESOURCES -> "ресурсы ";
            default            -> " ";
        };
        gfx.drawString(font, head, 20, 14, PaperRender.INK_FADED, false);
        float hs = 1.4f;
        gfx.pose().pushPose();
        gfx.pose().translate(20, 22, 0);
        gfx.pose().scale(hs, hs, 1f);
        gfx.drawString(font, headTitle, 0, 0, PaperRender.INK, false);
        gfx.pose().popPose();
        PaperRender.drawHandDivider(gfx, 20, 22 + (int)(9 * hs) + 4, w - 40,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.7f));

        gfx.pose().popPose();
    }

    @Override
    public void onClose() {
        // Автоматически применяем паки при закрытии меню, если они были изменены
        if (selectedPackIds != null) {
            applyResourcePacks();
        }
        this.minecraft.setScreen(parent);
    }

    // ==========================================
    // КАСТОМНЫЕ ВИДЖЕТЫ ДЛЯ РЕСУРС-ПАКОВ
    // ==========================================
    private class PaperHeader extends AbstractWidget {
        public PaperHeader(int x, int y, int w, String text) {
            super(x, y, w, 16, Component.literal(text));
        }
        @Override
        protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            gfx.fill(getX(), getY() + 9, getX() + getWidth(), getY() + 10, PaperRender.withAlpha(PaperRender.INK_FADED, 0.3f));
            gfx.drawString(font, getMessage().getString(), getX() + 4, getY() + 2, PaperRender.INK_FADED, false);
        }
        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
        @Override
        public boolean isMouseOver(double mx, double my) { return false; }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) { return false; }
    }

    private class PaperLabel extends AbstractWidget {
        public PaperLabel(int x, int y, int w, int h, String text) {
            super(x, y, w, h, Component.literal(text));
        }
        @Override
        protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            gfx.drawString(font, getMessage().getString(), getX() + 4, getY() + 4, PaperRender.INK, false);
        }
        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
        @Override
        public boolean isMouseOver(double mx, double my) { return false; }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) { return false; }
    }
}