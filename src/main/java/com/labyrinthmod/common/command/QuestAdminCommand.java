package com.labyrinthmod.common.command;

import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.OpenQuestAdminPacket;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class QuestAdminCommand {
    private QuestAdminCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("questadmin")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenQuestAdminPacket());
                    return 1;
                }));
    }
}
