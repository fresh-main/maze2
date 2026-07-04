package com.labyrinthmod.client;

import com.labyrinthmod.client.screen.AdminScreen;
import com.labyrinthmod.client.screen.FractionRevealScreen;
import com.labyrinthmod.client.screen.ImposterScreen;
import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.network.packet.FractionRevealPacket;
import com.labyrinthmod.common.network.packet.OpenImposterScreenPacket;
import com.labyrinthmod.common.network.packet.SyncAdminDataPacket;
import com.labyrinthmod.common.network.packet.SyncFractionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-only обработчики PLAY_TO_CLIENT пакетов.
 *
 * ВАЖНО: этот класс ссылается на client-only классы (Minecraft, Screen, *Screen),
 * поэтому общие пакеты должны вызывать его ТОЛЬКО через DistExecutor c двойной
 * лямбдой — иначе RuntimeDistCleaner упадёт при загрузке регистратора пакетов
 * на dedicated server.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    public static void handleSyncFraction(SyncFractionPacket p) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
            FractionType f = FractionType.fromId(p.fractionId);
            data.setFraction(f != null ? f : FractionType.NONE);
            data.setImposterMask(p.mask.isEmpty() ? null : p.mask);
        });
    }

    public static void handleSyncAdminData(SyncAdminDataPacket p) {
        Screen s = Minecraft.getInstance().screen;
        if (s instanceof AdminScreen as) as.updateLive(p);
    }

    public static void handleOpenImposterScreen(OpenImposterScreenPacket p) {
        Minecraft.getInstance().setScreen(new ImposterScreen(p.players));
    }

    public static void handleFractionReveal(FractionRevealPacket p) {
        Minecraft.getInstance().setScreen(new FractionRevealScreen(p.fractionId, p.maskFraction));
    }

    public static void handleFractionAccessSync(
            com.labyrinthmod.common.network.packet.S2CFractionAccessSyncPacket p) {
        // Если уже открыт экран доступа фракций — обновляем live.
        // Если НЕ открыт — открываем ТОЛЬКО когда сервер явно сказал это (openScreen=true,
        // т.е. этот игрок САМ запросил через /zone access). Update-broadcast от другого
        // админа (openScreen=false) НЕ должен открывать GUI у тех кто ничего не просил.
        Screen s = Minecraft.getInstance().screen;
        if (s instanceof com.labyrinthmod.client.screen.FractionAccessScreen fa) {
            fa.applySnapshot(p.map);
        } else if (p.openScreen) {
            Minecraft.getInstance().setScreen(
                    new com.labyrinthmod.client.screen.FractionAccessScreen(s, p.map));
        }
        // иначе: клиент не запрашивал и не имеет открытым screen — игнор.
    }
}
