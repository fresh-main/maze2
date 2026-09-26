package com.labyrinthmod.common.event;

import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Random;

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
    public static long worldTickCount = 0;
    private static final long ACTIVATION_DELAY_TICKS = 300; // Задержка 300 тиков (15 секунд)

    // ★ ТАЙМЕР ДЛЯ ПЕРЕЗАГРУЗКИ ЭНТИТИ ДВЕРЕЙ ЧЕРЕЗ 20 СЕКУНД (400 ТИКОВ) ★
    private static int eveningReloadTimer = -1;
    private static final int RELOAD_DELAY_TICKS = 400;

    // TODO: Укажите реальные координаты ваших дверей (X, Y, Z)
    private static final BlockPos DOOR_1_POS = new BlockPos(0, 0, 0);
    private static final BlockPos DOOR_2_POS = new BlockPos(0, 0, 0);
    private static final BlockPos DOOR_3_POS = new BlockPos(0, 0, 0);
    private static final BlockPos DOOR_4_POS = new BlockPos(0, 0, 0);

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

        // ★ ОБРАБОТКА ТАЙМЕРА ПЕРЕЗАГРУЗКИ ЭНТИТИ ★
        if (eveningReloadTimer > 0) {
            eveningReloadTimer--;
            if (eveningReloadTimer == 0) {
                LOGGER.info("[DoorSchedule] Прошло 20 секунд. Перезагрузка энтити всех 4 дверей...");
                reloadAllDoors(server);
            }
        }

        long dayTime = overworld.getDayTime();
        long day = dayTime / 24000L;
        long timeOfDay = dayTime % 24000L;

        // ==========================================
        // 1. ВЫБИРАЕМ ДВЕРЬ НА СЕГОДНЯ
        // ==========================================
        if (currentDayForDoor != day) {
            long mixedSeed = overworld.getSeed() ^ (day * 0x9E3779B97F4A7C15L);
            Random random = new Random(mixedSeed);
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

            // Запускаем таймер на перезагрузку энтити через 20 секунд (400 тиков)
            eveningReloadTimer = RELOAD_DELAY_TICKS;
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

    // ═══════════════════════════════════════════════════════════
    // ★ ПЕРЕЗАГРУЗКА ЭНТИТИ И СТРУКТУР ДВЕРЕЙ (КАК В ЛИФТЕ) ★
    // ═══════════════════════════════════════════════════════════
    private static void reloadAllDoors(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level == null) return;

        reloadDoorStructure(level, "dver_1", DOOR_1_POS);
        reloadDoorStructure(level, "dver_2", DOOR_2_POS);
        reloadDoorStructure(level, "dver_3", DOOR_3_POS);
        reloadDoorStructure(level, "dver_4", DOOR_4_POS);

        LOGGER.info("[DoorSchedule] Перезагрузка энтити дверей завершена.");
    }

    private static void reloadDoorStructure(ServerLevel level, String structureName, BlockPos structurePos) {
        ResourceLocation loc = new ResourceLocation("labyrinthmod", structureName);
        StructureTemplateManager templateManager = level.getStructureManager();
        Optional<StructureTemplate> optTemplate = templateManager.get(loc);
        if (optTemplate.isEmpty()) return;

        StructureTemplate template = optTemplate.get();
        Vec3i size = template.getSize();
        if (size.getX() == 0 && size.getY() == 0 && size.getZ() == 0) return;

        // Зона для очистки энтити (с небольшим запасом по 1 блоку с каждой стороны)
        AABB area = new AABB(
                structurePos.getX() - 1, structurePos.getY() - 1, structurePos.getZ() - 1,
                structurePos.getX() + size.getX() + 1, structurePos.getY() + size.getY() + 1, structurePos.getZ() + size.getZ() + 1
        );

        // Удаляем все энтити в зоне двери (кроме игроков)
        List<Entity> entities = level.getEntities((Entity) null, area, e -> true);
        for (Entity entity : entities) {
            if (!(entity instanceof net.minecraft.server.level.ServerPlayer)) {
                entity.discard();
            }
        }

        // Пересоздаем структуру (аналогично логике лифта)
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false);

        template.placeInWorld(level, structurePos, structurePos, settings, RandomSource.create(level.getSeed()), 3);
    }
}