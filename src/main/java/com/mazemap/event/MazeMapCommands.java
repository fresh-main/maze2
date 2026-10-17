package com.mazemap.event;

import com.labyrinthmod.LabyrinthMod;
import com.mazemap.item.PersonalMapItem;
import com.mazemap.registry.ModItems;
import com.mazemap.util.MapDurabilityHandler;
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
                        .then(Commands.literal("infinite")
                                .requires(source -> source.hasPermission(2))
                                .executes(MazeMapCommands::giveInfiniteMap))
                        .then(Commands.literal("reset")
                                .executes(MazeMapCommands::resetMap))
        );
    }

    private static int giveInfiniteMap(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack mapStack = new ItemStack(ModItems.PERSONAL_MAP.get());
        MapDurabilityHandler.setInfinite(mapStack, true);
        mapStack.setHoverName(Component.translatable("mazemap.item.infinite_map"));

        if (!player.getInventory().add(mapStack)) {
            player.drop(mapStack, false);
        }
        player.sendSystemMessage(Component.translatable("mazemap.command.infinite_map_received"));
        return 1;
    }

    private static int resetMap(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        ItemStack mapStack = PersonalMapItem.findInInventory(player);
        if (!mapStack.isEmpty()) {
            PersonalMapItem.clearData(mapStack);

            MazeMapNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2CClearMapPacket());

            player.displayClientMessage(Component.translatable("mazemap.command.map_reset"), true);
        } else {
            player.displayClientMessage(Component.translatable("mazemap.command.no_map"), true);
        }
        return 1;
    }
}
