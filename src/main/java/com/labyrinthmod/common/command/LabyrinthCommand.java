package com.labyrinthmod.common.command;

import com.labyrinthmod.common.config.ModConfig;
import com.labyrinthmod.common.event.ConfigSyncHandler;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

public class LabyrinthCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("labyrinth")
                .then(Commands.literal("help")
                        .executes(context -> showHelp(context.getSource()))
                )
                .then(Commands.literal("reloadconfig")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> reloadConfig(context.getSource()))
                )
        );
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§6§l=== LABYRINTH MOD COMMANDS ==="), false);
        source.sendSuccess(() -> Component.literal(""), false);

        // Команды фракций
        source.sendSuccess(() -> Component.literal("§e§l【Фракции】"), false);
        source.sendSuccess(() -> Component.literal("§7/fraction set <игрок> <фракция> §8- §fВыдать фракцию игроку"), false);
        source.sendSuccess(() -> Component.literal("§7/fraction get <игрок> §8- §fПоказать фракцию игрока"), false);
        source.sendSuccess(() -> Component.literal("§7/fraction list §8- §fСписок всех фракций"), false);
        source.sendSuccess(() -> Component.literal("§7/fraction reload §8- §fПерезагрузить конфиг"), false);
        source.sendSuccess(() -> Component.literal(""), false);

        // Команды зон
        source.sendSuccess(() -> Component.literal("§e§l【Зоны】"), false);
        source.sendSuccess(() -> Component.literal("§7/zone create <название> §8- §fСоздать зону на текущей позиции"), false);
        source.sendSuccess(() -> Component.literal("§7/zone delete <название> §8- §fУдалить зону"), false);
        source.sendSuccess(() -> Component.literal("§7/zone list §8- §fСписок всех зон"), false);
        source.sendSuccess(() -> Component.literal("§7/zone info §8- §fИнформация о текущей зоне"), false);
        source.sendSuccess(() -> Component.literal("§7/zone radius <размер> §8- §fУстановить радиус зоны"), false);
        source.sendSuccess(() -> Component.literal("§7/zone tp <название> §8- §fТелепорт в зону"), false);
        source.sendSuccess(() -> Component.literal("§7/zone enable §8- §fВключить систему зон"), false);
        source.sendSuccess(() -> Component.literal("§7/zone disable §8- §fВыключить систему зон"), false);
        source.sendSuccess(() -> Component.literal(""), false);

        // Команды крафта
        source.sendSuccess(() -> Component.literal("§e§l【Крафт】"), false);
        source.sendSuccess(() -> Component.literal("§7/fraction craft <фракция> <предмет> §8- §fЗапретить крафт для всех, кроме указанной фракции"), false);
        source.sendSuccess(() -> Component.literal("§7/fraction craft remove <предмет> §8- §fУдалить запрет на крафт"), false);
        source.sendSuccess(() -> Component.literal("§7/fraction craft list §8- §fСписок запрещённых крафтов"), false);
        source.sendSuccess(() -> Component.literal("§7/fraction craft clear §8- §fОчистить все запреты"), false);
        source.sendSuccess(() -> Component.literal(""), false);

        // Основные команды
        source.sendSuccess(() -> Component.literal("§e§l【Основные】"), false);
        source.sendSuccess(() -> Component.literal("§7/labyrinth help §8- §fПоказать эту справку"), false);
        source.sendSuccess(() -> Component.literal(""), false);

        // Информация о фракциях
        source.sendSuccess(() -> Component.literal("§6§l=== ДОСТУПНЫЕ ФРАКЦИИ ==="), false);
        source.sendSuccess(() -> Component.literal("§7- §aFARMER §8(Фермер) §7- Работа с грядками, посадка и сбор урожая"), false);
        source.sendSuccess(() -> Component.literal("§7- §cBUTCHER §8(Мясник) §7- Кормление животных, атака монстров, эффект Силы I"), false);
        source.sendSuccess(() -> Component.literal("§7- §bRUNNER §8(Бегун) §7- Атака монстров, покидание зон, эффект Скорости I"), false);
        source.sendSuccess(() -> Component.literal("§7- §6COOK §8(Повар) §7- Готовка еды (верстак, коптильня, костёр)"), false);
        source.sendSuccess(() -> Component.literal("§7- §dMEDIC §8(Медик) §7- Эффект Регенерации I"), false);
        source.sendSuccess(() -> Component.literal(""), false);

        // Информация об ограничениях
        source.sendSuccess(() -> Component.literal("§6§l=== ОГРАНИЧЕНИЯ ==="), false);
        source.sendSuccess(() -> Component.literal("§7• §fОбычная печь §cНЕ готовит еду §fникому"), false);
        source.sendSuccess(() -> Component.literal("§7• §fКоптильня и костёр §aтолько для поваров"), false);
        source.sendSuccess(() -> Component.literal("§7• §fКрафт еды §aтолько для поваров"), false);
        source.sendSuccess(() -> Component.literal("§7• §fАтака монстров/животных §aтолько для бегунов и мясников"), false);
        source.sendSuccess(() -> Component.literal("§7• §fПокидание зон §aтолько для бегунов"), false);
        source.sendSuccess(() -> Component.literal("§7• §fРабота с грядками §aтолько для фермеров"), false);
        source.sendSuccess(() -> Component.literal("§7• §fКормление животных §aтолько для мясников"), false);

        return 1;
    }
    private static int reloadConfig(CommandSourceStack source) {
        ModConfig.reload();

        if (source.getEntity() instanceof ServerPlayer player) {
            // Синхронизируем со всеми игроками
            ConfigSyncHandler.syncToAllPlayers(player);
        }

        source.sendSuccess(() -> Component.literal("§aКонфиг перезагружен и синхронизирован с клиентами!"), true);
        return 1;
    }
}