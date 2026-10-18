package com.labyrinthmod.common.command;

import com.labyrinthmod.common.data.CraftRestrictionManager;
import com.labyrinthmod.gui.CraftRestrictionMenu;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

public class FractionCraftCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fraction")
                .then(Commands.literal("craft")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("forbid")
                                .then(Commands.argument("fraction", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("FARMER"); builder.suggest("BUTCHER");
                                            builder.suggest("RUNNER"); builder.suggest("COOK");
                                            builder.suggest("MEDIC"); builder.suggest("OPERATOR");
                                            builder.suggest("NONE");
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                                .executes(context -> forbidCrafting(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "fraction"),
                                                        ResourceLocationArgument.getId(context, "item")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("allow")
                                .then(Commands.argument("fraction", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            builder.suggest("FARMER"); builder.suggest("BUTCHER");
                                            builder.suggest("RUNNER"); builder.suggest("COOK");
                                            builder.suggest("MEDIC"); builder.suggest("OPERATOR");
                                            builder.suggest("NONE");
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                                .executes(context -> allowCrafting(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "fraction"),
                                                        ResourceLocationArgument.getId(context, "item")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("list")
                                .executes(context -> listRestrictions(context.getSource()))
                        )
                        .then(Commands.literal("clear")
                                .executes(context -> clearAllRestrictions(context.getSource()))
                        )
                        .then(Commands.literal("gui")
                                .executes(context -> openGui(context.getSource()))
                        )
                )
        );
    }
    private static int openGui(CommandSourceStack source) {
        if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) ->
                    new com.labyrinthmod.gui.CraftRestrictionMenu(id, inv, net.minecraft.world.item.ItemStack.EMPTY),
                    Component.translatable("labyrinthmod.fraction_craft.title")
            ));
        }
        return 1;
    }

    private static int forbidCrafting(CommandSourceStack source, String fraction, ResourceLocation itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) {
            source.sendFailure(Component.translatable("labyrinthmod.fraction_craft.item_not_found", itemId));
            return 0;
        }

        String fractionUpper = fraction.toUpperCase();
        Set<String> currentForbidden = new HashSet<>(CraftRestrictionManager.getForbiddenFactions(item));
        currentForbidden.add(fractionUpper);
        CraftRestrictionManager.setRestrictions(item, currentForbidden);

        source.sendSuccess(() -> Component.translatable("labyrinthmod.fraction_craft.forbidden", fractionUpper, item.getDescription()), true);
        return 1;
    }

    private static int allowCrafting(CommandSourceStack source, String fraction, ResourceLocation itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) {
            source.sendFailure(Component.translatable("labyrinthmod.fraction_craft.item_not_found", itemId));
            return 0;
        }

        String fractionUpper = fraction.toUpperCase();
        Set<String> currentForbidden = new HashSet<>(CraftRestrictionManager.getForbiddenFactions(item));
        currentForbidden.remove(fractionUpper);
        CraftRestrictionManager.setRestrictions(item, currentForbidden);

        source.sendSuccess(() -> Component.translatable("labyrinthmod.fraction_craft.allowed", fractionUpper, item.getDescription()), true);
        return 1;
    }

    private static int listRestrictions(CommandSourceStack source) {
        var restrictions = CraftRestrictionManager.getAllRestrictions();

        if (restrictions.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("labyrinthmod.fraction_craft.no_restrictions"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable("labyrinthmod.fraction_craft.header"), false);

        for (var entry : restrictions.entrySet()) {
            String itemName = entry.getKey().getDescription().getString();
            Set<String> forbidden = entry.getValue();
            source.sendSuccess(() -> Component.translatable("labyrinthmod.fraction_craft.entry", itemName, String.join(", ", forbidden)), false);
        }

        return 1;
    }

    private static int clearAllRestrictions(CommandSourceStack source) {
        CraftRestrictionManager.clearAll();
        source.sendSuccess(() -> Component.translatable("labyrinthmod.fraction_craft.cleared"), true);
        return 1;
    }
}
