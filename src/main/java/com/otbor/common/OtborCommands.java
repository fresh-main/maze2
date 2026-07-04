package com.otbor.common;

import com.mojang.brigadier.context.CommandContext;
import com.labyrinthmod.LabyrinthMod;
import com.otbor.network.OtborNetwork;
import com.otbor.network.S2COpenCreditsPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Серверные команды OTBOR.
 *
 * /otbor_credits — запускает финальные титры у ВСЕХ игроков на сервере.
 * Требуется permission level 2 (op). Реализация:
 *   1. Сервер делает broadcast {@link S2COpenCreditsPacket} всем подключённым.
 *   2. Каждый клиент в обработчике пакета вызывает {@code OtborCreditsScreen.open()}.
 */
@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID)
public final class OtborCommands {

    private OtborCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("otbor_credits")
                        .requires(src -> src.hasPermission(2))  // op-level 2+ (default ops)
                        .executes(OtborCommands::startCreditsForAll)
        );
    }

    private static int startCreditsForAll(CommandContext<CommandSourceStack> ctx) {
        OtborNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new S2COpenCreditsPacket());
        ctx.getSource().sendSuccess(
                () -> Component.literal("§eТитры запущены для всех игроков"), true);
        return 1;
    }
}
