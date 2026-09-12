package com.labyrinthmod.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class LootTableCommand {

    private static final int PAGE_SIZE = 50;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("loottables")
                        .requires(source -> source.hasPermission(2))

                        // /loottables
                        .executes(ctx -> sendPage(ctx.getSource(), 1, null))

                        // /loottables <page>
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> sendPage(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "page"),
                                        null
                                ))
                        )

                        // /loottables search <filter>
                        .then(Commands.literal("search")
                                .then(Commands.argument("filter", StringArgumentType.greedyString())
                                        .executes(ctx -> sendPage(
                                                ctx.getSource(),
                                                1,
                                                StringArgumentType.getString(ctx, "filter")
                                        ))

                                        // /loottables search <filter> <page>
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(ctx -> sendPage(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "page"),
                                                        StringArgumentType.getString(ctx, "filter")
                                                ))
                                        )
                                )
                        )

                        // /loottables all
                        .then(Commands.literal("all")
                                .executes(ctx -> sendAll(ctx.getSource(), null))
                        )

                        // /loottables searchall <filter>
                        .then(Commands.literal("searchall")
                                .then(Commands.argument("filter", StringArgumentType.greedyString())
                                        .executes(ctx -> sendAll(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "filter")
                                        ))
                                )
                        )
        );
    }

    private static int sendPage(CommandSourceStack source, int page, String filter) {
        List<ResourceLocation> tables = getFilteredLootTables(source, filter);

        if (tables.isEmpty()) {
            source.sendFailure(Component.literal("Таблицы лута не найдены."));
            return 0;
        }

        int totalPages = Math.max(1, (tables.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.max(1, Math.min(page, totalPages));

        String filterInfo = filter == null || filter.isBlank()
                ? ""
                : " | Фильтр: " + filter;

        source.sendSuccess(
                () -> Component.literal(
                        "Таблиц лута: " + tables.size()
                                + " | Страница: " + currentPage + "/" + totalPages
                                + filterInfo
                ).withStyle(ChatFormatting.GOLD),
                false
        );

        int startIndex = (currentPage - 1) * PAGE_SIZE;
        int endIndex = Math.min(tables.size(), startIndex + PAGE_SIZE);

        for (int i = startIndex; i < endIndex; i++) {
            final int number = i + 1;
            final String id = tables.get(i).toString();

            source.sendSuccess(
                    () -> Component.literal(number + ". ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(id).withStyle(ChatFormatting.WHITE)),
                    false
            );
        }

        return tables.size();
    }

    private static int sendAll(CommandSourceStack source, String filter) {
        List<ResourceLocation> tables = getFilteredLootTables(source, filter);

        if (tables.isEmpty()) {
            source.sendFailure(Component.literal("Таблицы лута не найдены."));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Всего таблиц лута: " + tables.size()
                ).withStyle(ChatFormatting.GOLD),
                false
        );

        for (int i = 0; i < tables.size(); i++) {
            final int number = i + 1;
            final String id = tables.get(i).toString();

            source.sendSuccess(
                    () -> Component.literal(number + ". ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(id).withStyle(ChatFormatting.WHITE)),
                    false
            );
        }

        return tables.size();
    }

    private static List<ResourceLocation> getFilteredLootTables(CommandSourceStack source, String filter) {
        MinecraftServer server = source.getServer();

        if (server == null) {
            return List.of();
        }

        Collection<ResourceLocation> ids = server.getLootData().getKeys(LootDataType.TABLE);

        Stream<ResourceLocation> stream = ids.stream()
                .sorted(Comparator.comparing(ResourceLocation::toString));

        if (filter != null && !filter.isBlank()) {
            String lowerFilter = filter.toLowerCase(Locale.ROOT);

            stream = stream.filter(id ->
                    id.toString().toLowerCase(Locale.ROOT).contains(lowerFilter)
            );
        }

        return stream.toList();
    }
}