package com.labyrinthmod.client.screen;

import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.ImposterAttackPacket;
import com.labyrinthmod.common.network.packet.OpenImposterScreenPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class ImposterScreen extends Screen {

    private final List<OpenImposterScreenPacket.PlayerInfo> players;
    private UUID selectedPlayerId = null;
    private String selectedPlayerName = null;
    private Button attackButton;
    private int scrollOffset = 0;
    private static final int PLAYERS_PER_PAGE = 10;

    public ImposterScreen(List<OpenImposterScreenPacket.PlayerInfo> players) {
        super(Component.literal("Выбор жертвы"));
        this.players = players;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 40;

        // Заголовок
        addRenderableWidget(Button.builder(
                        Component.literal("§c§lВЫБОР ЖЕРТВЫ"),
                        b -> {})
                .bounds(centerX - 100, startY - 20, 200, 20)
                .build()
        );

        // Список игроков
        int visibleCount = Math.min(players.size() - scrollOffset, PLAYERS_PER_PAGE);
        for (int i = 0; i < visibleCount; i++) {
            OpenImposterScreenPacket.PlayerInfo info = players.get(scrollOffset + i);
            final UUID playerId = info.uuid;
            final String playerName = info.name;
            final String fraction = info.fraction;

            addRenderableWidget(Button.builder(
                            Component.literal("§e" + playerName + " §7(" + fraction + ")"),
                            b -> {
                                selectedPlayerId = playerId;
                                selectedPlayerName = playerName;
                                attackButton.active = true;
                            })
                    .bounds(centerX - 120, startY + i * 25, 240, 20)
                    .build()
            );
        }

        // Кнопка атаки
        attackButton = addRenderableWidget(Button.builder(
                        Component.literal("§c§lНАПАСТЬ"),
                        b -> {
                            if (selectedPlayerId != null) {
                                NetworkHandler.CHANNEL.sendToServer(new ImposterAttackPacket(selectedPlayerId));
                                onClose();
                            }
                        })
                .bounds(centerX - 50, startY + PLAYERS_PER_PAGE * 25 + 20, 100, 20)
                .build()
        );
        attackButton.active = false;

        // Кнопка закрытия
        addRenderableWidget(Button.builder(
                                Component.literal("Закрыть"),
                                b -> onClose()
                        )
                        .bounds(centerX - 110, startY + PLAYERS_PER_PAGE * 25 + 20, 60, 20)
                        .build()
        );

        // Кнопка обновления
        addRenderableWidget(Button.builder(
                        Component.literal("§aОбновить"),
                        b -> {
                            // TODO: запрос на сервер
                        })
                .bounds(centerX + 60, startY + PLAYERS_PER_PAGE * 25 + 20, 60, 20)
                .build()
        );

        // Кнопки навигации
        if (scrollOffset > 0) {
            addRenderableWidget(Button.builder(
                                    Component.literal("▲"),
                                    b -> scrollOffset = Math.max(0, scrollOffset - PLAYERS_PER_PAGE)
                            )
                            .bounds(centerX + 130, startY, 20, 20)
                            .build()
            );
        }

        if (scrollOffset + PLAYERS_PER_PAGE < players.size()) {
            addRenderableWidget(Button.builder(
                                    Component.literal("▼"),
                                    b -> scrollOffset = Math.min(players.size() - PLAYERS_PER_PAGE, scrollOffset + PLAYERS_PER_PAGE)
                            )
                            .bounds(centerX + 130, startY + PLAYERS_PER_PAGE * 25 - 5, 20, 20)
                            .build()
            );
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);

        if (selectedPlayerName != null) {
            gfx.drawString(this.font,
                    "§aВыбрана цель: §e" + selectedPlayerName,
                    this.width / 2 - 100, this.height - 60, 0xFFFFFF);
        }

        gfx.drawString(this.font,
                "§c§lВНИМАНИЕ! После атаки будет кулдаун 1 час!",
                this.width / 2 - 150, this.height - 40, 0xFF5555);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}