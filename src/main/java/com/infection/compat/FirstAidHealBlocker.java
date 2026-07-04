package com.infection.compat;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.network.MessageUpdatePart;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Класс, который ссылается на First Aid. Грузится только если FirstAidCompat.isLoaded() == true
 * (иначе JVM ClassLoader не встретит эти импорты).
 *
 * Логика:
 *   - На каждый серверный тик для заражённого на 100% игрока берём его damage-model.
 *   - Сравниваем currentHealth каждого part с сохранённым «уровнем-базой» (snapshot).
 *   - Если currentHealth > snapshot — кто-то вылечил. Откатываем currentHealth к snapshot,
 *     принудительно синкаем клиенту пакетом MessageUpdatePart.
 *   - Если &lt;= snapshot — обновляем snapshot (урон прошёл).
 *
 * Это безусловная защита: работает даже если миксин не применился.
 */
public final class FirstAidHealBlocker {

    private static final Map<UUID, EnumMap<EnumPlayerPart, Float>> SNAPSHOTS = new ConcurrentHashMap<>();

    private FirstAidHealBlocker() {}

    public static void revertIfInfected(ServerPlayer player, int infectionLevel) {
        if (infectionLevel < 100) {
            SNAPSHOTS.remove(player.getUUID());
            return;
        }

        AbstractPlayerDamageModel model;
        try {
            model = CommonUtils.getDamageModel(player);
        } catch (Throwable t) {
            return;
        }
        if (model == null) return;

        EnumMap<EnumPlayerPart, Float> snap = SNAPSHOTS.computeIfAbsent(player.getUUID(),
                k -> new EnumMap<>(EnumPlayerPart.class));

        boolean anyReverted = false;
        for (AbstractDamageablePart part : model) {
            Float stored = snap.get(part.part);
            if (stored != null && part.currentHealth > stored + 0.001f) {
                part.currentHealth = stored;
                syncPart(player, part);
                anyReverted = true;
            } else {
                snap.put(part.part, part.currentHealth);
            }
        }

        if (anyReverted) {
            try {
                model.scheduleResync();
            } catch (Throwable ignored) {}
        }
    }

    private static void syncPart(ServerPlayer player, AbstractDamageablePart part) {
        try {
            FirstAid.NETWORKING.send(PacketDistributor.PLAYER.with(() -> player),
                    new MessageUpdatePart(part));
        } catch (Throwable ignored) {}
    }

    public static void forget(UUID playerId) {
        SNAPSHOTS.remove(playerId);
    }
}
