package com.infection.capability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Унифицированное получение уровня заражения.
 * На сервере — берётся из capability (authoritative).
 * На клиенте — из ClientInfectionCache (обновляется S2C sync-пакетом).
 */
public final class InfectionQuery {

    private InfectionQuery() {}

    public static int getLevel(Player player) {
        if (player == null) return 0;
        if (player instanceof ServerPlayer) {
            return InfectionProvider.get(player).map(IInfection::getLevel).orElse(0);
        }
        // Клиентская сторона — читаем кеш.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return com.infection.client.ClientInfectionCache.get(player.getUUID());
        }
        return InfectionProvider.get(player).map(IInfection::getLevel).orElse(0);
    }
}
