package com.labyrinthmod.common.event;

import com.labyrinthmod.common.generation.StructureGenerator;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Отдельный обработчик расписания для 8 секторных дверей:
 * sector_1 ... sector_8.
 *
 * Логика:
 * - При первом запуске мира генерируется случайный порядок дверей 1..8.
 * - Порядок сохраняется в SavedData мира.
 * - Каждый игровой день открывается следующая дверь по этому порядку.
 * - После 8 дней цикл начинается заново, но порядок остаётся тем же.
 * - Утро/вечер и перезагрузка энтити работают по той же схеме, что и у дверей глейда.
 */
@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class SectorDoorScheduleHandler {

    private static final Logger LOGGER = LogManager.getLogger();

    private static long worldTickCount = 0;
    private static final long ACTIVATION_DELAY_TICKS = 300; // 15 секунд после старта сервера

    private static long lastMorningDay = -1;
    private static long lastEveningDay = -1;

    private static int reloadTimer = -1;
    private static final int RELOAD_DELAY_TICKS = 400; // 20 секунд после вечерней активации

    private static final String DATA_NAME = "labyrinth_sector_door_schedule";
    private static final int DOOR_COUNT = 8;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        worldTickCount++;

        // Не активируем двери, пока мир/сервер толком не прогрелись.
        if (worldTickCount < ACTIVATION_DELAY_TICKS) {
            return;
        }

        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }

        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        // Перезагрузка энтити секторных дверей через 20 секунд после вечера.
        if (reloadTimer > 0) {
            reloadTimer--;
            if (reloadTimer == 0) {
                LOGGER.info("[SectorDoorSchedule] Прошло 20 секунд. Перезагрузка энтити всех 8 секторных дверей...");
                reloadAllSectorDoors(server);
            }
        }

        long dayTime = overworld.getDayTime();
        long day = dayTime / 24000L;
        long timeOfDay = dayTime % 24000L;

        SectorDoorScheduleData data = getScheduleData(overworld);
        data.ensureDay(day);

        int door = data.currentDoor();
        String command = "activate sector_" + door;

        boolean isMorning = timeOfDay >= 100 && timeOfDay < 12000;
        boolean isEvening = timeOfDay >= 12100 && timeOfDay < 23000;

        // Утро: открываем/активируем секторную дверь.
        if (isMorning && lastMorningDay < day) {
            executeCommand(server, command);
            lastMorningDay = day;
            LOGGER.info("[SectorDoorSchedule] Утро! Активирована секторная дверь: {}", command);
        }

        // Вечер: открываем/активируем ту же секторную дверь и ставим таймер перезагрузки.
        if (isEvening && lastEveningDay < day) {
            executeCommand(server, command);
            lastEveningDay = day;
            reloadTimer = RELOAD_DELAY_TICKS;
            LOGGER.info("[SectorDoorSchedule] Вечер! Активирована секторная дверь: {}", command);
        }
    }

    private static SectorDoorScheduleData getScheduleData(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();

        SectorDoorScheduleData data = storage.computeIfAbsent(
                SectorDoorScheduleData::load,
                SectorDoorScheduleData::new,
                DATA_NAME
        );

        data.initIfMissing(level.getSeed());
        return data;
    }

    private static void executeCommand(MinecraftServer server, String command) {
        try {
            CommandSourceStack source = server.createCommandSourceStack();
            ParseResults<CommandSourceStack> parseResults =
                    server.getCommands().getDispatcher().parse(command, source);
            server.getCommands().performCommand(parseResults, command);
        } catch (Exception e) {
            LOGGER.error("[SectorDoorSchedule] Ошибка при выполнении команды: {}", command, e);
        }
    }

    private static void reloadAllSectorDoors(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level == null) {
            return;
        }

        int reloaded = 0;

        for (int i = 1; i <= DOOR_COUNT; i++) {
            BlockPos pos = StructureGenerator.getSectorDoorPos(i);
            if (pos == null) {
                LOGGER.warn("[SectorDoorSchedule] Нет позиции для секторной двери sector_{}", i);
                continue;
            }

            reloadDoorStructure(level, "sector_" + i, pos);
            reloaded++;
        }

        LOGGER.info("[SectorDoorSchedule] Перезагрузка энтити секторных дверей завершена. Перезагружено: {}", reloaded);
    }

    private static void reloadDoorStructure(ServerLevel level, String structureName, BlockPos structurePos) {
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("labyrinthmod", structureName);

        StructureTemplateManager templateManager = level.getStructureManager();
        Optional<StructureTemplate> optTemplate = templateManager.get(loc);

        if (optTemplate.isEmpty()) {
            return;
        }

        StructureTemplate template = optTemplate.get();
        Vec3i size = template.getSize();

        if (size.getX() == 0 && size.getY() == 0 && size.getZ() == 0) {
            return;
        }

        // Зона для очистки энтити с небольшим запасом.
        AABB area = new AABB(
                structurePos.getX() - 1,
                structurePos.getY() - 1,
                structurePos.getZ() - 1,
                structurePos.getX() + size.getX() + 1,
                structurePos.getY() + size.getY() + 1,
                structurePos.getZ() + size.getZ() + 1
        );

        List<Entity> entities = level.getEntities((Entity) null, area, e -> true);

        for (Entity entity : entities) {
            if (!(entity instanceof ServerPlayer)) {
                entity.discard();
            }
        }

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false);

        template.placeInWorld(
                level,
                structurePos,
                structurePos,
                settings,
                RandomSource.create(level.getSeed()),
                3
        );
    }

    /**
     * SavedData для хранения порядка секторных дверей.
     *
     * Пример порядка:
     * [3, 8, 1, 6, 2, 7, 4, 5]
     *
     * День 0: sector_3
     * День 1: sector_8
     * День 2: sector_1
     * ...
     * День 8: снова sector_3
     */
    public static class SectorDoorScheduleData extends SavedData {

        private int[] order = new int[DOOR_COUNT];
        private int currentIndex = 0;
        private long lastDay = -1L;

        public SectorDoorScheduleData() {
        }

        private SectorDoorScheduleData(CompoundTag tag) {
            this.order = tag.getIntArray("order");
            this.currentIndex = tag.getInt("currentIndex");
            this.lastDay = tag.getLong("lastDay");

            if (!isValidOrder(this.order)) {
                this.order = new int[DOOR_COUNT];
            }

            if (this.currentIndex < 0 || this.currentIndex >= DOOR_COUNT) {
                this.currentIndex = 0;
            }
        }

        public static SectorDoorScheduleData load(CompoundTag tag) {
            return new SectorDoorScheduleData(tag);
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            if (!isValidOrder(order)) {
                order = new int[DOOR_COUNT];
                for (int i = 0; i < DOOR_COUNT; i++) {
                    order[i] = i + 1;
                }
            }

            if (currentIndex < 0 || currentIndex >= DOOR_COUNT) {
                currentIndex = 0;
            }

            tag.putIntArray("order", order);
            tag.putInt("currentIndex", currentIndex);
            tag.putLong("lastDay", lastDay);

            return tag;
        }

        public void initIfMissing(long worldSeed) {
            if (!isValidOrder(order)) {
                generateOrder(new Random(worldSeed ^ 0x9E3779B97F4A7C15L));
                setDirty();
            }

            if (currentIndex < 0 || currentIndex >= DOOR_COUNT) {
                currentIndex = 0;
                setDirty();
            }
        }

        public void ensureDay(long day) {
            if (lastDay == day) {
                return;
            }

            if (lastDay < 0) {
                currentIndex = 0;
            } else {
                long diff = day - lastDay;
                long next = currentIndex + diff;
                currentIndex = (int) Math.floorMod(next, (long) DOOR_COUNT);
            }

            if (currentIndex < 0 || currentIndex >= DOOR_COUNT) {
                currentIndex = 0;
            }

            lastDay = day;
            setDirty();
        }

        public int currentDoor() {
            if (!isValidOrder(order)) {
                return 1;
            }

            if (currentIndex < 0 || currentIndex >= DOOR_COUNT) {
                currentIndex = 0;
            }

            return order[currentIndex];
        }

        private void generateOrder(Random random) {
            List<Integer> values = new ArrayList<>(DOOR_COUNT);

            for (int i = 1; i <= DOOR_COUNT; i++) {
                values.add(i);
            }

            Collections.shuffle(values, random);

            order = new int[DOOR_COUNT];

            for (int i = 0; i < DOOR_COUNT; i++) {
                order[i] = values.get(i);
            }
        }

        private static boolean isValidOrder(int[] arr) {
            if (arr == null || arr.length != DOOR_COUNT) {
                return false;
            }

            boolean[] seen = new boolean[DOOR_COUNT + 1];

            for (int value : arr) {
                if (value < 1 || value > DOOR_COUNT) {
                    return false;
                }

                if (seen[value]) {
                    return false;
                }

                seen[value] = true;
            }

            return true;
        }
    }
}