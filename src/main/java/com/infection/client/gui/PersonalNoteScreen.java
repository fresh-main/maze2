package com.infection.client.gui;

import com.infection.client.ClientInfectionCache;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Экран чтения личной записки-самочувствия. Текст берётся из ClientInfectionCache
 * (синхронизируется с сервера). Если текст пустой — показываем шаблонную «всё нормально».
 */
public class PersonalNoteScreen extends Screen {

    private static final int PAPER_W = 280;
    private static final int PAPER_H = 220;
    private static final int PAGE_PAD = 18;

    private final UUID ownerId;
    private List<String> wrappedLines = List.of();

    public PersonalNoteScreen(UUID ownerId) {
        super(Component.literal("Записка"));
        this.ownerId = ownerId;
    }

    @Override
    protected void init() {
        super.init();
        rebuildText();

        addRenderableWidget(Button.builder(Component.literal("Закрыть"),
                        b -> onClose())
                .bounds(this.width / 2 - 50, (this.height + PAPER_H) / 2 + 8, 100, 20)
                .build());
    }

    private void rebuildText() {
        String raw = ClientInfectionCache.getNoteText(ownerId);
        if (raw == null || raw.isEmpty()) {
            raw = "Сегодня всё нормально.\nГолова ясная, руки слушаются.";
        }
        int maxW = PAPER_W - PAGE_PAD * 2;
        List<String> out = new ArrayList<>();
        for (String paragraph : raw.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                out.add("");
                continue;
            }
            // word-wrap по ширине шрифта
            String[] words = paragraph.split(" ");
            StringBuilder cur = new StringBuilder();
            for (String w : words) {
                String trial = cur.length() == 0 ? w : cur + " " + w;
                if (this.font.width(trial) > maxW && cur.length() > 0) {
                    out.add(cur.toString());
                    cur = new StringBuilder(w);
                } else {
                    cur = new StringBuilder(trial);
                }
            }
            if (cur.length() > 0) out.add(cur.toString());
        }
        wrappedLines = out;
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx);

        int paperX = (this.width - PAPER_W) / 2;
        int paperY = (this.height - PAPER_H) / 2;

        // фон-«бумага»
        gfx.fill(paperX - 2, paperY - 2, paperX + PAPER_W + 2, paperY + PAPER_H + 2, 0xFF1A1410);
        gfx.fill(paperX, paperY, paperX + PAPER_W, paperY + PAPER_H, 0xFFE9DCB9);
        // тёмная окантовка
        gfx.fill(paperX, paperY, paperX + PAPER_W, paperY + 1, 0xFF6B5842);
        gfx.fill(paperX, paperY + PAPER_H - 1, paperX + PAPER_W, paperY + PAPER_H, 0xFF6B5842);
        gfx.fill(paperX, paperY, paperX + 1, paperY + PAPER_H, 0xFF6B5842);
        gfx.fill(paperX + PAPER_W - 1, paperY, paperX + PAPER_W, paperY + PAPER_H, 0xFF6B5842);

        // заголовок
        String title = "Записка";
        int tw = this.font.width(title);
        gfx.drawString(this.font, title, paperX + (PAPER_W - tw) / 2, paperY + 8, 0xFF2A1810, false);
        // линия под заголовком
        gfx.fill(paperX + PAGE_PAD, paperY + 22, paperX + PAPER_W - PAGE_PAD, paperY + 23, 0xFF7A1F1F);

        // текст
        int textY = paperY + 32;
        int lineH = 11;
        for (String line : wrappedLines) {
            if (textY + lineH > paperY + PAPER_H - PAGE_PAD) break;
            gfx.drawString(this.font, line, paperX + PAGE_PAD, textY, 0xFF2A1810, false);
            textY += lineH;
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
