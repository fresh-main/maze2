package com.labyrinthmod.client;

import com.labyrinthmod.common.event.ImmortalityWhitelist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.gui.components.AbstractButton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber(modid = "labyrinthmod", value = Dist.CLIENT)
public class FirstAidPaperStyleHandler {

    private static boolean isFirstAidScreen(Screen screen) {
        return screen != null && screen.getClass().getName().toLowerCase().contains("firstaid");
    }

    // Бумажная панель по размеру контента
    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        Screen screen = event.getScreen();
        if (!isFirstAidScreen(screen)) return;

        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int w = screen.width;
        int h = screen.height;

        // Тёмный фон за панелью
        g.fill(0, 0, w, h, 0xB0000000);

        // Границы всех кнопок
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget) {
                minX = Math.min(minX, widget.getX());
                minY = Math.min(minY, widget.getY());
                maxX = Math.max(maxX, widget.getX() + widget.getWidth());
                maxY = Math.max(maxY, widget.getY() + widget.getHeight());
            }
        }
        if (minX == Integer.MAX_VALUE) return;

        // Панель по размеру контента
        int px = minX - 55;
        int py = minY - 80;
        int pw = (maxX - minX) + 110;
        int ph = (maxY - minY) + 130;

        // Бумажная панель
        g.fill(px, py, px + pw, py + ph, 0xFFE8DCC4);
        g.renderOutline(px, py, pw, ph, 0xFF8B7355);
        g.renderOutline(px + 3, py + 3, pw - 6, ph - 6, 0xFFB4A488);

        // Заголовок
        g.drawCenteredString(mc.font, "ПЕРЕВЯЗКА", px + pw / 2, py + 10, 0xFF8B1E1E);
    }

    // Расширяем кнопки, чтобы текст помещался
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!isFirstAidScreen(screen)) return;

        Minecraft mc = Minecraft.getInstance();
        for (var child : screen.children()) {
            if (child instanceof AbstractButton button && button.getWidth() < 200) {
                int needW = mc.font.width(button.getMessage()) + 14;
                if (needW > button.getWidth()) {
                    boolean leftCol = button.getX() + button.getWidth() / 2 < screen.width / 2;
                    int oldRight = button.getX() + button.getWidth();
                    button.setWidth(needW);
                    // Левая колонка растёт ВЛЕВО (сердечки справа не закрываются),
                    // правая колонка растёт ВПРАВО (сердечки слева не закрываются)
                    if (leftCol) button.setX(oldRight - needW);
                }
            }
        }
    }

    // Сообщение "Бинт применён" + лечение
    @SubscribeEvent
    public static void onScreenClick(ScreenEvent.MouseButtonPressed.Post event) {
        Screen screen = event.getScreen();
        if (!isFirstAidScreen(screen)) return;

        double mx = event.getMouseX();
        double my = event.getMouseY();

        List<AbstractWidget> left = new ArrayList<>();
        List<AbstractWidget> right = new ArrayList<>();
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.getWidth() < 200) {
                if (widget.getX() + widget.getWidth() / 2 < screen.width / 2) left.add(widget);
                else right.add(widget);
            }
        }
        left.sort(Comparator.comparingInt(AbstractWidget::getY));
        right.sort(Comparator.comparingInt(AbstractWidget::getY));

        String[] leftNames = {"Голова", "Левая рука", "Левая нога", "Левая ступня"};
        String[] rightNames = {"Тело", "Правая рука", "Правая нога", "Правая ступня"};

        for (int i = 0; i < left.size() && i < leftNames.length; i++) {
            if (left.get(i).isMouseOver(mx, my)) {
                applyHeal("§aБинт применён: §6" + leftNames[i]);
                return;
            }
        }
        for (int i = 0; i < right.size() && i < rightNames.length; i++) {
            if (right.get(i).isMouseOver(mx, my)) {
                applyHeal("§aБинт применён: §6" + rightNames[i]);
                return;
            }
        }
    }

    private static void applyHeal(String message) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player != null && ImmortalityWhitelist.isWhitelisted(player)) {
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 2f));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        }
    }
}