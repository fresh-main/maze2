package com.labyrinthmod.common.event;

import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ImmortalityWhitelist {

    // Храним UUID игроков в белом списке
    private static final Set<UUID> whitelist = new HashSet<>();

    public static void addPlayer(UUID playerId) {
        whitelist.add(playerId);
    }

    public static void removePlayer(UUID playerId) {
        whitelist.remove(playerId);
    }

    public static boolean isWhitelisted(UUID playerId) {
        return whitelist.contains(playerId);
    }

    public static boolean isWhitelisted(Player player) {
        return whitelist.contains(player.getUUID());
    }

    public static Set<UUID> getAll() {
        return new HashSet<>(whitelist);
    }

    public static void clear() {
        whitelist.clear();
    }
}