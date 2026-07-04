package com.labyrinthmod.common.command;

import com.labyrinthmod.common.zone.WindZone;
import com.labyrinthmod.common.zone.WindZoneManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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

public class WindZoneCommand {

    private static final Map<UUID, BlockPos> pos1Selections = new HashMap<>();
    private static final Map<UUID, BlockPos> pos2Selections = new HashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("windzone")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("pos1")
                        .executes(WindZoneCommand::setPos1))
                .then(Commands.literal("pos2")
                        .executes(WindZoneCommand::setPos2))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("strength", IntegerArgumentType.integer(1, 10))
                                        .then(Commands.argument("force", IntegerArgumentType.integer(1, 5))
                                                .executes(WindZoneCommand::createWindZone)))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(WindZoneCommand::removeWindZone)))
                .then(Commands.literal("list")
                        .executes(WindZoneCommand::listWindZones))
                .then(Commands.literal("clear")
                        .executes(WindZoneCommand::clearWindZones))
                .then(Commands.literal("enable")
                        .executes(ctx -> setEnabled(ctx, true)))
                .then(Commands.literal("disable")
                        .executes(ctx -> setEnabled(ctx, false)))
                .then(Commands.literal("toggle")
                        .executes(WindZoneCommand::toggleEnabled))
                .then(Commands.literal("status")
                        .executes(WindZoneCommand::status))
                .then(Commands.literal("test")
                        .executes(WindZoneCommand::testWindZone))
        );
    }

    private static int testWindZone(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        WindZoneManager manager = WindZoneManager.get(player.level());
        if (manager == null) return 0;

        BlockPos pos = player.blockPosition();

        // Создаём временную зону для теста
        WindZone testZone = new WindZone("test_" + player.getUUID().toString().substring(0, 8),
                pos, pos.offset(3, 3, 3), 5, 3);
        manager.addWindZone(testZone);

        player.sendSystemMessage(Component.literal("§aТестовая зона создана вокруг вас! Поставьте блок и сломайте его."));
        player.sendSystemMessage(Component.literal("§7Для удаления используйте §e/windzone remove test_" + player.getUUID().toString().substring(0, 8)));

        return 1;
    }

    private static int setPos1(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        BlockPos pos = player.blockPosition();
        pos1Selections.put(player.getUUID(), pos);
        player.sendSystemMessage(Component.literal("§aПозиция 1 установлена: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
        return 1;
    }

    private static int setPos2(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        BlockPos pos = player.blockPosition();
        pos2Selections.put(player.getUUID(), pos);
        player.sendSystemMessage(Component.literal("§aПозиция 2 установлена: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
        return 1;
    }

    private static int createWindZone(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        String name = StringArgumentType.getString(ctx, "name");
        int strength = IntegerArgumentType.getInteger(ctx, "strength");
        int force = IntegerArgumentType.getInteger(ctx, "force");

        BlockPos pos1 = pos1Selections.get(player.getUUID());
        BlockPos pos2 = pos2Selections.get(player.getUUID());

        if (pos1 == null || pos2 == null) {
            player.sendSystemMessage(Component.literal("§cСначала установите позиции 1 и 2 с помощью /windzone pos1 и /windzone pos2"));
            return 0;
        }

        WindZoneManager manager = WindZoneManager.get(player.level());
        if (manager == null) return 0;

        if (manager.getWindZone(name) != null) {
            player.sendSystemMessage(Component.literal("§cЗона ветра с именем " + name + " уже существует!"));
            return 0;
        }

        WindZone zone = new WindZone(name, pos1, pos2, strength, force);
        manager.addWindZone(zone);

        player.sendSystemMessage(Component.literal("§aЗона ветра §e" + name + " §aсоздана!"));
        player.sendSystemMessage(Component.literal("§7Сила ветра: " + strength + ", Сила отбрасывания: " + force));
        player.sendSystemMessage(Component.literal("§7Размер: от " + zone.minX + "," + zone.minY + "," + zone.minZ +
                " до " + zone.maxX + "," + zone.maxY + "," + zone.maxZ));
        player.sendSystemMessage(Component.literal("§7Теперь при размещении/удалении блоков в этой зоне будет вырываться пыль!"));

        return 1;
    }

    private static int removeWindZone(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        WindZoneManager manager = WindZoneManager.get(ctx.getSource().getLevel());
        if (manager == null) return 0;

        if (manager.getWindZone(name) == null) {
            ctx.getSource().sendFailure(Component.literal("§cЗона ветра " + name + " не найдена!"));
            return 0;
        }

        manager.removeWindZone(name);
        ctx.getSource().sendSuccess(() -> Component.literal("§aЗона ветра §e" + name + " §aудалена!"), true);
        return 1;
    }

    private static int listWindZones(CommandContext<CommandSourceStack> ctx) {
        WindZoneManager manager = WindZoneManager.get(ctx.getSource().getLevel());
        if (manager == null) return 0;

        var zones = manager.getAllWindZones();

        if (zones.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§eНет созданных зон ветра"), false);
            return 1;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§6=== Список зон ветра ==="), false);
        for (WindZone zone : zones) {
            String status = zone.isActive() ? "§aВкл" : "§cВыкл";
            ctx.getSource().sendSuccess(() -> Component.literal("§e- " + zone.name +
                    " §7(Сила: " + zone.windStrength + ", Отбрасывание: " + zone.pushForce + ") " + status), false);
        }
        return 1;
    }

    private static int clearWindZones(CommandContext<CommandSourceStack> ctx) {
        WindZoneManager manager = WindZoneManager.get(ctx.getSource().getLevel());
        if (manager == null) return 0;

        manager.clearWindZones();
        ctx.getSource().sendSuccess(() -> Component.literal("§aВсе зоны ветра удалены!"), true);
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        WindZoneManager manager = WindZoneManager.get(ctx.getSource().getLevel());
        if (manager == null) return 0;

        manager.setEnabled(enabled);
        ctx.getSource().sendSuccess(() -> Component.literal("§aЗоны ветра " + (enabled ? "включены" : "выключены")), true);
        return 1;
    }

    private static int toggleEnabled(CommandContext<CommandSourceStack> ctx) {
        WindZoneManager manager = WindZoneManager.get(ctx.getSource().getLevel());
        if (manager == null) return 0;

        manager.setEnabled(!manager.isEnabled());
        ctx.getSource().sendSuccess(() -> Component.literal("§aЗоны ветра " + (manager.isEnabled() ? "включены" : "выключены")), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        WindZoneManager manager = WindZoneManager.get(ctx.getSource().getLevel());
        if (manager == null) return 0;

        ctx.getSource().sendSuccess(() -> Component.literal("§6=== Статус зон ветра ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eСостояние: " + (manager.isEnabled() ? "§aВключены" : "§cВыключены")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eКоличество зон: §f" + manager.getAllWindZones().size()), false);
        return 1;
    }
}