package com.labyrinthmod.client;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class LocalServerAutoAdder {

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        ServerData currentServer = mc.getCurrentServer();

        // Проверяем, что мы подключились к локальному серверу
        if (currentServer != null && isLocalServer(currentServer.ip)) {
            addOrUpdateLocalServer(mc, currentServer);
        }
    }

    private static boolean isLocalServer(String ip) {
        if (ip == null || ip.isEmpty()) return true; // Одиночная игра или LAN без явного IP

        String lowerIp = ip.toLowerCase();
        return lowerIp.startsWith("127.0.0.1") ||
                lowerIp.startsWith("localhost") ||
                lowerIp.startsWith("192.168.") ||
                lowerIp.startsWith("10.") ||
                lowerIp.startsWith("172.16.") || lowerIp.startsWith("172.17.") ||
                lowerIp.startsWith("172.18.") || lowerIp.startsWith("172.19.") ||
                lowerIp.startsWith("172.20.") || lowerIp.startsWith("172.21.") ||
                lowerIp.startsWith("172.22.") || lowerIp.startsWith("172.23.") ||
                lowerIp.startsWith("172.24.") || lowerIp.startsWith("172.25.") ||
                lowerIp.startsWith("172.26.") || lowerIp.startsWith("172.27.") ||
                lowerIp.startsWith("172.28.") || lowerIp.startsWith("172.29.") ||
                lowerIp.startsWith("172.30.") || lowerIp.startsWith("172.31.");
    }

    /**
     * ★ ДОБАВЛЕНИЕ ИЛИ ОБНОВЛЕНИЕ СЕРВЕРА В СПИСКЕ ★
     * Использует стандартные классы Minecraft 1.20.1: ServerList и ServerData.
     */
    private static void addOrUpdateLocalServer(Minecraft mc, ServerData serverData) {
        ServerList serverList = new ServerList(mc);
        serverList.load(); // Загружаем текущий список из servers.dat

        String targetIp = serverData.ip;
        String targetName = serverData.name;

        // Если имя стандартное, пытаемся получить более понятное (название мира)
        if (targetName == null || targetName.isEmpty() || targetName.equals("Minecraft Server")) {
            if (mc.getSingleplayerServer() != null) {
                targetName = "Локальный мир: " + mc.getSingleplayerServer().getWorldData().getLevelName();
            } else {
                targetName = "Локальный сервер";
            }
        }

        // Извлекаем базовый IP (без порта) для сравнения
        String baseIp = targetIp.contains(":") ? targetIp.split(":")[0] : targetIp;
        int existingIndex = -1;

        for (int i = 0; i < serverList.size(); i++) {
            ServerData existing = serverList.get(i);
            String existingBaseIp = existing.ip.contains(":") ? existing.ip.split(":")[0] : existing.ip;

            if (existingBaseIp.equalsIgnoreCase(baseIp)) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex != -1) {

            ServerData existing = serverList.get(existingIndex);
            if (!existing.ip.equals(targetIp)) {
                existing.ip = targetIp;
                existing.name = targetName;
                serverList.save();
                LabyrinthMod.LOGGER.info("Обновлен порт локального сервера в списке: {}", targetIp);
            }
        } else {

            ServerData newServer = new ServerData(targetName, targetIp, false);

            serverList.add(newServer,false);
            serverList.save();
            LabyrinthMod.LOGGER.info("Локальный сервер автоматически добавлен в список: {} ({})", targetName, targetIp);
        }
    }
}