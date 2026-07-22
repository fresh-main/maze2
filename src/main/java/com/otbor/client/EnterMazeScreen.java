package com.otbor.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets; // ★ ДОБАВЛЕНО для исправления кодировки
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class EnterMazeScreen extends Screen {
    private final Screen parent;

    private List<ServerEntry> servers = new ArrayList<>();
    private int currentServerIndex = 0;

    private static final File SERVERS_FILE = new File(
            Minecraft.getInstance().gameDirectory, "config/otbor/servers.json");

    private float slideProgress = 0f;
    private boolean isSliding = false;
    private int slideDirection = 0;
    private long slideStartTime = 0;
    private static final long SLIDE_DURATION = 350;
    private int previousServerIndex = 0;

    private ServerStatusPinger pinger;
    private ServerData currentServerData;
    private int playerCount = 0;
    private int maxPlayers = 0;
    private int ping = -1;
    private long lastPingTime = 0;
    private boolean isPinging = false;
    private boolean pingFailed = false;
    private boolean firstPingDone = false;

    private EditBox nicknameBox;
    private String currentNickname = "";

    private int btnBackX, btnBackY, btnBackW, btnBackH;
    private int btnAddX, btnAddY, btnAddW, btnAddH;
    private int btnDirectX, btnDirectY, btnDirectW, btnDirectH; // ★ НОВАЯ КНОПКА
    private boolean hoverBack = false, hoverAdd = false, hoverDirect = false; // ★ НОВАЯ ПЕРЕМЕННАЯ

    private int btnEnterX, btnEnterY, btnEnterW, btnEnterH;
    private boolean hoverEnter = false;

    private final int arrowSize = 27;
    private int arrowLeftX, arrowRightX, arrowY;
    private boolean hoverLeft = false, hoverRight = false;

    public static class ServerEntry {
        public String name;
        public String ip;
        public String description;

        public ServerEntry() {}

        public ServerEntry(String name, String ip, String description) {
            this.name = name;
            this.ip = ip;
            this.description = description;
        }
    }

    public EnterMazeScreen(Screen parent) {
        super(Component.literal("ВОЙТИ В ЛАБИРИНТ"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        loadServers();
        if (currentServerIndex >= servers.size()) currentServerIndex = 0;

        String playerName = Minecraft.getInstance().getUser().getName();
        if (playerName == null || playerName.isEmpty()) {
            playerName = "Бегущий";
        }
        currentNickname = playerName;

        int cx = this.width / 2;
        int leftW = 260;
        int leftH = 300;
        int leftX = cx - leftW - 18;
        int leftY = 70;

        nicknameBox = new EditBox(this.font, 0, 0, leftW - 32, 18, Component.literal("nickname"));
        nicknameBox.setValue(currentNickname);
        nicknameBox.setBordered(false);
        nicknameBox.setTextColor(PaperRender.INK_RED);
        nicknameBox.setMaxLength(20);

        btnEnterW = leftW - 32;
        btnEnterH = 24;
        btnEnterX = 16;
        btnEnterY = leftH - 78;

        btnBackW = 100; btnBackH = 22;
        btnBackX = 30; btnBackY = 30;

        // ★ ИЗМЕНЕНО: Две кнопки рядом по 130px с зазором 4px (в сумме 264px, центрированы)
        int btnW = 130;
        int btnH = 36;
        int btnY = this.height - btnH - 30;

        btnAddW = btnW; btnAddH = btnH;
        btnAddX = cx - btnW - 2;
        btnAddY = btnY;

        btnDirectW = btnW; btnDirectH = btnH;
        btnDirectX = cx + 2;
        btnDirectY = btnY;

        arrowY = leftY + leftH / 2 - arrowSize / 2;
        arrowLeftX = leftX - 35;
        arrowRightX = leftX + leftW + 8;

        firstPingDone = false;
        pingFailed = false;
        ping = -1;
        playerCount = 0;
        maxPlayers = 0;
        initServerPinger();
    }

    private void loadServers() {
        servers.clear();
        try {
            if (SERVERS_FILE.exists()) {
                // ★ ИСПРАВЛЕНИЕ КОДИРОВКИ: Явное чтение как UTF-8
                String json = new String(Files.readAllBytes(SERVERS_FILE.toPath()), StandardCharsets.UTF_8);
                List<ServerEntry> loaded = new Gson().fromJson(json, new TypeToken<List<ServerEntry>>(){}.getType());
                if (loaded != null && !loaded.isEmpty()) {
                    servers.addAll(loaded);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (servers.isEmpty()) {
            servers.add(new ServerEntry("Глэйд", "87.228.57.198:30017", "Основной лабиринт"));
            saveServers();
        }
    }

    private void saveServers() {
        try {
            SERVERS_FILE.getParentFile().mkdirs();
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(servers);
            // ★ ИСПРАВЛЕНИЕ КОДИРОВКИ: Явная запись как UTF-8 (безопасный метод Java 11+)
            Files.writeString(SERVERS_FILE.toPath(), json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addServerAndSave(ServerEntry entry) {
        servers.add(entry);
        saveServers();
        currentServerIndex = servers.size() - 1;
        firstPingDone = false;
        pingFailed = false;
        ping = -1;
        initServerPinger();
    }

    private void initServerPinger() {
        if (servers.isEmpty()) return;
        ServerEntry entry = servers.get(currentServerIndex);
        currentServerData = new ServerData(entry.name, entry.ip, false);
        pinger = new ServerStatusPinger();
        lastPingTime = System.currentTimeMillis();
        pingServer();
    }

    private void pingServer() {
        if (isPinging || pinger == null) return;

        isPinging = true;
        pingFailed = false;

        Thread pingThread = new Thread(() -> {
            try {
                pinger.pingServer(currentServerData, () -> {
                    if (currentServerData.players != null) {
                        playerCount = currentServerData.players.online();
                        maxPlayers = currentServerData.players.max();
                    }
                    ping = (int) currentServerData.ping;
                    pingFailed = false;
                    firstPingDone = true;
                    isPinging = false;
                });

                long startTime = System.currentTimeMillis();
                while (isPinging && System.currentTimeMillis() - startTime < 5000) {
                    pinger.tick();
                    Thread.sleep(50);
                }

                if (isPinging) {
                    pingFailed = true;
                    firstPingDone = true;
                    isPinging = false;
                }
            } catch (Exception e) {
                pingFailed = true;
                firstPingDone = true;
                isPinging = false;
            }
        });
        pingThread.setDaemon(true);
        pingThread.start();
    }

    private void connectToServer() {
        if (servers.isEmpty()) return;
        ServerEntry entry = servers.get(currentServerIndex);
        ServerData data = new ServerData(entry.name, entry.ip, false);
        ConnectScreen.startConnecting(parent, Minecraft.getInstance(),
                ServerAddress.parseString(entry.ip), data, false);
    }

    private void openAddServerScreen() {
        this.minecraft.setScreen(new AddLabyrinthScreen(this, (entry) -> {
            addServerAndSave(entry);
        }));
    }

    // ★ НОВЫЙ МЕТОД: Открытие экрана прямого подключения
    private void openDirectConnectScreen() {
        this.minecraft.setScreen(new DirectConnectMazeScreen(this));
    }

    private void flipServer(int direction) {
        if (isSliding || servers.size() <= 1) return;

        isSliding = true;
        slideDirection = direction;
        slideProgress = 0f;
        slideStartTime = System.currentTimeMillis();
        previousServerIndex = currentServerIndex;

        currentServerIndex += direction;
        if (currentServerIndex < 0) currentServerIndex = servers.size() - 1;
        else if (currentServerIndex >= servers.size()) currentServerIndex = 0;

        firstPingDone = false;
        pingFailed = false;
        ping = -1;
        playerCount = 0;
        maxPlayers = 0;

        ServerEntry entry = servers.get(currentServerIndex);
        currentServerData = new ServerData(entry.name, entry.ip, false);
        lastPingTime = System.currentTimeMillis();
    }

    @Override
    public void tick() {
        super.tick();

        if (isSliding) {
            long elapsed = System.currentTimeMillis() - slideStartTime;
            slideProgress = Math.min(1f, elapsed / (float) SLIDE_DURATION);
            if (slideProgress >= 1f) {
                isSliding = false;
                slideProgress = 0f;
                pingServer();
            }
        }

        if (pinger != null && isPinging) {
            pinger.tick();
        }
    }

    private String getServerStatusText() {
        if (isSliding) return "перелистывание...";
        if (isPinging && !firstPingDone) return "проверка...";
        if (isPinging && firstPingDone) return "обновление...";
        if (pingFailed) return "недоступен";
        if (currentServerData != null && currentServerData.status != null) return "онлайн";
        if (ping >= 0) return "онлайн";
        return "выключен";
    }

    private int getServerStatusColor() {
        String status = getServerStatusText();
        switch (status) {
            case "онлайн": return PaperRender.INK;
            case "выключен":
            case "недоступен": return PaperRender.INK_RED;
            default: return PaperRender.INK_FADED;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        PaperRender.drawBoardBackground(gfx, this.width, this.height);

        Font font = this.font;
        String kicker = "ФАЙЛ №01 · ПРОТОКОЛ ПОДКЛЮЧЕНИЯ";
        int kickerW = font.width(kicker);
        gfx.drawString(font, kicker, this.width / 2 - kickerW / 2, 34,
                0xFFB8A581, false);

        String title = "ВОЙТИ В ЛАБИРИНТ";
        float ts = 2.2f;
        int tw = (int) (font.width(title) * ts);
        gfx.pose().pushPose();
        gfx.pose().translate(this.width / 2f - tw / 2f, 44, 0);
        gfx.pose().scale(ts, ts, 1f);
        gfx.drawString(font, title, 0, 0, PaperRender.PAPER_LIGHT, false);
        gfx.pose().popPose();

        int cx = this.width / 2;
        int leftW = 260;
        int leftH = 260;
        int leftX = cx - leftW - 18;
        int leftY = 70;

        // ★ ИЗМЕНЕНО: Отрисовка двух кнопок рядом
        renderCustomButton(gfx, btnBackX, btnBackY, btnBackW, btnBackH, " <- НАЗАД", hoverBack, PaperRender.INK_SOFT);
        renderCustomButton(gfx, btnAddX, btnAddY, btnAddW, btnAddH, "+ ДОБАВИТЬ", hoverAdd, PaperRender.INK_RED);
        renderCustomButton(gfx, btnDirectX, btnDirectY, btnDirectW, btnDirectH, "ВОЙТИ ПО IP", hoverDirect, PaperRender.INK_RED);

        int rightW = 220;
        int rightH = 230;
        int rightX = cx + 18;
        int rightY = 90;
        renderRulesMemo(gfx, rightX, rightY, rightW, rightH);

        if (servers.size() > 1) {
            hoverLeft = !isSliding
                    && mouseX >= arrowLeftX && mouseX <= arrowLeftX + arrowSize
                    && mouseY >= arrowY && mouseY <= arrowY + arrowSize;
            hoverRight = !isSliding
                    && mouseX >= arrowRightX && mouseX <= arrowRightX + arrowSize
                    && mouseY >= arrowY && mouseY <= arrowY + arrowSize;

            renderArrowButton(gfx, arrowLeftX, arrowY, arrowSize, "<", hoverLeft);
            renderArrowButton(gfx, arrowRightX, arrowY, arrowSize, ">", hoverRight);
        }

        if (isSliding) {
            renderServerCardWithSlide(gfx, leftX, leftY, leftW, leftH,
                    currentServerIndex, 0f, 1f, mouseX, mouseY);
            renderServerCardWithSlide(gfx, leftX, leftY, leftW, leftH,
                    previousServerIndex, slideDirection * slideProgress, 1f - slideProgress, mouseX, mouseY);
        } else {
            renderServerCardWithSlide(gfx, leftX, leftY, leftW, leftH,
                    currentServerIndex, 0f, 1f, mouseX, mouseY);
        }

        if (!servers.isEmpty()) {
            String counter = (currentServerIndex + 1) + " / " + servers.size();
            int counterW = font.width(counter);
            gfx.drawString(font, counter, leftX + leftW / 2 - counterW / 2,
                    leftY + leftH + 8, PaperRender.INK_FADED, false);
        }

        // ★ ИЗМЕНЕНО: Обновление hover для всех трех кнопок
        hoverBack = mouseX >= btnBackX && mouseX <= btnBackX + btnBackW
                && mouseY >= btnBackY && mouseY <= btnBackY + btnBackH;
        hoverAdd = mouseX >= btnAddX && mouseX <= btnAddX + btnAddW
                && mouseY >= btnAddY && mouseY <= btnAddY + btnAddH;
        hoverDirect = mouseX >= btnDirectX && mouseX <= btnDirectX + btnDirectW
                && mouseY >= btnDirectY && mouseY <= btnDirectY + btnDirectH;
    }

    private void renderCustomButton(GuiGraphics gfx, int x, int y, int w, int h,
                                    String text, boolean hovered, int borderColor) {
        int bg = hovered ? PaperRender.PAPER_BASE : PaperRender.PAPER_DARK;
        int border = hovered ? PaperRender.INK_RED : borderColor;

        gfx.fill(x, y, x + w, y + h, bg);
        gfx.fill(x, y, x + w, y + 1, border);
        gfx.fill(x, y + h - 1, x + w, y + h, border);
        gfx.fill(x, y, x + 1, y + h, border);
        gfx.fill(x + w - 1, y, x + w, y + h, border);

        Font font = this.font;
        int tw = font.width(text);
        int color = hovered ? PaperRender.INK_RED : PaperRender.INK;
        gfx.drawString(font, text, x + w / 2 - tw / 2, y + (h - 8) / 2, color, false);
    }

    private void renderServerCardWithSlide(GuiGraphics gfx, int x, int y, int w, int h,
                                           int serverIndex, float slideOffset, float alpha,
                                           int mouseX, int mouseY) {
        if (servers.isEmpty() || serverIndex < 0 || serverIndex >= servers.size()) return;
        if (alpha <= 0.01f) return;

        ServerEntry entry = servers.get(serverIndex);

        gfx.pose().pushPose();

        int slidePixels = (int) (slideOffset * w * 1.2f);
        gfx.pose().translate(slidePixels, 0, 0);

        gfx.pose().translate(x + w / 2f, y + h / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-1.8f));
        gfx.pose().translate(-w / 2f, -h / 2f, 0);

        PaperRender.drawPaper(gfx, 0, 0, w, h, 1.0f, PaperRender.PAPER_BASE);
        PaperRender.drawPin(gfx, 16, 12, false);
        PaperRender.drawPin(gfx, w - 16, 12, true);

        Font font = this.font;

        String kick = "ПРИВЯЗАННЫЙ УЗЕЛ · ТОЛЬКО ОДИН";
        gfx.drawString(font, kick, 14, 14, PaperRender.INK_FADED, false);

        String heading = "СЕРВЕР \"" + entry.name + "\"";
        float hs = 1.7f;
        gfx.pose().pushPose();
        gfx.pose().translate(14, 26, 0);
        gfx.pose().scale(hs, hs, 1f);
        gfx.drawString(font, heading, 0, 0, PaperRender.INK, false);
        gfx.pose().popPose();

        PaperRender.drawHandDivider(gfx, 14, 26 + (int)(9*hs) + 6, w - 28,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.8f));

        int dy = 26 + (int)(9*hs) + 16;
        int lineH = 12;

        gfx.drawString(font, entry.description, 16, dy, PaperRender.INK_SOFT, false);
        dy += lineH + 4;

        if (serverIndex == currentServerIndex || !isSliding) {
            String statusText = getServerStatusText();
            int statusColor = getServerStatusColor();
            drawKvColored(gfx, font, "СТАТУС: ", statusText, 16, dy, statusColor);
            dy += lineH;

            String onlineText = (firstPingDone && !pingFailed)
                    ? (playerCount + "/" + maxPlayers + " игроков")
                    : "—";
            drawKv(gfx, font, "ОНЛАЙН: ", onlineText, 16, dy);
            dy += lineH;

            String pingText = (firstPingDone && !pingFailed && ping >= 0)
                    ? (ping + " ms")
                    : "—";
            drawKv(gfx, font, "ПИНГ: ", pingText, 16, dy);
            dy += lineH;
        } else {
            dy += lineH * 3;
        }

        PaperRender.drawHandDivider(gfx, 14, dy + 4, w - 28,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.8f));

        gfx.drawString(font, "твоё имя, бегущий:", 16, dy + 14, PaperRender.INK, false);

        int nickY = h - 62;
        int nickX = 16;
        int nickW = w - 32;
        int nickH = 18;

        nicknameBox.setX(nickX);
        nicknameBox.setY(nickY);
        nicknameBox.setWidth(nickW);
        nicknameBox.setValue(currentNickname);
        nicknameBox.render(gfx, mouseX, mouseY, 0); // ★ ИСПРАВЛЕНО: добавлены mouseX, mouseY для корректного рендера курсора

        gfx.fill(nickX, nickY + nickH, nickX + nickW, nickY + nickH + 1, PaperRender.INK);

        int btnX = btnEnterX;
        int btnY = btnEnterY;
        int btnW = btnEnterW;
        int btnH = btnEnterH;

        boolean isCurrentCard = (serverIndex == currentServerIndex && !isSliding);
        hoverEnter = isCurrentCard && !isSliding
                && mouseX >= (x + btnX) && mouseX <= (x + btnX + btnW)
                && mouseY >= (y + btnY) && mouseY <= (y + btnY + btnH);

        int bg = hoverEnter ? PaperRender.PAPER_BASE : PaperRender.PAPER_DARK;
        int border = hoverEnter ? PaperRender.INK_RED : PaperRender.INK_RED;

        gfx.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
        gfx.fill(btnX, btnY, btnX + btnW, btnY + 1, border);
        gfx.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, border);
        gfx.fill(btnX, btnY, btnX + 1, btnY + btnH, border);
        gfx.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, border);

        String btnText = "-> ВОЙТИ В ЛАБИРИНТ <-";
        int tw = font.width(btnText);
        int color = hoverEnter ? PaperRender.INK_RED : PaperRender.INK;
        gfx.drawString(font, btnText, btnX + btnW / 2 - tw / 2, btnY + (btnH - 8) / 2, color, false);

        gfx.pose().popPose();
    }

    private void drawKv(GuiGraphics gfx, Font font, String key, String val, int x, int y) {
        gfx.drawString(font, key, x, y, PaperRender.INK_SOFT, false);
        int keyW = font.width(key);
        gfx.drawString(font, val, x + keyW + 4, y, PaperRender.INK, false);
    }

    private void drawKvColored(GuiGraphics gfx, Font font, String key, String val, int x, int y, int valColor) {
        gfx.drawString(font, key, x, y, PaperRender.INK_SOFT, false);
        int keyW = font.width(key);
        gfx.drawString(font, val, x + keyW + 4, y, valColor, false);
    }

    private void renderArrowButton(GuiGraphics gfx, int x, int y, int size, String text, boolean hovered) {
        int bg = hovered ? PaperRender.PAPER_BASE : PaperRender.PAPER_DARK;
        int border = hovered ? PaperRender.INK_RED : PaperRender.INK_SOFT;

        gfx.fill(x, y, x + size, y + size, bg);
        gfx.fill(x, y, x + size, y + 1, border);
        gfx.fill(x, y + size - 1, x + size, y + size, border);
        gfx.fill(x, y, x + 1, y + size, border);
        gfx.fill(x + size - 1, y, x + size, y + size, border);

        Font font = this.font;
        int tw = font.width(text);
        int color = hovered ? PaperRender.INK_RED : PaperRender.INK;
        gfx.drawString(font, text, x + size / 2 - tw / 2, y + size / 2 - 4, color, false);
    }

    private void renderRulesMemo(GuiGraphics gfx, int x, int y, int w, int h) {
        Font font = this.font;
        gfx.pose().pushPose();
        gfx.pose().translate(x + w / 2f, y + h / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(2.4f));
        gfx.pose().translate(-w / 2f, -h / 2f, 0);

        PaperRender.drawPaper(gfx, 0, 0, w, h, 1.0f, PaperRender.PAPER_LIGHT);
        PaperRender.drawTape(gfx, -6, 6, 48, 12, 0xB0);

        gfx.drawString(font, "ПАМЯТКА БЕГУЩЕМУ", 12, 12, PaperRender.INK_FADED, false);

        String t = "три правила";
        float ts = 1.6f;
        gfx.pose().pushPose();
        gfx.pose().translate(12, 22, 0);
        gfx.pose().scale(ts, ts, 1f);
        gfx.drawString(font, t, 0, 0, PaperRender.INK, false);
        gfx.pose().popPose();

        int ry = 22 + (int)(9*ts) + 10;
        String[] rules = {
                "1) Никогда не выходи за стены",
                "   до сигнала.",
                "",
                "2) Всегда будь готов бежать.",
                "",
                "3) НИКОГДА не трогай",
                "   гриверов.",
        };
        for (String r : rules) {
            gfx.drawString(font, r, 14, ry, PaperRender.INK, false);
            ry += 11;
        }

        PaperRender.drawHandDivider(gfx, 14, ry + 4, w - 28,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.7f));

        gfx.drawString(font, "если читаешь это -", 14, ry + 14, PaperRender.INK_RED, false);
        gfx.drawString(font, "ты один из нас.", 14, ry + 24, PaperRender.INK_RED, false);

        gfx.drawString(font, "— Ньют", w - 60, h - 16, PaperRender.INK_FADED, false);

        gfx.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= btnBackX && mx <= btnBackX + btnBackW && my >= btnBackY && my <= btnBackY + btnBackH) {
            this.minecraft.setScreen(parent);
            return true;
        }

        if (mx >= btnAddX && mx <= btnAddX + btnAddW && my >= btnAddY && my <= btnAddY + btnAddH) {
            openAddServerScreen();
            return true;
        }

        // ★ ДОБАВЛЕНО: Обработка клика по новой кнопке
        if (mx >= btnDirectX && mx <= btnDirectX + btnDirectW && my >= btnDirectY && my <= btnDirectY + btnDirectH) {
            openDirectConnectScreen();
            return true;
        }

        if (!isSliding && servers.size() > 1) {
            if (hoverLeft) {
                flipServer(-1);
                return true;
            }
            if (hoverRight) {
                flipServer(1);
                return true;
            }
        }

        if (hoverEnter && !isSliding) {
            connectToServer();
            return true;
        }

        if (nicknameBox.isMouseOver(mx, my)) {
            nicknameBox.setFocused(true);
            nicknameBox.mouseClicked(mx, my, button);
            return true;
        } else {
            nicknameBox.setFocused(false);
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nicknameBox.isFocused()) {
            currentNickname = nicknameBox.getValue();
            return nicknameBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (nicknameBox.isFocused()) {
            currentNickname = nicknameBox.getValue();
            return nicknameBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        pinger = null;
        this.minecraft.setScreen(parent);
    }
}