package com.labyrinthmod.common.command;

import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.event.FractionEvents;
import com.labyrinthmod.common.item.ImposterTabletItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class FractionCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fraction")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("fraction", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (FractionType type : FractionType.values()) {
                                                if (type != FractionType.NONE && type != FractionType.IMPOSTER
                                                        && type != FractionType.OPERATOR && type != FractionType.RUNNER
                                                        && type != FractionType.BUTCHER) {
                                                    builder.suggest(type.name());
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(FractionCommand::setFraction)
                                )
                        )
                )
                .then(Commands.literal("traitor")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("mask", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("FARMER");
                                            builder.suggest("BUTCHER");
                                            builder.suggest("RUNNER");
                                            builder.suggest("COOK");
                                            builder.suggest("MEDIC");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> setImposter(ctx, true))
                                )
                                .executes(ctx -> setImposter(ctx, false))
                        )
                )
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(FractionCommand::removeFraction)
                        )
                )
                .then(Commands.literal("list")
                        .executes(FractionCommand::listFractions)
                )
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(FractionCommand::getFraction)
                        )
                )
        );
    }

    private static int setFraction(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String fractionName = StringArgumentType.getString(ctx, "fraction");
            FractionType fraction = FractionType.fromName(fractionName);

            if (fraction == FractionType.NONE || fraction == FractionType.IMPOSTER
                    || fraction == FractionType.OPERATOR || fraction == FractionType.RUNNER
                    || fraction == FractionType.BUTCHER) {
                ctx.getSource().sendFailure(Component.translatable("labyrinthmod.fraction_command.unavailable"));
                return 0;
            }

            target.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
                FractionType oldFraction = data.getFraction();
                data.setFraction(fraction);
                FractionEvents.updatePlayerDisplayName(target);
                FractionEvents.onFractionChanged(target, oldFraction, fraction);
            });

            ctx.getSource().sendSuccess(() -> Component.translatable("labyrinthmod.fraction_command.assigned", target.getDisplayName(), fraction.displayNameComponent()), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("labyrinthmod.command.error", e.getMessage()));
            return 0;
        }
    }

    private static int setImposter(CommandContext<CommandSourceStack> ctx, boolean hasMask) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String maskName = hasMask ? StringArgumentType.getString(ctx, "mask") : null;

            // Определяем маскировку
            String finalMask;
            if (hasMask && maskName != null) {
                finalMask = maskName.toUpperCase();
                // Проверяем, что маскировка валидна
                FractionType maskType = FractionType.fromName(finalMask);
                if (maskType == null || maskType == FractionType.NONE || maskType == FractionType.OPERATOR || maskType == FractionType.IMPOSTER) {
                    ctx.getSource().sendFailure(Component.translatable("labyrinthmod.fraction_command.invalid_mask"));
                    return 0;
                }
            } else {
                finalMask = FractionType.getRandomMask();
            }

            target.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
                FractionType oldFraction = data.getFraction();
                data.setFraction(FractionType.IMPOSTER);
                data.setImposterMask(finalMask);
                FractionType.IMPOSTER.setMaskFraction(finalMask);
                FractionEvents.updatePlayerDisplayName(target);
                FractionEvents.onFractionChanged(target, oldFraction, FractionType.IMPOSTER);

                // Выдаём планшет предателя
                ImposterTabletItem.giveTabletIfNotExists(target);
            });

            ctx.getSource().sendSuccess(() -> Component.translatable("labyrinthmod.fraction_command.imposter_assigned", target.getDisplayName(), finalMask), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("labyrinthmod.command.error", e.getMessage()));
            return 0;
        }
    }

    private static int removeFraction(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            target.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
                FractionType oldFraction = data.getFraction();
                data.setFraction(FractionType.NONE);
                data.setImposterMask(null);
                FractionEvents.updatePlayerDisplayName(target);
                FractionEvents.onFractionChanged(target, oldFraction, FractionType.NONE);

                // Забираем планшет предателя
                target.getInventory().clearOrCountMatchingItems(
                        item -> item.getItem() instanceof ImposterTabletItem,
                        64,
                        target.inventoryMenu.getCraftSlots()
                );
            });

            ctx.getSource().sendSuccess(() -> Component.translatable("labyrinthmod.fraction_command.removed", target.getDisplayName()), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("labyrinthmod.command.error", e.getMessage()));
            return 0;
        }
    }

    private static int listFractions(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatable("labyrinthmod.fraction_command.list"), false);
        return 1;
    }

    private static int getFraction(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            target.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
                if (data.hasFraction()) {
                    FractionType fraction = data.getFraction();
                    if (fraction == FractionType.IMPOSTER && data.hasImposterMask()) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("labyrinthmod.fraction_command.current_imposter", target.getDisplayName(), data.getImposterMask()), false);
                    } else {
                        ctx.getSource().sendSuccess(() -> Component.translatable("labyrinthmod.fraction_command.current", target.getDisplayName(), fraction.displayNameComponent()), false);
                    }
                } else {
                    ctx.getSource().sendSuccess(() -> Component.translatable("labyrinthmod.fraction_command.none", target.getDisplayName()), false);
                }
            });
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("labyrinthmod.command.error", e.getMessage()));
            return 0;
        }
    }
}
