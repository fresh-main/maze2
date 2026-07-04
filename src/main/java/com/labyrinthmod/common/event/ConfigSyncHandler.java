package com.labyrinthmod.common.event;

import com.labyrinthmod.common.config.ModConfig;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.SyncConfigPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class ConfigSyncHandler {

    // Синхронизация при входе игрока
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            syncConfigToClient(serverPlayer);
        }
    }

    // Синхронизация при изменении конфига админом (можно вызвать из команды)
    public static void syncToAllPlayers(ServerPlayer source) {
        if (source == null) return;

        CompoundTag configData = ModConfig.serializeToNbt();
        SyncConfigPacket packet = new SyncConfigPacket(configData);

        // Отправляем всем игрокам на сервере
        source.getServer().getPlayerList().getPlayers().forEach(player -> {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        });

        source.sendSystemMessage(Component.literal("§aКонфиг синхронизирован со всеми игроками!"));
    }

    // Синхронизация конкретному игроку
    public static void syncConfigToClient(ServerPlayer player) {
        CompoundTag configData = ModConfig.serializeToNbt();
        SyncConfigPacket packet = new SyncConfigPacket(configData);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    // Проверка и синхронизация при изменении конфига (можно повесить на таймер)
    public static void checkAndSyncIfChanged(ServerPlayer player) {
        if (ModConfig.hasConfigChanged()) {
            ModConfig.reload();
            syncConfigToClient(player);
        }
    }
}