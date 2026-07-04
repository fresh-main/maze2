package com.labyrinthmod.common.command;

import com.labyrinthmod.common.entity.GriverEntity;
import com.labyrinthmod.common.entity.GriverEntityType;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.OpenAdminMenuPacket;
import com.labyrinthmod.common.patrol.PatrolManager;
import com.labyrinthmod.common.util.ModLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GriverCommand {

    // Хранилище для игроков под атакой (UUID -> оставшиеся тики)
    private static final Map<UUID, Integer> attackedPlayers = new ConcurrentHashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("griver")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .executes(GriverCommand::spawnGriver)
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> spawnGriverAt(ctx, BlockPosArgument.getBlockPos(ctx, "pos")))))
                .then(Commands.literal("list").executes(GriverCommand::listAllGrivers))
                .then(Commands.literal("killall").executes(GriverCommand::killAll))
                .then(Commands.literal("admin").executes(GriverCommand::openAdmin))
                .then(Commands.literal("attack")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(GriverCommand::attackPlayer)))
                .then(Commands.literal("patrol")
                        .then(Commands.literal("add")
                                .executes(ctx -> addPoint(ctx, null))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> addPoint(ctx, BlockPosArgument.getBlockPos(ctx, "pos")))))
                        .then(Commands.literal("clear").executes(GriverCommand::clearPoints))
                        .then(Commands.literal("clearall").executes(GriverCommand::clearPoints))
                        .then(Commands.literal("removeall").executes(GriverCommand::clearPoints))
                        .then(Commands.literal("list").executes(GriverCommand::listPoints))
                        .then(Commands.literal("start").executes(GriverCommand::startGlobal))
                        .then(Commands.literal("stop").executes(GriverCommand::stopGlobal))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(GriverCommand::setSpawn)))
                        .then(Commands.literal("autogen")
                                .executes(ctx -> autogen(ctx, 8, 3))
                                .then(Commands.argument("spacing", IntegerArgumentType.integer(2, 100))
                                        .executes(ctx -> autogen(ctx, IntegerArgumentType.getInteger(ctx, "spacing"), 3))
                                        .then(Commands.argument("yRadius", IntegerArgumentType.integer(0, 30))
                                                .executes(ctx -> autogen(ctx, IntegerArgumentType.getInteger(ctx, "spacing"),
                                                        IntegerArgumentType.getInteger(ctx, "yRadius")))))))
                .then(Commands.literal("bounds")
                        .then(Commands.literal("min")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> setBoundsMin(ctx, BlockPosArgument.getBlockPos(ctx, "pos")))))
                        .then(Commands.literal("max")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> setBoundsMax(ctx, BlockPosArgument.getBlockPos(ctx, "pos")))))
                        .then(Commands.literal("clear").executes(GriverCommand::clearBounds))
                        .then(Commands.literal("info").executes(GriverCommand::boundsInfo)))
                .then(Commands.literal("exclude")
                        .then(Commands.literal("clear").executes(GriverCommand::clearExclusions))
                        .then(Commands.literal("list").executes(GriverCommand::listExclusions)))
                .then(Commands.literal("settings")
                        .then(Commands.literal("mindistance")
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(5, 1000))
                                        .executes(ctx -> setMinDistance(ctx, IntegerArgumentType.getInteger(ctx, "blocks")))))
                        .then(Commands.literal("maxdistance")
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(20, 5000))
                                        .executes(ctx -> setMaxDistance(ctx, IntegerArgumentType.getInteger(ctx, "blocks")))))
                        .then(Commands.literal("cooldown")
                                .then(Commands.argument("count", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> setCooldown(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                        .then(Commands.literal("time")
                                .then(Commands.argument("ticks", LongArgumentType.longArg(0, 23999))
                                        .executes(ctx -> setTime(ctx, LongArgumentType.getLong(ctx, "ticks"))))
                                .then(Commands.literal("day").executes(ctx -> setTime(ctx, 1000L)))
                                .then(Commands.literal("night").executes(ctx -> setTime(ctx, 13000L)))
                                .then(Commands.literal("midnight").executes(ctx -> setTime(ctx, 18000L))))
                        .then(Commands.literal("timebased")
                                .then(Commands.literal("on").executes(ctx -> setTimeBased(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> setTimeBased(ctx, false))))
                        .executes(GriverCommand::showSettings))
        );
    }

    // ========== НОВАЯ КОМАНДА АТАКИ ==========

    // ========== НОВАЯ КОМАНДА АТАКИ ==========

    private static int attackPlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            ServerLevel level = ctx.getSource().getLevel();

            int duration = 600; // 30 секунд (20 тиков * 30)

            // Запоминаем игрока в карте атакуемых
            attackedPlayers.put(target.getUUID(), duration);

            // Добавляем скрытый эффект (GLOWING - гриверы видят через стены)
            // Игрок не увидит эффект (ambient=true, showParticles=false, showIcon=false)
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, false, false, false));

            // Оповещаем всех гриверов о новой цели
            AABB area = new AABB(-30000000, -1000, -30000000, 30000000, 1000, 30000000);
            int griverCount = 0;

            for (var e : level.getEntities(null, area)) {
                if (e instanceof GriverEntity griver) {
                    // Заставляем гривера атаковать цель
                    griver.setForcedAttackTarget(target, duration);
                    griverCount++;
                }
            }

            final int finalGriverCount = griverCount; // <-- СОЗДАЁМ FINAL ПЕРЕМЕННУЮ

            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§aИгрок " + target.getName().getString() + " помечен для атаки гриверами на 30 секунд! §7(" + finalGriverCount + " гриверов)"), true);

            target.sendSystemMessage(Component.literal("§c§lВНИМАНИЕ! §cГриверы начали охоту на вас! Бегите!"));

            ModLogger.admin(ctx.getSource().getTextName(), "ATTACK_PLAYER",
                    "target=" + target.getName().getString() + " duration=30s grivers=" + finalGriverCount);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cОшибка: " + e.getMessage()));
            return 0;
        }
    }

    // Геттер для проверки, помечен ли игрок
    public static boolean isPlayerMarkedForAttack(UUID playerId) {
        return attackedPlayers.containsKey(playerId) && attackedPlayers.get(playerId) > 0;
    }

    public static int getPlayerMarkTime(UUID playerId) {
        return attackedPlayers.getOrDefault(playerId, 0);
    }

    // Обновление таймеров (вызывать из тика сервера)
    public static void tickAttackedPlayers() {
        attackedPlayers.entrySet().removeIf(entry -> {
            int newTime = entry.getValue() - 1;
            if (newTime <= 0) {
                return true;
            }
            entry.setValue(newTime);
            return false;
        });
    }

    // ========== SPAWN ==========

    private static int spawnGriver(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        return spawnGriverAt(ctx, player.blockPosition());
    }

    private static int spawnGriverAt(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        ServerLevel level = ctx.getSource().getLevel();
        GriverEntity griver = GriverEntityType.GRIVER.get().create(level);
        if (griver != null) {
            griver.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            level.addFreshEntity(griver);
            int id = griver.getId();
            ctx.getSource().sendSuccess(() -> Component.literal("§aГривер спавнен (ID: " + id + ")"), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§cНе удалось спавнить гривера"));
        return 0;
    }

    // ========== LIST/KILL ==========

    private static int listAllGrivers(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        List<GriverEntity> grivers = new ArrayList<>();
        AABB area = new AABB(-30000000, -1000, -30000000, 30000000, 1000, 30000000);
        for (var entity : level.getEntities(null, area)) {
            if (entity instanceof GriverEntity g) grivers.add(g);
        }
        if (grivers.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§eНет гриверов"), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§6=== Гриверы (" + grivers.size() + ") ==="), false);
        for (int i = 0; i < grivers.size(); i++) {
            GriverEntity g = grivers.get(i);
            final int idx = i;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§e#" + idx + " §7ID=" + g.getId() + " HP=" + (int) g.getHealth() + "/" + (int) g.getMaxHealth()
                            + " §7[" + (int) g.getX() + " " + (int) g.getY() + " " + (int) g.getZ() + "]"
                            + (g.isPatrolling() ? " §a[patrol]" : "")), false);
        }
        return 1;
    }

    private static int killAll(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int count = 0;
        AABB area = new AABB(-30000000, -1000, -30000000, 30000000, 1000, 30000000);
        for (var entity : level.getEntities(null, area)) {
            if (entity instanceof GriverEntity g) { g.kill(); count++; }
        }
        final int c = count;
        ctx.getSource().sendSuccess(() -> Component.literal("§cУбито гриверов: " + c), true);
        return 1;
    }

    // ========== ADMIN GUI ==========

    private static int openAdmin(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer sp = ctx.getSource().getPlayer();
        if (sp == null) return 0;
        PatrolManager m = PatrolManager.get(sp.serverLevel());
        if (m == null) return 0;
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                OpenAdminMenuPacket.fromServer(sp.serverLevel(), m));
        return 1;
    }

    // ========== ТОЧКИ ==========

    private static int addPoint(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        ServerLevel level = ctx.getSource().getLevel();
        PatrolManager m = PatrolManager.get(level);
        if (m == null) return 0;
        if (pos == null) {
            ServerPlayer p = ctx.getSource().getPlayer();
            if (p == null) return 0;
            pos = p.blockPosition();
        }
        final BlockPos fp = pos;
        m.addPatrolPoint(fp);
        ctx.getSource().sendSuccess(() -> Component.literal("§aТочка добавлена: " + fp.getX() + " " + fp.getY() + " " + fp.getZ()), true);
        return 1;
    }

    private static int clearPoints(CommandContext<CommandSourceStack> ctx) {
        PatrolManager m = PatrolManager.get(ctx.getSource().getLevel());
        if (m == null) return 0;
        m.clearPatrolPoints();
        ctx.getSource().sendSuccess(() -> Component.literal("§aТочки очищены"), true);
        return 1;
    }

    private static int listPoints(CommandContext<CommandSourceStack> ctx) {
        PatrolManager m = PatrolManager.get(ctx.getSource().getLevel());
        if (m == null) return 0;
        List<BlockPos> pts = m.getPatrolPoints();
        if (pts.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§eТочек нет"), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§6=== Точки (" + pts.size() + ") ==="), false);
        for (int i = 0; i < pts.size(); i++) {
            BlockPos p = pts.get(i);
            final int idx = i;
            ctx.getSource().sendSuccess(() -> Component.literal("§e#" + idx + " §7" + p.getX() + " " + p.getY() + " " + p.getZ()), false);
        }
        return 1;
    }

    private static int setSpawn(CommandContext<CommandSourceStack> ctx) {
        PatrolManager m = PatrolManager.get(ctx.getSource().getLevel());
        if (m == null) return 0;
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        m.setSpawnPoint(pos);
        ctx.getSource().sendSuccess(() -> Component.literal("§aТочка спавна: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), true);
        return 1;
    }

    // ========== СТАРТ/СТОП ==========

    // В GriverCommand.java - исправленный метод startGlobal

    private static int startGlobal(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        PatrolManager m = PatrolManager.get(level);
        if (m == null) return 0;

        List<BlockPos> points = m.getPatrolPoints();
        if (points.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§cНет точек патрулирования! Используйте /griver patrol autogen"));
            return 0;
        }

        m.setGlobalPatrolActive(true);

        List<GriverEntity> grivers = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        Map<UUID, BlockPos> positions = new HashMap<>();
        AABB area = new AABB(-30000000, -1000, -30000000, 30000000, 1000, 30000000);
        for (var e : level.getEntities(null, area)) {
            if (e instanceof GriverEntity g) {
                grivers.add(g);
                ids.add(g.getUUID());
                positions.put(g.getUUID(), g.blockPosition());
            }
        }

        if (grivers.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§cНет гриверов для патрулирования! Используйте спавнер"));
            return 0;
        }

        Map<UUID, List<BlockPos>> plans = m.planAllGriversGlobally(ids, positions, PatrolManager.PLAN_LENGTH);

        int count = 0;
        for (GriverEntity g : grivers) {
            List<BlockPos> plan = plans.get(g.getUUID());
            if (plan != null && !plan.isEmpty()) {
                g.applyExternalPlan(plan);
                count++;
            } else {
                g.resetAndJoinPatrol();  // ← ИЗМЕНЕНО: вместо joinGlobalPatrol()
                count++;
            }
        }

        final int finalCount = count;
        final int pointSize = points.size();
        ctx.getSource().sendSuccess(() -> Component.literal("§aПатруль запущен для " + finalCount + " гриверов (точек: " + pointSize + ")"), true);
        return 1;
    }

    private static int stopGlobal(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        PatrolManager m = PatrolManager.get(level);
        if (m == null) return 0;

        m.setGlobalPatrolActive(false);

        AABB area = new AABB(-30000000, -1000, -30000000, 30000000, 1000, 30000000);
        int count = 0;
        for (var e : level.getEntities(null, area)) {
            if (e instanceof GriverEntity g) {
                g.leaveGlobalPatrol();
                count++;
            }
        }

        final int finalCount = count;

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§cПатруль остановлен. " + finalCount + " гриверов возвращаются домой и НЕ БУДУТ АТАКОВАТЬ!"), true);

        return 1;
    }

    // ========== НАСТРОЙКИ ==========

    private static int setMinDistance(CommandContext<CommandSourceStack> ctx, int v) {
        PatrolManager m = PatrolManager.get(ctx.getSource().getLevel());
        if (m == null) return 0;
        m.setMinDistanceBetweenGrivers(v);
        ctx.getSource().sendSuccess(() -> Component.literal("§aМин. дистанция: " + v), true);
        return 1;
    }

    private static int setMaxDistance(CommandContext<CommandSourceStack> ctx, int v) {
        PatrolManager m = PatrolManager.get(ctx.getSource().getLevel());
        if (m == null) return 0;
        m.setMaxTargetDistance(v);
        ctx.getSource().sendSuccess(() -> Component.literal("§aРадиус цели: " + v), true);
        return 1;
    }

    private static int setCooldown(CommandContext<CommandSourceStack> ctx, int v) {
        PatrolManager m = PatrolManager.get(ctx.getSource().getLevel());
        if (m == null) return 0;
        m.setRevisitCooldown(v);
        ctx.getSource().sendSuccess(() -> Component.literal("§aCooldown: " + v), true);
        return 1;
    }

    private static int setTime(CommandContext<CommandSourceStack> ctx, long t) {
        PatrolManager m = PatrolManager.get(ctx.getSource().getLevel());
        if (m == null) return 0;
        m.setEmergenceTime(t);
        ctx.getSource().sendSuccess(() -> Component.literal("§aВремя выхода: " + t), true);
        return 1;
    }

    private static int setTimeBased(CommandContext<CommandSourceStack> ctx, boolean v) {
        PatrolManager m = PatrolManager.get(ctx.getSource().getLevel());
        if (m == null) return 0;
        m.setTimeBasedEmergenceEnabled(v);
        ctx.getSource().sendSuccess(() -> Component.literal("§aВыход по времени: " + (v ? "ON" : "OFF")), true);
        return 1;
    }

    // ========== BOUNDS ==========

    private static int setBoundsMin(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        PatrolManager m = PatrolManager.get(ctx.getSource().getLevel());
        if (m == null) return 0;
        m.setBoundsMin(pos);
        ctx.getSource().sendSuccess(() -> Component.literal("§aMin угол: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), true);
        return 1;
    }

    private static int setBoundsMax(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        PatrolManager m = PatrolManager.get(ctx.getSource().getLevel());
        if (m == null) return 0;
        m.setBoundsMax(pos);
        ctx.getSource().sendSuccess(() -> Component.literal("§aMax угол: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), true);
        return 1;
    }

    private static int clearBounds(CommandContext<CommandSourceStack> ctx) {
        PatrolManager m = PatrolManager.get(ctx.getSource().getLevel());
        if (m == null) return 0;
        m.clearBounds();
        ctx.getSource().sendSuccess(() -> Component.literal("§aГраницы сброшены"), true);
        return 1;
    }

    private static int boundsInfo(CommandContext<CommandSourceStack> ctx) {
        PatrolManager m = PatrolManager.get(ctx.getSource().getLevel());
        if (m == null) return 0;
        if (!m.hasBounds()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§eГраницы не заданы"), false);
            return 1;
        }
        BlockPos a = m.getBoundsMin();
        BlockPos b = m.getBoundsMax();
        int sx = Math.abs(a.getX() - b.getX()) + 1;
        int sz = Math.abs(a.getZ() - b.getZ()) + 1;
        ctx.getSource().sendSuccess(() -> Component.literal("§6Границы: §f" + a + " → " + b + " §7(" + sx + "×" + sz + ")"), false);
        return 1;
    }

    // ========== EXCLUSION ZONES ==========

    private static int clearExclusions(CommandContext<CommandSourceStack> ctx) {
        PatrolManager m = PatrolManager.get(ctx.getSource().getLevel());
        if (m == null) return 0;
        m.clearExclusionZones();
        ctx.getSource().sendSuccess(() -> Component.literal("§aЗоны исключения очищены"), true);
        return 1;
    }

    private static int listExclusions(CommandContext<CommandSourceStack> ctx) {
        PatrolManager m = PatrolManager.get(ctx.getSource().getLevel());
        if (m == null) return 0;
        var zones = m.getExclusionZones();
        if (zones.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§eНет зон исключения"), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§6=== Зоны исключения (" + zones.size() + ") ==="), false);
        for (int i = 0; i < zones.size(); i++) {
            var z = zones.get(i);
            final int idx = i;
            ctx.getSource().sendSuccess(() -> Component.literal("§e#" + idx + " §7["
                    + z.minX + "," + z.minY + "," + z.minZ + " → "
                    + z.maxX + "," + z.maxY + "," + z.maxZ + "]"), false);
        }
        return 1;
    }

    // ========== AUTOGEN ==========

    private static int autogen(CommandContext<CommandSourceStack> ctx, int spacing, int yRadius) {
        ServerLevel level = ctx.getSource().getLevel();
        PatrolManager m = PatrolManager.get(level);
        if (m == null) return 0;
        if (!m.hasBounds()) {
            ctx.getSource().sendFailure(Component.literal("§cСначала задайте границы: /griver bounds min/max"));
            return 0;
        }
        ServerPlayer p = ctx.getSource().getPlayer();
        int floorY = p != null ? p.blockPosition().getY() : 64;

        int before = m.getPatrolPoints().size();
        int added = m.autogeneratePoints(level, spacing, floorY, yRadius);
        if (added < 0) {
            ctx.getSource().sendFailure(Component.literal("§cОшибка автогенерации"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§aАвтогенерация: §f+" + added
                + " §7(всего: " + (before + added) + ", шаг: " + spacing + ", y±" + yRadius + ", floorY=" + floorY + ")"), true);
        return 1;
    }

    private static int showSettings(CommandContext<CommandSourceStack> ctx) {
        PatrolManager m = PatrolManager.get(ctx.getSource().getLevel());
        if (m == null) return 0;
        ctx.getSource().sendSuccess(() -> Component.literal("§6=== Настройки патруля ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eТочек: §f" + m.getPatrolPoints().size()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eМин. дистанция: §f" + m.getMinDistanceBetweenGrivers()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eРадиус цели: §f" + m.getMaxTargetDistance()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eCooldown: §f" + m.getRevisitCooldown()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eВремя выхода: §f" + m.getEmergenceTime()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eTimeBased: §f" + m.isTimeBasedEmergenceEnabled()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eАктивен: §f" + m.isGlobalPatrolActive()), false);
        return 1;
    }
    // Статический метод для вызова из TraitorAttackPacket
    public static int attackPlayerStatic(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            ServerLevel level = ctx.getSource().getLevel();

            int duration = 600; // 30 секунд

            // Запоминаем игрока в карте атакуемых
            attackedPlayers.put(target.getUUID(), duration);

            // Добавляем эффект GLOWING
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, false, false, false));

            // Оповещаем всех гриверов о новой цели
            AABB area = new AABB(-30000000, -1000, -30000000, 30000000, 1000, 30000000);
            int griverCount = 0;

            for (var e : level.getEntities(null, area)) {
                if (e instanceof GriverEntity griver) {
                    griver.setForcedAttackTarget(target, duration);
                    griverCount++;
                }
            }

            final int finalGriverCount = griverCount;

            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§aИгрок " + target.getName().getString() + " помечен для атаки гриверами на 30 секунд! §7(" + finalGriverCount + " гриверов)"), true);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cОшибка: " + e.getMessage()));
            return 0;
        }
    }
}