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
            ctx.getSource().sendFailure(Component.literal("§cЭта команда только для игроков!"));
            return 0;
        }

        // Проверяем, что игрок - предатель
        boolean isImposter = player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction() == FractionType.IMPOSTER)
                .orElse(false);

        if (!isImposter) {
            player.sendSystemMessage(Component.literal("§cТолько предатели могут использовать эту команду!"));
            return 0;
        }

        // Проверяем, есть ли уже активная атака
        if (ImposterTabletItem.isAttackActive()) {
            long remaining = (ImposterTabletItem.getAttackEndTime() - System.currentTimeMillis()) / 1000;
            player.sendSystemMessage(Component.literal("§cГриверы уже атакуют " + ImposterTabletItem.getCurrentTargetName() + "! Осталось " + remaining + " секунд."));
            return 0;
        }

        // Проверяем кулдаун
        long remainingCooldown = ImposterTabletItem.getRemainingCooldown(player);
        if (remainingCooldown > 0) {
            long minutes = remainingCooldown / 60000;
            long seconds = (remainingCooldown % 60000) / 1000;
            player.sendSystemMessage(Component.literal("§cДо следующей атаки: " + minutes + "м " + seconds + "с"));
            return 0;
        }

        // Получаем список игроков
        List<OpenImposterScreenPacket.PlayerInfo> onlinePlayers = OpenImposterScreenPacket.getOnlinePlayers(player);
        if (onlinePlayers.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cНет доступных целей!"));
            return 0;
        }

        // Открываем GUI
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenImposterScreenPacket(onlinePlayers));

        player.sendSystemMessage(Component.literal("§aПланшет открыт!"));
        return 1;
    }

    private static int showCooldown(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("§cЭта команда только для игроков!"));
            return 0;
        }

        boolean isImposter = player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction() == FractionType.IMPOSTER)
                .orElse(false);

        if (!isImposter) {
            player.sendSystemMessage(Component.literal("§cТолько предатели могут использовать эту команду!"));
            return 0;
        }

        long remainingCooldown = ImposterTabletItem.getRemainingCooldown(player);
        if (remainingCooldown > 0) {
            long minutes = remainingCooldown / 60000;
            long seconds = (remainingCooldown % 60000) / 1000;
            player.sendSystemMessage(Component.literal("§cДо следующей атаки: " + minutes + "м " + seconds + "с"));
        } else {
            player.sendSystemMessage(Component.literal("§aВы можете использовать планшет!"));
        }

        return 1;
    }
}