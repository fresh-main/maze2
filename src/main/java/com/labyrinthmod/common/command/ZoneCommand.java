package com.labyrinthmod.common.command;

import com.labyrinthmod.common.zone.ZoneManager;
import com.labyrinthmod.common.zone.ZoneManager.Zone;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ZoneCommand {
    private static final Map<UUID, BlockPos> pos1Selections = new HashMap<>();
    private static final Map<UUID, BlockPos> pos2Selections = new HashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("zone")
                .requires(source -> source.hasPermission(2)
                        || (source.getPlayer() != null && source.getPlayer().isCreative()))
                .then(Commands.literal("pos1")
                        .executes(ZoneCommand::setPos1))
                .then(Commands.literal("pos2")
                        .executes(ZoneCommand::setPos2))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ZoneCommand::createZone)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ZoneCommand::removeZone)))
                .then(Commands.literal("list")
                        .executes(ZoneCommand::listZones))
                .then(Commands.literal("clear")
                        .executes(ZoneCommand::clearZones))
                .then(Commands.literal("info")
                        .executes(ZoneCommand::zoneInfo))
                .then(Commands.literal("enable")
                        .executes(ZoneCommand::enableZones))
                .then(Commands.literal("disable")
                        .executes(ZoneCommand::disableZones))
                .then(Commands.literal("toggle")
                        .executes(ZoneCommand::toggleZones))
                .then(Commands.literal("status")
                        .executes(ZoneCommand::zoneStatus))
                .then(Commands.literal("access")
                        .executes(ZoneCommand::openAccessScreen))
        );
    }

    /** Открывает FractionAccessScreen у админа: шлёт серверный sync-запрос,
     *  по ответу клиент откроет экран. Команда — единственный способ доступа,
     *  отдельно от админ-меню гриверов. */
    private static int openAccessScreen(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Команда доступна только игрокам"));
            return 0;
        }
        com.labyrinthmod.common.zone.ZoneManager manager =
                com.labyrinthmod.common.zone.ZoneManager.get(player.serverLevel());
        if (manager == null) return 0;
        com.labyrinthmod.common.network.NetworkHandler.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new com.labyrinthmod.common.network.packet.S2CFractionAccessSyncPacket(
                        manager.getFractionAccessSnapshot(), true /* openScreen — игрок САМ запросил */));
        return 1;
    }

    private static int setPos1(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        BlockPos pos = player.blockPosition();
        pos1Selections.put(player.getUUID(), pos);
        player.sendSystemMessage(Component.literal("§aПозиция 1 установлена: " +
                pos.getX() + " " + pos.getY() + " " + pos.getZ()));
        return 1;
    }

    private static int setPos2(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        BlockPos pos = player.blockPosition();
        pos2Selections.put(player.getUUID(), pos);
        player.sendSystemMessage(Component.literal("§aПозиция 2 установлена: " +
                pos.getX() + " " + pos.getY() + " " + pos.getZ()));
        return 1;
    }

    private static int createZone(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        String name = StringArgumentType.getString(ctx, "name");

        BlockPos pos1 = pos1Selections.get(player.getUUID());
        BlockPos pos2 = pos2Selections.get(player.getUUID());

        if (pos1 == null || pos2 == null) {
            player.sendSystemMessage(Component.literal("§cСначала установите позиции 1 и 2 с помощью /zone pos1 и /zone pos2"));
            return 0;
        }

        ZoneManager manager = ZoneManager.get(player.level());
        if (manager == null) return 0;

        if (manager.getZone(name) != null) {
            player.sendSystemMessage(Component.literal("§cЗона с именем " + name + " уже существует!"));
            return 0;
        }

        Zone zone = new Zone(name, pos1, pos2);
        manager.addZone(zone);

        player.sendSystemMessage(Component.literal("§aЗона §e" + name + " §aсоздана!"));
        player.sendSystemMessage(Component.literal("§7Размер: от " + zone.minX + "," + zone.minY + "," + zone.minZ +
                " до " + zone.maxX + "," + zone.maxY + "," + zone.maxZ));

        return 1;
    }

    private static int removeZone(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ZoneManager manager = ZoneManager.get(ctx.getSource().getLevel());
        if (manager == null) return 0;

        if (manager.getZone(name) == null) {
            ctx.getSource().sendFailure(Component.literal("§cЗона " + name + " не найдена!"));
            return 0;
        }

        manager.removeZone(name);
        ctx.getSource().sendSuccess(() -> Component.literal("§aЗона §e" + name + " §aудалена!"), true);
        return 1;
    }

    private static int listZones(CommandContext<CommandSourceStack> ctx) {
        ZoneManager manager = ZoneManager.get(ctx.getSource().getLevel());
        if (manager == null) return 0;

        var zones = manager.getAllZones();

        if (zones.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§eНет созданных зон"), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§6=== Список зон ==="), false);
        for (Zone zone : zones) {
            ctx.getSource().sendSuccess(() -> Component.literal("§e- " + zone.name +
                    " §7(" + zone.minX + ".." + zone.maxX + ", " + zone.minY + ".." + zone.maxY + ", " + zone.minZ + ".." + zone.maxZ + ")"), false);
        }
        return 1;
    }

    private static int clearZones(CommandContext<CommandSourceStack> ctx) {
        ZoneManager manager = ZoneManager.get(ctx.getSource().getLevel());
        if (manager == null) return 0;

        manager.clearZones();
        ctx.getSource().sendSuccess(() -> Component.literal("§aВсе зоны удалены!"), true);
        return 1;
    }

    private static int zoneInfo(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        ZoneManager manager = ZoneManager.get(player.level());
        if (manager == null) return 0;

        Zone currentZone = manager.getZoneAt(player.blockPosition());

        if (currentZone == null) {
            player.sendSystemMessage(Component.literal("§eВы не находитесь ни в одной зоне"));
        } else {
            player.sendSystemMessage(Component.literal("§aВы находитесь в зоне: §e" + currentZone.name));
        }
        return 1;
    }

    private static int enableZones(CommandContext<CommandSourceStack> ctx) {
        ZoneManager manager = ZoneManager.get(ctx.getSource().getLevel());
        if (manager == null) return 0;

        manager.setZonesEnabled(true);
        return 1;
    }

    private static int disableZones(CommandContext<CommandSourceStack> ctx) {
        ZoneManager manager = ZoneManager.get(ctx.getSource().getLevel());
        if (manager == null) return 0;

        manager.setZonesEnabled(false);
        return 1;
    }

    private static int toggleZones(CommandContext<CommandSourceStack> ctx) {
        ZoneManager manager = ZoneManager.get(ctx.getSource().getLevel());
        if (manager == null) return 0;

        manager.toggleZones();
        boolean enabled = manager.isZonesEnabled();
        if (enabled) {
            ctx.getSource().sendSuccess(() -> Component.literal("§aЗоны §eВКЛЮЧЕНЫ§a! Теперь не-бегуны не могут их покидать"), true);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§cЗоны §eВЫКЛЮЧЕНЫ§c! Теперь все могут свободно перемещаться"), true);
        }
        return 1;
    }

    private static int zoneStatus(CommandContext<CommandSourceStack> ctx) {
        ZoneManager manager = ZoneManager.get(ctx.getSource().getLevel());
        if (manager == null) return 0;

        boolean enabled = manager.isZonesEnabled();
        int zoneCount = manager.getAllZones().size();

        ctx.getSource().sendSuccess(() -> Component.literal("§6=== Статус зон ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eСостояние: " + (enabled ? "§aВКЛЮЧЕНЫ" : "§cВЫКЛЮЧЕНЫ")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eКоличество зон: §f" + zoneCount), false);
        return 1;
    }
}