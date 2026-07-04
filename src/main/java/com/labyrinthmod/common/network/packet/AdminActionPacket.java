package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.entity.GriverEntity;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.patrol.PatrolManager;
import com.labyrinthmod.common.util.ModLogger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.io.File;
import java.util.function.Supplier;

/**
 * Клиент -> сервер: команды из админ-меню.
 */
public class AdminActionPacket {
    public enum Action {
        START_PATROL, STOP_PATROL,
        CLEAR_POINTS, REQUEST_SYNC, REQUEST_FULL_MAP,
        EXPORT_MAP, IMPORT_MAP,
        TELEPORT_TO_GRIVER, KILL_GRIVER,
        RECALCULATE_POINTS
    }

    public final Action action;
    public final String stringArg;
    public final int intArg;

    public AdminActionPacket(Action a) { this(a, "", 0); }
    public AdminActionPacket(Action a, String s) { this(a, s, 0); }
    public AdminActionPacket(Action a, int i) { this(a, "", i); }
    public AdminActionPacket(Action a, String s, int i) {
        this.action = a; this.stringArg = s; this.intArg = i;
    }

    public static void encode(AdminActionPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.action);
        buf.writeUtf(p.stringArg);
        buf.writeInt(p.intArg);
    }

    public static AdminActionPacket decode(FriendlyByteBuf buf) {
        return new AdminActionPacket(buf.readEnum(Action.class), buf.readUtf(), buf.readInt());
    }

    public static void handle(AdminActionPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (!player.hasPermissions(2) && !player.isCreative()) return;

            ServerLevel level = player.serverLevel();
            PatrolManager m = PatrolManager.get(level);
            if (m == null) return;

            if (p.action != Action.REQUEST_SYNC) {
                ModLogger.admin(player.getName().getString(), p.action.name(),
                        (p.stringArg.isEmpty() ? "" : "s=" + p.stringArg) + (p.intArg != 0 ? " i=" + p.intArg : ""));
            }

            switch (p.action) {
                case START_PATROL -> startGlobalPatrol(player, level, m);
                case STOP_PATROL -> {
                    stopGlobalPatrol(level, m);
                    sendSync(player, level, m, -1);
                }
                case CLEAR_POINTS -> {
                    m.clearPatrolPoints();
                    sendSync(player, level, m, -1, true); // points changed
                }
                // intArg = entity id выделенного гривера (или -1). Без этого сервер
                // считал тяжёлые пути для ВСЕХ гриверов на каждый клик → лагало.
                case REQUEST_SYNC -> sendSync(player, level, m, p.intArg);
                case REQUEST_FULL_MAP -> sendFullMap(player, level, m);
                case EXPORT_MAP -> exportMap(player, m, p.stringArg);
                case IMPORT_MAP -> importMap(player, m, p.stringArg);
                case TELEPORT_TO_GRIVER -> teleportToGriver(player, level, p.intArg);
                case KILL_GRIVER -> killGriver(level, p.intArg);
                case RECALCULATE_POINTS -> recalculatePoints(player, level, m, p.intArg);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void sendSync(ServerPlayer player, ServerLevel level, PatrolManager m, int selectedEntityId) {
        sendSync(player, level, m, selectedEntityId, false);
    }

    /**
     * @param includePoints если true — шлём весь список patrolPoints + spawnPoint (heavy:
     *        3000+ блокпозов = 24КБ network + сериализация). Если false — клиент держит
     *        свой кэш, points поле не передаётся вообще. Использовать true только при
     *        реальной смене точек (RECALC/CLEAR).
     */
    private static void sendSync(ServerPlayer player, ServerLevel level, PatrolManager m,
                                 int selectedEntityId, boolean includePoints) {
        var grivers = OpenAdminMenuPacket.snapshotGriversLight(level, m, selectedEntityId);
        var players = OpenAdminMenuPacket.snapshotPlayersLight(level);
        SyncAdminDataPacket pkt = includePoints
                ? new SyncAdminDataPacket(grivers, players, m.isGlobalPatrolActive(),
                        m.getPatrolPoints(), m.getSpawnPoint())
                : SyncAdminDataPacket.light(grivers, players, m.isGlobalPatrolActive());
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
    }

    private static void startGlobalPatrol(ServerPlayer player, ServerLevel level, PatrolManager m) {
        if (m.getPatrolPoints().isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "§c[Patrol] Нет точек патрулирования. Нажми «Расчитать точки» в меню или /griver autogen."));
            return;
        }
        m.setGlobalPatrolActive(true);
        AABB area = new AABB(-30000000, -1000, -30000000, 30000000, 1000, 30000000);

        java.util.List<GriverEntity> grivers = new java.util.ArrayList<>();
        java.util.List<java.util.UUID> ids = new java.util.ArrayList<>();
        java.util.Map<java.util.UUID, net.minecraft.core.BlockPos> positions = new java.util.HashMap<>();
        for (var e : level.getEntities(null, area)) {
            if (e instanceof GriverEntity g) {
                grivers.add(g);
                ids.add(g.getUUID());
                positions.put(g.getUUID(), g.blockPosition());
                // Сразу форсим чанк гривера через TicketType.FORCED, чтобы entity
                // оставалось в entity-tick state. Без этого на dedicated server'е
                // гривер вне симуляции игроков не тикает → не патрулирует.
                int gx = g.blockPosition().getX() >> 4;
                int gz = g.blockPosition().getZ() >> 4;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        level.setChunkForced(gx + dx, gz + dz, true);
                    }
                }
            }
        }

        if (grivers.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "§c[Patrol] В мире нет ни одного гривера. Заспавни их перед запуском."));
            // Активацию не отменяем: новые гриверы автоматически подключатся к патрулю.
        }

        // Глобальное планирование: сразу распределяем 5 точек каждому
        java.util.Map<java.util.UUID, java.util.List<net.minecraft.core.BlockPos>> plans =
                m.planAllGriversGlobally(ids, positions, PatrolManager.PLAN_LENGTH);

        ModLogger.patrol("global-plan-start", "grivers=" + grivers.size() + " points=" + m.getPatrolPoints().size());
        for (GriverEntity g : grivers) {
            java.util.List<net.minecraft.core.BlockPos> plan = plans.get(g.getUUID());
            int planSize = plan == null ? 0 : plan.size();
            int zoneSize = m.getGriverZone(g.getUUID()).size();
            ModLogger.patrol("global-plan", "griver=" + g.getUUID().toString().substring(0, 8)
                    + " zoneSize=" + zoneSize + " planSize=" + planSize);
            if (plan != null && !plan.isEmpty()) {
                g.applyExternalPlan(plan);
            } else {
                // ИСПРАВЛЕНО: используем resetAndJoinPatrol вместо joinGlobalPatrol
                g.resetAndJoinPatrol();
            }
        }

        player.sendSystemMessage(Component.literal(
                "§a[Patrol] Запущен. Гриверов: " + grivers.size() + ", точек: " + m.getPatrolPoints().size()));
        sendSync(player, level, m, -1);
    }

    private static void stopGlobalPatrol(ServerLevel level, PatrolManager m) {
        m.setGlobalPatrolActive(false);
        AABB area = new AABB(-30000000, -1000, -30000000, 30000000, 1000, 30000000);
        int stopped = 0;
        for (var e : level.getEntities(null, area)) {
            if (e instanceof GriverEntity g) {
                g.leaveGlobalPatrol();
                stopped++;
            }
        }
        ModLogger.patrol("global-stop", "stopped=" + stopped);
    }

    private static void recalculatePoints(ServerPlayer player, ServerLevel level, PatrolManager m, int spacing) {
        if (!m.hasBounds()) {
            player.sendSystemMessage(Component.literal(
                    "§c[Patrol] Сначала задайте границы лабиринта: /griver bounds min/max"));
            return;
        }
        int s = spacing > 0 ? spacing : 8;
        int floorY = player.blockPosition().getY();
        int before = m.getPatrolPoints().size();
        m.clearPatrolPoints();
        // yRadius = 0: сканируем строго на этаже игрока, без вертикального диапазона.
        int added = m.autogeneratePoints(level, s, floorY, 0);
        if (added < 0) {
            player.sendSystemMessage(Component.literal("§c[Patrol] Ошибка автогенерации"));
            return;
        }
        player.sendSystemMessage(Component.literal(
                "§a[Patrol] Расчёт точек: §f" + added + " §7(было: " + before
                        + ", шаг: " + s + ", Y=" + floorY + ")"));
        sendSync(player, level, m, -1, true); // points changed
    }

    private static void teleportToGriver(ServerPlayer player, ServerLevel level, int entityId) {
        var e = level.getEntity(entityId);
        if (e != null) {
            player.teleportTo(level, e.getX(), e.getY() + 1, e.getZ(), player.getYRot(), player.getXRot());
        }
    }

    private static void killGriver(ServerLevel level, int entityId) {
        var e = level.getEntity(entityId);
        if (e instanceof GriverEntity g) g.kill();
    }

    private static void exportMap(ServerPlayer player, PatrolManager m, String name) {
        try {
            File f = m.getMapFile(name.isEmpty() ? "default" : name);
            CompoundTag tag = m.exportMapData();
            NbtIo.writeCompressed(tag, f);
            player.sendSystemMessage(Component.literal("§a[Patrol] Карта сохранена: " + f.getAbsolutePath()));
        } catch (Exception e) {
            LabyrinthMod.LOGGER.error("Export map failed", e);
            player.sendSystemMessage(Component.literal("§cОшибка экспорта: " + e.getMessage()));
        }
    }

    private static void importMap(ServerPlayer player, PatrolManager m, String name) {
        try {
            File f = m.getMapFile(name.isEmpty() ? "default" : name);
            if (!f.exists()) {
                player.sendSystemMessage(Component.literal("§cФайл не найден: " + f.getName()));
                return;
            }
            CompoundTag tag = NbtIo.readCompressed(f);
            m.importMapData(tag);
            player.sendSystemMessage(Component.literal("§a[Patrol] Карта загружена из " + f.getName()));
        } catch (Exception e) {
            LabyrinthMod.LOGGER.error("Import map failed", e);
            player.sendSystemMessage(Component.literal("§cОшибка импорта: " + e.getMessage()));
        }
    }

    private static void sendFullMap(ServerPlayer player, ServerLevel level, PatrolManager m) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                OpenAdminMenuPacket.fromServer(level, m));
    }
}
