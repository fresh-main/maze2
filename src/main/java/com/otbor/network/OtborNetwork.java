package com.otbor.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Сетевой канал OTBOR. Регистрируется в {@code LabyrinthMod.commonSetup}.
 *
 * Сейчас несёт один пакет: {@link S2COpenCreditsPacket} —
 * server → all clients, открывает экран финальных титров у каждого игрока.
 */
public final class OtborNetwork {
    private static final String PROTOCOL = "1";

    // Используем уникальное имя канала - "otbor:main" вместо "labyrinthmod:main"
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("otbor", "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int nextId = 0;
    private static boolean registered = false;

    private OtborNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;

        CHANNEL.messageBuilder(S2COpenCreditsPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2COpenCreditsPacket::encode)
                .decoder(S2COpenCreditsPacket::decode)
                .consumerMainThread(S2COpenCreditsPacket::handle)
                .add();

        // Логируем без зависимости от LabyrinthMod
        System.out.println("[otbor] network channel registered with id 'otbor:main'");
    }
}