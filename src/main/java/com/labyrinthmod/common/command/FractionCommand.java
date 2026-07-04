package com.labyrinthmod.common.command;

import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.event.FractionEvents;
import com.labyrinthmod.common.init.ModBlocks;
import com.labyrinthmod.common.item.ImposterTabletItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class FractionCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fraction")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("fraction", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (FractionType type : FractionType.values()) {
                                                if (type != FractionType.NONE && type != FractionType.IMPOSTER) {
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

            if (fraction == null || fraction == FractionType.NONE || fraction == FractionType.IMPOSTER) {
                ctx.getSource().sendFailure(Component.literal("§cНеверный тип фракции. Используйте /fraction traitor для выдачи предателя"));
                return 0;
            }

            target.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
                FractionType oldFraction = data.getFraction();
                data.setFraction(fraction);
                FractionEvents.updatePlayerDisplayName(target);
                FractionEvents.onFractionChanged(target, oldFraction, fraction);
            });

            ctx.getSource().sendSuccess(() -> Component.literal("§aИгроку " + target.getName().getString() + " выдана фракция: " + fraction.displayName), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cОшибка: " + e.getMessage()));
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
                    ctx.getSource().sendFailure(Component.literal("§cНеверная маскировка! Доступны: FARMER, BUTCHER, RUNNER, COOK, MEDIC"));
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

            ctx.getSource().sendSuccess(() -> Component.literal("§aИгроку " + target.getName().getString() + " выдана фракция ПРЕДАТЕЛЬ с маскировкой " + finalMask), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cОшибка: " + e.getMessage()));
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

            ctx.getSource().sendSuccess(() -> Component.literal("§aУ игрока " + target.getName().getString() + " удалена фракция"), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cОшибка: " + e.getMessage()));
            return 0;
        }
    }

    private static int listFractions(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§6=== Доступные фракции ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e- FARMER §7(Фермер) - работа с грядками"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§6- BUTCHER §7(Мясник) - кормление животных, атака монстров, сила I"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§b- RUNNER §7(Бегун) - атака монстров, покидание зон, скорость I"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§f- COOK §7(Повар) - готовка еды"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§d- MEDIC §7(Медик) - регенерация I"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7- OPERATOR §7(Оператор) - нет ограничений"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§c- IMPOSTER §7(Предатель) - скрытая фракция"), false);
        return 1;
    }

    private static int getFraction(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            target.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
                if (data.hasFraction()) {
                    FractionType fraction = data.getFraction();
                    if (fraction == FractionType.IMPOSTER && data.hasImposterMask()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§aУ игрока " + target.getName().getString() + " фракция: ПРЕДАТЕЛЬ (маскировка: " + data.getImposterMask() + ")"), false);
                    } else {
                        ctx.getSource().sendSuccess(() -> Component.literal("§aУ игрока " + target.getName().getString() + " фракция: " + fraction.displayName), false);
                    }
                } else {
                    ctx.getSource().sendSuccess(() -> Component.literal("§eУ игрока " + target.getName().getString() + " нет фракции"), false);
                }
            });
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cОшибка: " + e.getMessage()));
            return 0;
        }
    }
}