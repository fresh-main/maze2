package com.mazemap.event;

import com.labyrinthmod.LabyrinthMod;
import com.mazemap.item.PersonalMapItem;
import com.mazemap.network.MazeMapNetwork;
import com.mazemap.network.packet.S2CClearMapPacket;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID)
public final class MazeMapCommands {
    private MazeMapCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("mazemap")
                        .then(Commands.literal("reset")
                                .executes(MazeMapCommands::resetMap))
        );
    }

    private static int resetMap(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        ItemStack mapStack = PersonalMapItem.findInInventory(player);
        if (!mapStack.isEmpty()) {
            PersonalMapItem.clearData(mapStack);

            MazeMapNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2CClearMapPacket());

            player.displayClientMessage(Component.literal("§aКарта в предмете сброшена."), true);
        } else {
            player.displayClientMessage(Component.literal("§cУ вас нет карты в инвентаре."), true);
        }
        return 1;
    }
}