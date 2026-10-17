package com.labyrinthmod.common.command;

import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.item.ImposterTabletItem;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.OpenImposterScreenPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

public class ImposterCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("imposter")
                .requires(source -> source.hasPermission(0)) // Любой игрок может использовать
                .then(Commands.literal("tablet")
                        .executes(ImposterCommand::openTablet)
                )
                .then(Commands.literal("cooldown")
                        .executes(ImposterCommand::showCooldown)
                )
        );
    }

    private static int openTablet(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.translatable("labyrinthmod.imposter_command.players_only"));
            return 0;
        }

        // Проверяем, что игрок - предатель
        boolean isImposter = player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction() == FractionType.IMPOSTER)
                .orElse(false);

        if (!isImposter) {
            player.sendSystemMessage(Component.translatable("labyrinthmod.imposter_command.imposters_only"));
            return 0;
        }

        // Проверяем, есть ли уже активная атака
        if (ImposterTabletItem.isAttackActive()) {
            long remaining = (ImposterTabletItem.getAttackEndTime() - System.currentTimeMillis()) / 1000;
            player.sendSystemMessage(Component.translatable("labyrinthmod.imposter_command.attack_active", ImposterTabletItem.getCurrentTargetName(), remaining));
            return 0;
        }

        // Проверяем кулдаун
        long remainingCooldown = ImposterTabletItem.getRemainingCooldown(player);
        if (remainingCooldown > 0) {
            long minutes = remainingCooldown / 60000;
            long seconds = (remainingCooldown % 60000) / 1000;
            player.sendSystemMessage(Component.translatable("labyrinthmod.imposter_command.cooldown", minutes, seconds));
            return 0;
        }

        // Получаем список игроков
        List<OpenImposterScreenPacket.PlayerInfo> onlinePlayers = OpenImposterScreenPacket.getOnlinePlayers(player);
        if (onlinePlayers.isEmpty()) {
            player.sendSystemMessage(Component.translatable("labyrinthmod.imposter_command.no_targets"));
            return 0;
        }

        // Открываем GUI
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenImposterScreenPacket(onlinePlayers));

        player.sendSystemMessage(Component.translatable("labyrinthmod.imposter_command.tablet_opened"));
        return 1;
    }

    private static int showCooldown(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.translatable("labyrinthmod.imposter_command.players_only"));
            return 0;
        }

        boolean isImposter = player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction() == FractionType.IMPOSTER)
                .orElse(false);

        if (!isImposter) {
            player.sendSystemMessage(Component.translatable("labyrinthmod.imposter_command.imposters_only"));
            return 0;
        }

        long remainingCooldown = ImposterTabletItem.getRemainingCooldown(player);
        if (remainingCooldown > 0) {
            long minutes = remainingCooldown / 60000;
            long seconds = (remainingCooldown % 60000) / 1000;
            player.sendSystemMessage(Component.translatable("labyrinthmod.imposter_command.cooldown", minutes, seconds));
        } else {
            player.sendSystemMessage(Component.translatable("labyrinthmod.imposter_command.ready"));
        }

        return 1;
    }
}
