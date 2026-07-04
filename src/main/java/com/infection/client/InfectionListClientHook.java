package com.infection.client;

import com.infection.client.gui.InfectionListScreen;
import com.infection.network.packet.S2CInfectionListPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Изолятор клиентского кода для S2CInfectionListPacket. Грузится только когда
 * invokestatic openWithEntries реально срабатывает — а это происходит лишь внутри
 * DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...), т.е. на клиенте.
 *
 * Если бы вызов InfectionListScreen и Minecraft.getInstance() стояли прямо в
 * S2CInfectionListPacket.handle — Forge RuntimeDistCleaner на dedicated server
 * рухнул бы при загрузке класса пакета (Screen помечен @OnlyIn(CLIENT)).
 */
@OnlyIn(Dist.CLIENT)
public final class InfectionListClientHook {

    private InfectionListClientHook() {}

    public static void openWithEntries(List<S2CInfectionListPacket.Entry> entries) {
        for (S2CInfectionListPacket.Entry e : entries) {
            ClientInfectionCache.put(e.id(), e.level(), e.noteText(), e.customNoteText(),
                    e.hallucinationsSuppressedUntil());
        }
        Minecraft.getInstance().setScreen(new InfectionListScreen(entries));
    }
}
