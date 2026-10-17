package com.labyrinthmod.common.command;

import com.labyrinthmod.common.capability.PossessionProvider;
import com.labyrinthmod.common.entity.GriverEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class PossessCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("possess")
                .requires(source -> source.hasPermission(2)) // Только операторы (уровень 2)
                .then(Commands.argument("entityId", IntegerArgumentType.integer(0))
                        .executes(context -> {
                            int entityId = IntegerArgumentType.getInteger(context, "entityId");
                            return possessEntity(context.getSource(), entityId);
                        })
                )
        );
    }

    private static int possessEntity(CommandSourceStack source, int entityId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("labyrinthmod.possess.players_only"));
            return 0;
        }

        // Дополнительная проверка прав
        if (!player.hasPermissions(2) && !player.isCreative()) {
            source.sendFailure(Component.translatable("labyrinthmod.possess.no_permission"));
            return 0;
        }

        Entity target = player.level().getEntity(entityId);

        if (!(target instanceof GriverEntity griver)) {
            source.sendFailure(Component.translatable("labyrinthmod.possess.not_griver", entityId));
            return 0;
        }

        if (griver.isVehicle()) {
            source.sendFailure(Component.translatable("labyrinthmod.possess.already_ridden"));
            return 0;
        }

        // Используем стандартную систему верховой езды
        if (griver.isSaddled()) {
            player.startRiding(griver);

            player.getCapability(PossessionProvider.POSSESSION).ifPresent(data -> {
                data.setPossessing(true);
                data.setPossessedEntityId(entityId);
            });

            source.sendSuccess(() -> Component.translatable("labyrinthmod.possess.mounted"), true);
            return 1;
        }

        source.sendFailure(Component.translatable("labyrinthmod.possess.not_saddled"));
        return 0;
    }
}
