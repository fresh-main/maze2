package com.labyrinthmod.client.screen;

import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.AdminActionPacket;
import com.labyrinthmod.common.network.packet.OpenAdminMenuPacket;
import com.labyrinthmod.common.network.packet.SyncAdminDataPacket;
import com.labyrinthmod.common.network.packet.UpdateSettingsPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class AdminScreen extends Screen {

    private List<BlockPos> points;
    private BlockPos spawnPoint;
    private int minDistance;
    private int maxDistance;
    private int revisitCooldown;
    private long emergenceTime;
    private boolean timeBasedEnabled;
    private boolean patrolActive;
    private List<OpenAdminMenuPacket.GriverSnapshot> grivers;
    private List<OpenAdminMenuPacket.PlayerSnapshot> players;

    // Карта лабиринта
    private BlockPos boundsMin, boundsMax;
    private int mapDataWidth, mapDataHeight;
    private byte[] mapData;
    private List<OpenAdminMenuPacket.ZoneSnapshot> exclusionZones;

    private static final int MAX_MAP_SIZE = 500;

    // Кэш текстуры карты
    private DynamicTexture mapTexture;
    private ResourceLocation mapTextureLoc;

    private EditBox minDistanceField;
    private EditBox maxDistanceField;
    private EditBox cooldownField;
    private EditBox emergenceField;
    private EditBox mapNameField;
    private EditBox spacingField;
    private Checkbox timeBasedCheckbox;
    private Button startStopButton;

    // Состояние карты
    private double mapCenterX, mapCenterZ;
    private double mapScale = 0.5;
    private int mapX, mapY, mapW, mapH;

    // Выбранный гривер
    private int selectedGriverEntityId = -1;
    private int refreshCooldown = 0;

    public AdminScreen(OpenAdminMenuPacket data) {
        super(Component.literal("Labyrinth Admin Menu"));
        applyData(data);
    }

    private boolean firstOpen = true;

    private void applyData(OpenAdminMenuPacket data) {
        this.points = data.points;
        this.spawnPoint = data.spawnPoint;
        this.minDistance = data.minDistance;
        this.maxDistance = data.maxDistance;
        this.revisitCooldown = data.revisitCooldown;
        this.emergenceTime = data.emergenceTime;
        this.timeBasedEnabled = data.timeBasedEnabled;
        this.patrolActive = data.patrolActive;
        this.players = data.players;

        // ТОЛЬКО ПРИ ПЕРВОМ ОТКРЫТИИ обновляем карту и гриверов
        if (firstOpen || data.mapData != null && data.mapData.length > 0) {
            this.grivers = data.grivers;
            this.boundsMin = data.boundsMin;
            this.boundsMax = data.boundsMax;
            this.mapDataWidth = data.mapWidth;
            this.mapDataHeight = data.mapHeight;
            this.mapData = data.mapData;
            this.exclusionZones = data.exclusionZones;
            buildMapTexture();
            firstOpen = false;
        } else {
            // Только обновляем список гриверов (лёгкие данные)
            this.grivers = data.grivers;
        }
    }

    private void buildMapTexture() {
        if (mapTexture != null) {
            mapTexture.close();
            mapTexture = null;
            mapTextureLoc = null;
        }
        if (mapData == null || mapData.length == 0 || mapDataWidth <= 0 || mapDataHeight <= 0) return;

        com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(mapDataWidth, mapDataHeight, false);
        for (int i = 0; i < mapDataWidth; i++) {
            for (int j = 0; j < mapDataHeight; j++) {
                boolean walkable = mapData[i * mapDataHeight + j] != 0;
                int color = walkable ? 0xFF3A6E3A : 0xFFBBBBBB;
                img.setPixelRGBA(i, j, color);
            }
        }
        mapTexture = new DynamicTexture(img);
        mapTextureLoc = Minecraft.getInstance().getTextureManager().register("labyrinthmod_map", mapTexture);
    }

    @Override
    public void removed() {
        super.removed();
        if (mapTexture != null) {
            mapTexture.close();
            mapTexture = null;
        }
    }

    public void updateLive(SyncAdminDataPacket p) {
        this.grivers = p.grivers;
        this.players = p.players;
        this.patrolActive = p.patrolActive;
        if (p.points != null) this.points = p.points;
        if (p.spawnPoint != null) this.spawnPoint = p.spawnPoint;
        if (startStopButton != null) {
            startStopButton.setMessage(Component.literal(patrolActive ? "Остановить патруль" : "Запустить патруль"));
        }
    }

    @Override
    protected void init() {
        super.init();

        // Слева: список гриверов. Центр: карта. Справа: настройки.
        int listW = 140;
        int rightW = 200;
        int margin = 10;

        mapX = listW + margin;
        mapY = 40;
        mapW = this.width - listW - rightW - margin * 2;
        mapH = this.height - 80;

        int rightX = this.width - rightW;
        int y = 40;

        // ========== Настройки справа ==========
        minDistanceField = new EditBox(this.font, rightX, y, rightW - margin, 20, Component.literal(""));
        minDistanceField.setValue(String.valueOf(minDistance));
        addRenderableWidget(minDistanceField);
        y += 25;

        maxDistanceField = new EditBox(this.font, rightX, y, rightW - margin, 20, Component.literal(""));
        maxDistanceField.setValue(String.valueOf(maxDistance));
        addRenderableWidget(maxDistanceField);
        y += 25;

        cooldownField = new EditBox(this.font, rightX, y, rightW - margin, 20, Component.literal(""));
        cooldownField.setValue(String.valueOf(revisitCooldown));
        addRenderableWidget(cooldownField);
        y += 25;

        emergenceField = new EditBox(this.font, rightX, y, rightW - margin, 20, Component.literal(""));
        emergenceField.setValue(String.valueOf(emergenceTime));
        addRenderableWidget(emergenceField);
        y += 25;

        // Пресеты времени
        int presetW = (rightW - margin - 10) / 3;
        addRenderableWidget(Button.builder(Component.literal("День"), b -> emergenceField.setValue("1000"))
                .bounds(rightX, y, presetW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Ночь"), b -> emergenceField.setValue("13000"))
                .bounds(rightX + presetW + 5, y, presetW, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Полночь"), b -> emergenceField.setValue("18000"))
                .bounds(rightX + (presetW + 5) * 2, y, presetW, 18).build());
        y += 23;

        timeBasedCheckbox = new Checkbox(rightX, y, rightW - margin, 20,
                Component.literal("Выход по времени"), timeBasedEnabled);
        addRenderableWidget(timeBasedCheckbox);
        y += 25;

        addRenderableWidget(Button.builder(Component.literal("Применить настройки"),
                        b -> applySettings())
                .bounds(rightX, y, rightW - margin, 20).build());
        y += 25;

        y += 10;

        // Управление патрулём
        startStopButton = Button.builder(
                        Component.literal(patrolActive ? "Остановить патруль" : "Запустить патруль"),
                        b -> {
                            // Локально переключаем состояние сразу — иначе двойной клик до прихода
                            // sync'а пошлёт ту же команду повторно. Сервер всё равно вернёт корректный
                            // patrolActive в ответном sync'е (в обработчиках START/STOP_PATROL).
                            AdminActionPacket.Action act = patrolActive
                                    ? AdminActionPacket.Action.STOP_PATROL
                                    : AdminActionPacket.Action.START_PATROL;
                            NetworkHandler.CHANNEL.sendToServer(new AdminActionPacket(act));
                            patrolActive = !patrolActive;
                            startStopButton.setMessage(Component.literal(
                                    patrolActive ? "Остановить патруль" : "Запустить патруль"));
                        })
                .bounds(rightX, y, rightW - margin, 22).build();
        addRenderableWidget(startStopButton);
        y += 27;

        // Расчёт точек: spacing + кнопка
        int spacingW = 60;
        spacingField = new EditBox(this.font, rightX, y, spacingW, 20, Component.literal(""));
        spacingField.setValue("8");
        spacingField.setHint(Component.literal("шаг"));
        addRenderableWidget(spacingField);
        addRenderableWidget(Button.builder(Component.literal("Расчитать точки"),
                        b -> {
                            int sp;
                            try { sp = Integer.parseInt(spacingField.getValue()); } catch (Exception e) { sp = 8; }
                            if (sp < 1) sp = 1;
                            NetworkHandler.CHANNEL.sendToServer(new AdminActionPacket(
                                    AdminActionPacket.Action.RECALCULATE_POINTS, sp));
                        })
                .bounds(rightX + spacingW + 5, y, rightW - margin - spacingW - 5, 20).build());
        y += 25;

        addRenderableWidget(Button.builder(Component.literal("Очистить точки"),
                        b -> NetworkHandler.CHANNEL.sendToServer(new AdminActionPacket(AdminActionPacket.Action.CLEAR_POINTS)))
                .bounds(rightX, y, rightW - margin, 20).build());
        y += 25;

        // Импорт/экспорт
        y += 5;
        mapNameField = new EditBox(this.font, rightX, y, rightW - margin, 20, Component.literal(""));
        mapNameField.setValue("default");
        mapNameField.setHint(Component.literal("имя карты"));
        addRenderableWidget(mapNameField);
        y += 25;

        int halfW = (rightW - margin - 5) / 2;
        addRenderableWidget(Button.builder(Component.literal("Экспорт"),
                        b -> NetworkHandler.CHANNEL.sendToServer(new AdminActionPacket(
                                AdminActionPacket.Action.EXPORT_MAP, mapNameField.getValue())))
                .bounds(rightX, y, halfW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Импорт"),
                        b -> NetworkHandler.CHANNEL.sendToServer(new AdminActionPacket(
                                AdminActionPacket.Action.IMPORT_MAP, mapNameField.getValue())))
                .bounds(rightX + halfW + 5, y, halfW, 20).build());
        y += 25;

        // ========== Кнопки действий слева под списком ==========
        int leftButtonsY = this.height - 65;
        addRenderableWidget(Button.builder(Component.literal("Телепорт к гриверу"),
                        b -> {
                            if (selectedGriverEntityId >= 0) {
                                NetworkHandler.CHANNEL.sendToServer(new AdminActionPacket(
                                        AdminActionPacket.Action.TELEPORT_TO_GRIVER, selectedGriverEntityId));
                            }
                        })
                .bounds(margin, leftButtonsY, listW, 20).build());
        leftButtonsY += 22;
        addRenderableWidget(Button.builder(Component.literal("Убить гривера"),
                        b -> {
                            if (selectedGriverEntityId >= 0) {
                                NetworkHandler.CHANNEL.sendToServer(new AdminActionPacket(
                                        AdminActionPacket.Action.KILL_GRIVER, selectedGriverEntityId));
                            }
                        })
                .bounds(margin, leftButtonsY, listW, 20).build());
    }

    private void applySettings() {
        int md, maxd, cd;
        long em;
        try { md = Integer.parseInt(minDistanceField.getValue()); } catch (Exception e) { md = minDistance; }
        try { maxd = Integer.parseInt(maxDistanceField.getValue()); } catch (Exception e) { maxd = maxDistance; }
        try { cd = Integer.parseInt(cooldownField.getValue()); } catch (Exception e) { cd = revisitCooldown; }
        try { em = Long.parseLong(emergenceField.getValue()); } catch (Exception e) { em = emergenceTime; }

        Minecraft.getInstance().player.sendSystemMessage(Component.literal(
                "§7[Patrol] Отправка: min=" + md + " max=" + maxd + " cd=" + cd + " em=" + em));

        NetworkHandler.CHANNEL.sendToServer(new UpdateSettingsPacket(md, maxd, cd, em, timeBasedCheckbox.selected()));
    }

    /** Запоминаем какую цель отправляли в прошлом sync — чтобы триггерить
     *  внеочередной запрос при смене выделения. */
    private int lastSyncedGriverId = -2;

    @Override
    public void tick() {
        super.tick();
        // Увеличиваем интервал с 10 тиков (0.5 сек) до 100 тиков (5 секунд).
        // Если игрок сменил выделение — запрашиваем сразу, чтобы новый путь/зона
        // подгрузились без задержки в 5 секунд.
        refreshCooldown++;
        boolean selectionChanged = (selectedGriverEntityId != lastSyncedGriverId);
        if (refreshCooldown >= 100 || selectionChanged) {
            refreshCooldown = 0;
            lastSyncedGriverId = selectedGriverEntityId;
            // intArg = entity id выделенного гривера. Сервер посчитает тяжёлый
            // displayPath ТОЛЬКО для него, остальным шлёт пустые поля.
            NetworkHandler.CHANNEL.sendToServer(new AdminActionPacket(
                    AdminActionPacket.Action.REQUEST_SYNC, selectedGriverEntityId));
        }
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        this.renderBackground(gfx);

        // Заголовок
        gfx.drawString(this.font, "§6§lLabyrinth Admin", 10, 10, 0xFFFFFF);
        gfx.drawString(this.font, "Патруль: " + (patrolActive ? "§aАКТИВЕН" : "§cОСТАНОВЛЕН")
                + " §7| Точек: §f" + (points != null ? points.size() : 0), 10, 24, 0xFFFFFF);

        // Список гриверов
        renderGriverList(gfx, mouseX, mouseY, 10, 40, 140);

        // Карта
        renderMap(gfx, mouseX, mouseY);

        // Подписи к полям настроек
        int rightX = this.width - 200;
        gfx.drawString(this.font, "Мин. дистанция (блоки):", rightX, 30, 0xAAAAAA);
        gfx.drawString(this.font, "Радиус цели (блоки):", rightX, 55, 0xAAAAAA);
        gfx.drawString(this.font, "Cooldown посещений:", rightX, 80, 0xAAAAAA);
        gfx.drawString(this.font, "Время выхода (0-24000):", rightX, 105, 0xAAAAAA);

        super.render(gfx, mouseX, mouseY, partial);
    }

    private void renderGriverList(GuiGraphics gfx, int mx, int my, int x, int y, int w) {
        gfx.fill(x, y, x + w, y + 220, 0x80000000);
        gfx.drawString(this.font, "Гриверы: " + grivers.size(), x + 3, y + 3, 0xFFFF55);

        int itemY = y + 15;
        for (int i = 0; i < grivers.size() && i < 15; i++) {
            var g = grivers.get(i);
            int color = (g.entityId == selectedGriverEntityId) ? 0x80FFFF00 : 0x40FFFFFF;

            boolean hover = mx >= x && mx < x + w && my >= itemY && my < itemY + 13;
            if (hover) color = 0x80FFFFFF;

            gfx.fill(x + 1, itemY, x + w - 1, itemY + 13, color);

            String name = "#" + i + " HP:" + (int) g.health + "/" + (int) g.maxHealth;
            gfx.drawString(this.font, name, x + 3, itemY + 3, 0xFFFFFF);
            itemY += 14;
        }
    }

    private void renderMap(GuiGraphics gfx, int mx, int my) {
        gfx.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xFF1A1A1A);
        gfx.renderOutline(mapX, mapY, mapW, mapH, 0xFF666666);

        gfx.enableScissor(mapX + 1, mapY + 1, mapX + mapW - 1, mapY + mapH - 1);

        int centerX = mapX + mapW / 2;
        int centerZ = mapY + mapH / 2;

        if (mapTextureLoc != null && boundsMin != null && boundsMax != null && mapData != null) {
            int minX = Math.min(boundsMin.getX(), boundsMax.getX());
            int minZ = Math.min(boundsMin.getZ(), boundsMax.getZ());
            int maxX = Math.max(boundsMin.getX(), boundsMax.getX());
            int maxZ = Math.max(boundsMin.getZ(), boundsMax.getZ());

            int startX = centerX + (int) ((minX - mapCenterX) * mapScale);
            int startZ = centerZ + (int) ((minZ - mapCenterZ) * mapScale);
            int width = (int) ((maxX - minX + 1) * mapScale);
            int height = (int) ((maxZ - minZ + 1) * mapScale);

            if (width > 0 && height > 0) {
                gfx.blit(mapTextureLoc, startX, startZ, width, height,
                        0f, 0f, mapDataWidth, mapDataHeight, mapDataWidth, mapDataHeight);
            }
        }

        // Границы
        if (boundsMin != null && boundsMax != null) {
            int minX = Math.min(boundsMin.getX(), boundsMax.getX());
            int minZ = Math.min(boundsMin.getZ(), boundsMax.getZ());
            int maxX = Math.max(boundsMin.getX(), boundsMax.getX());
            int maxZ = Math.max(boundsMin.getZ(), boundsMax.getZ());
            int x1 = centerX + (int) ((minX - mapCenterX) * mapScale);
            int z1 = centerZ + (int) ((minZ - mapCenterZ) * mapScale);
            int x2 = centerX + (int) ((maxX + 1 - mapCenterX) * mapScale);
            int z2 = centerZ + (int) ((maxZ + 1 - mapCenterZ) * mapScale);
            drawRectOutline(gfx, x1, z1, x2, z2, 0xFF00AAFF);
        }

        // Зоны исключения
        if (exclusionZones != null) {
            for (var z : exclusionZones) {
                int x1 = centerX + (int) ((z.minX - mapCenterX) * mapScale);
                int z1 = centerZ + (int) ((z.minZ - mapCenterZ) * mapScale);
                int x2 = centerX + (int) ((z.maxX + 1 - mapCenterX) * mapScale);
                int z2 = centerZ + (int) ((z.maxZ + 1 - mapCenterZ) * mapScale);
                gfx.fill(x1, z1, x2, z2, 0x50FF4444);
                drawRectOutline(gfx, x1, z1, x2, z2, 0xFFFF4444);
            }
        }

        // Точки патрулирования
        int pointSize = mapScale < 0.3 ? 1 : 2;
        for (BlockPos p : points) {
            int px = centerX + (int) ((p.getX() - mapCenterX) * mapScale);
            int pz = centerZ + (int) ((p.getZ() - mapCenterZ) * mapScale);
            gfx.fill(px - pointSize, pz - pointSize, px + pointSize + 1, pz + pointSize + 1, 0xFF55FF55);
        }

        // Спавн
        if (spawnPoint != null) {
            int px = centerX + (int) ((spawnPoint.getX() - mapCenterX) * mapScale);
            int pz = centerZ + (int) ((spawnPoint.getZ() - mapCenterZ) * mapScale);
            gfx.fill(px - 3, pz - 3, px + 4, pz + 4, 0xFFFFDD00);
        }

        // ВЫДЕЛЕННЫЙ ГРИВЕР: его зона + путь по лабиринту + цель.
        OpenAdminMenuPacket.GriverSnapshot selected = null;
        for (var g : grivers) if (g.entityId == selectedGriverEntityId) { selected = g; break; }

        if (selected != null) {
            // Зона: прямоугольник в которой работает гривер.
            if (selected.zoneBounds != null) {
                int zx1 = centerX + (int) ((selected.zoneBounds[0] - mapCenterX) * mapScale);
                int zz1 = centerZ + (int) ((selected.zoneBounds[2] - mapCenterZ) * mapScale);
                int zx2 = centerX + (int) ((selected.zoneBounds[1] + 1 - mapCenterX) * mapScale);
                int zz2 = centerZ + (int) ((selected.zoneBounds[3] + 1 - mapCenterZ) * mapScale);
                gfx.fill(zx1, zz1, zx2, zz2, 0x3000FFFF);
                drawRectOutline(gfx, zx1, zz1, zx2, zz2, 0xFF00FFFF);
            }

            // Путь по лабиринту (block-by-block). pathWaypoints теперь = displayPath.
            if (selected.pathWaypoints != null && selected.pathWaypoints.size() > 1) {
                int prevX = centerX + (int) ((selected.pos.getX() - mapCenterX) * mapScale);
                int prevZ = centerZ + (int) ((selected.pos.getZ() - mapCenterZ) * mapScale);
                for (BlockPos step : selected.pathWaypoints) {
                    int sx = centerX + (int) ((step.getX() - mapCenterX) * mapScale);
                    int sz = centerZ + (int) ((step.getZ() - mapCenterZ) * mapScale);
                    drawLine(gfx, prevX, prevZ, sx, sz, 0xFFFFFF00);
                    prevX = sx; prevZ = sz;
                }
            }

            // Цель — крестик.
            if (selected.currentTarget != null) {
                int tx = centerX + (int) ((selected.currentTarget.getX() - mapCenterX) * mapScale);
                int tz = centerZ + (int) ((selected.currentTarget.getZ() - mapCenterZ) * mapScale);
                gfx.fill(tx - 4, tz - 1, tx + 5, tz + 1, 0xFFFF8000);
                gfx.fill(tx - 1, tz - 4, tx + 1, tz + 5, 0xFFFF8000);
            }
        }

        // Гриверы
        for (var g : grivers) {
            int px = centerX + (int) ((g.pos.getX() - mapCenterX) * mapScale);
            int pz = centerZ + (int) ((g.pos.getZ() - mapCenterZ) * mapScale);
            boolean isSelected = g.entityId == selectedGriverEntityId;
            int col = isSelected ? 0xFFFF00FF : 0xFFFF5555;
            gfx.fill(px - 3, pz - 3, px + 4, pz + 4, col);
        }

        // Игроки
        if (players != null) {
            for (var pl : players) {
                int px = centerX + (int) ((pl.pos.getX() - mapCenterX) * mapScale);
                int pz = centerZ + (int) ((pl.pos.getZ() - mapCenterZ) * mapScale);
                gfx.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFF55AAFF);
            }
        }

        gfx.disableScissor();

        gfx.drawString(this.font, "Scale: " + String.format("%.2f", mapScale), mapX + 4, mapY + 4, 0xAAAAAA);
        gfx.drawString(this.font, "Центр: " + (int) mapCenterX + "," + (int) mapCenterZ, mapX + 4, mapY + 14, 0xAAAAAA);
        gfx.drawString(this.font, "Колёсико: зум, ПКМ: drag", mapX + 4, mapY + mapH - 12, 0x808080);
    }

    private void drawRectOutline(GuiGraphics gfx, int x1, int z1, int x2, int z2, int color) {
        gfx.fill(x1, z1, x2, z1 + 1, color);
        gfx.fill(x1, z2 - 1, x2, z2, color);
        gfx.fill(x1, z1, x1 + 1, z2, color);
        gfx.fill(x2 - 1, z1, x2, z2, color);
    }

    private static int hsvToRgb(int h, float s, float v) {
        float hf = h % 360 / 60f;
        int i = (int) hf;
        float f = hf - i;
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private void drawLine(GuiGraphics gfx, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int x = x1, y = y1;
        int steps = 0;
        while (steps++ < 500) {
            if (x >= mapX && x < mapX + mapW && y >= mapY && y < mapY + mapH) {
                gfx.fill(x, y, x + 1, y + 1, color);
            }
            if (x == x2 && y == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = 10, y = 40 + 15;
        int w = 140;
        for (int i = 0; i < grivers.size() && i < 15; i++) {
            if (mx >= x && mx < x + w && my >= y && my < y + 13) {
                selectedGriverEntityId = grivers.get(i).entityId;
                return true;
            }
            y += 14;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 1 && mx >= mapX && mx < mapX + mapW && my >= mapY && my < mapY + mapH) {
            mapCenterX -= dx / mapScale;
            mapCenterZ -= dy / mapScale;
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= mapX && mx < mapX + mapW && my >= mapY && my < mapY + mapH) {
            mapScale *= (delta > 0) ? 1.25 : 0.8;
            mapScale = Math.max(0.05, Math.min(5.0, mapScale));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}