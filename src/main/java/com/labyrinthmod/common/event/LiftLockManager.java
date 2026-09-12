package com.labyrinthmod.common.event;

import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.S2CLiftLockPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class LiftLockManager {

    private static volatile boolean isLocked = false;
    private static UUID initiator = null;
    private static final Set<UUID> waitingPlayers = new HashSet<>();

    public static void lock(UUID initiatorUuid) {
        if (isLocked) return;
        isLocked = true;
        initiator = initiatorUuid;
        System.out.println("[LiftLock] Мир заблокирован. Инициатор: " + initiatorUuid);
    }

    public static void unlockAndReset(MinecraftServer server) {
        if (!isLocked) return;
        isLocked = false;
        System.out.println("[LiftLock] Мир разблокирован. Освобождение " + waitingPlayers.size() + " игроков.");

        for (UUID uuid : waitingPlayers) {
            if (server != null) {
                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                if (p != null) {
                    unfreezePlayer(p);
                    NetworkHandler.sendToPlayer(p, new S2CLiftLockPacket(false));
                    LiftCommandHandler.addToPending(uuid);
                }
            }
        }
        waitingPlayers.clear();
        initiator = null;
    }

    public static void freezeAndHold(ServerPlayer player) {
        waitingPlayers.add(player.getUUID());
        player.setInvulnerable(true);
        player.setInvisible(true);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(0, -40, 0);
        NetworkHandler.sendToPlayer(player, new S2CLiftLockPacket(true));
    }

    public static void unfreezePlayer(ServerPlayer player) {
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }

    public static boolean isLocked() { return isLocked; }
    public static UUID getInitiator() { return initiator; }

    public static void removeWaiting(UUID uuid) {
        waitingPlayers.remove(uuid);
    }

    public static boolean isWaiting(UUID uuid) {
        return waitingPlayers.contains(uuid);
    }
}