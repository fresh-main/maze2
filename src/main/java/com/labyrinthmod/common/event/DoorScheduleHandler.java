package com.labyrinthmod.common.event;

import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;

/**
 * ★ РАСПИСАНИЕ ДВЕРЕЙ ★
 * Утром и вечером каждого дня активирует одну случайную дверь из 4-х.
 * Активация начинается не ранее чем через 300 тиков после загрузки мира.
 */
@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class DoorScheduleHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    // Состояние для отслеживания, какие команды уже были выполнены сегодня
    private static long lastMorningDay = -1;
    private static long lastEveningDay = -1;

    // Кэш выбранной двери на текущий день
    private static int currentDayDoor = -1;
    private static long currentDayForDoor = -1;

    // ★ СЧЁТЧИК ТИКОВ С МОМЕНТА ЗАГРУЗКИ МИРА ★
    private static long worldTickCount = 0;
    private static final long ACTIVATION_DELAY_TICKS = 300; // Задержка 300 тиков (15 секунд)

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // ★ УВЕЛИЧИВАЕМ СЧЁТЧИК КАЖДЫЙ ТИК ★
        worldTickCount++;

        // ★ НЕ АКТИВИРУЕМ ДВЕРИ, ПОКА НЕ ПРОШЛО 300 ТИКОВ ★
        if (worldTickCount < ACTIVATION_DELAY_TICKS) {
            return;
        }

        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        long dayTime = overworld.getDayTime();
        long day = dayTime / 24000L;
        long timeOfDay = dayTime % 24000L;

        // ==========================================
        // 1. ВЫБИРАЕМ ДВЕРЬ НА СЕГОДНЯ
        // ==========================================
        if (currentDayForDoor != day) {
            Random random = new Random(overworld.getSeed() ^ day);
            currentDayDoor = random.nextInt(4) + 1;
            currentDayForDoor = day;
        }

        String command = "activate dver_" + currentDayDoor;

        // ==========================================
        // 2. ПРОВЕРКА УТРА (с 100 до 11999 тиков)
        // ==========================================
        boolean isMorning = timeOfDay >= 100 && timeOfDay < 12000;
        if (isMorning && lastMorningDay < day) {
            executeCommand(server, command);
            lastMorningDay = day;
            LOGGER.info("[DoorSchedule] Утро! Активирована команда: {}", command);
        }

        // ==========================================
        // 3. ПРОВЕРКА ВЕЧЕРА (с 12100 до 23999 тиков)
        // ==========================================
        boolean isEvening = timeOfDay >= 12100 && timeOfDay < 23000;
        if (isEvening && lastEveningDay < day) {
            executeCommand(server, command);
            lastEveningDay = day;
            LOGGER.info("[DoorSchedule] Вечер! Активирована команда: {}", command);
        }
    }

    private static void executeCommand(MinecraftServer server, String command) {
        try {
            CommandSourceStack source = server.createCommandSourceStack();
            ParseResults<CommandSourceStack> parseResults =
                    server.getCommands().getDispatcher().parse(command, source);
            server.getCommands().performCommand(parseResults, command);
        } catch (Exception e) {
            LOGGER.error("[DoorSchedule] Ошибка при выполнении команды: {}", command, e);
        }
    }
}