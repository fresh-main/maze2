package com.labyrinthmod.common.event;

import com.labyrinthmod.common.generation.LabyrinthConfig;
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

    private static long lastMorningDay = -1;
    private static long lastEveningDay = -1;
    private static int currentDayDoor = -1;
    private static long currentDayForDoor = -1;

    public static long worldTickCount = 0;
    private static final long ACTIVATION_DELAY_TICKS = 300;

    private static int eveningReloadTimer = -1;
    private static final int RELOAD_DELAY_TICKS = 400;

    // ═══════════════════════════════════════════════════════════
    // ★ ИСПРАВЛЕНО: координаты дверей вычисляются динамически
    //   на основе GLADE_RADIUS из конфига, точно так же, как в
    //   StructureGenerator.updateDverPosition().
    //
    //   Формулы из StructureGenerator:
    //     dverX = -24
    //     dverY = 31
    //     dverZ = -(gladeRadius + 8)
    //
    //     dver_1 → (dverX, dverY, -dverZ - 9)  = (-24, 31, gladeRadius - 1)
    //     dver_2 → (dverX, dverY,  dverZ)       = (-24, 31, -(gladeRadius + 8))
    //     dver_3 → (-dverZ - 9, dverY, dverX)   = (gladeRadius - 1, 31, -24)
    //     dver_4 → (dverZ, dverY, dverX)         = (-(gladeRadius + 8), 31, -24)
    // ═══════════════════════════════════════════════════════════

    private static int getGladeRadius() {
        LabyrinthConfig cfg = LabyrinthConfig.getInstance();
        return cfg != null ? cfg.gleydRadius : 70;
    }

    private static BlockPos getDoor1Pos() {
        int r = getGladeRadius();
        return new BlockPos(-24, 31, r - 1);
    }

    private static BlockPos getDoor2Pos() {
        int r = getGladeRadius();
        return new BlockPos(-24, 31, -(r + 8));
    }

    private static BlockPos getDoor3Pos() {
        int r = getGladeRadius();
        return new BlockPos(r - 1, 31, -24);
    }

    private static BlockPos getDoor4Pos() {
        int r = getGladeRadius();
        return new BlockPos(-(r + 8), 31, -24);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        worldTickCount++;

        if (worldTickCount < ACTIVATION_DELAY_TICKS) {
            return;
        }

        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

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

        if (currentDayForDoor != day) {
            long mixedSeed = overworld.getSeed() ^ (day * 0x9E3779B97F4A7C15L);
            Random random = new Random(mixedSeed);
            currentDayDoor = random.nextInt(4) + 1;
            currentDayForDoor = day;
        }

        String command = "activate dver_" + currentDayDoor;

        boolean isMorning = timeOfDay >= 100 && timeOfDay < 12000;
        if (isMorning && lastMorningDay < day) {
            executeCommand(server, command);
            lastMorningDay = day;
            LOGGER.info("[DoorSchedule] Утро! Активирована команда: {}", command);
        }

        boolean isEvening = timeOfDay >= 12100 && timeOfDay < 23000;
        if (isEvening && lastEveningDay < day) {
            executeCommand(server, command);
            lastEveningDay = day;
            LOGGER.info("[DoorSchedule] Вечер! Активирована команда: {}", command);
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

    private static void reloadAllDoors(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level == null) return;

        // ★ ИСПРАВЛЕНО: используем динамические координаты вместо (0,0,0)
        reloadDoorStructure(level, "dver_1", getDoor1Pos());
        reloadDoorStructure(level, "dver_2", getDoor2Pos());
        reloadDoorStructure(level, "dver_3", getDoor3Pos());
        reloadDoorStructure(level, "dver_4", getDoor4Pos());

        LOGGER.info("[DoorSchedule] Перезагрузка энтити дверей завершена. "
                        + "GLADE_RADIUS={}, dver_1={}, dver_2={}, dver_3={}, dver_4={}",
                getGladeRadius(),
                getDoor1Pos(), getDoor2Pos(), getDoor3Pos(), getDoor4Pos());
    }

    private static void reloadDoorStructure(ServerLevel level, String structureName, BlockPos structurePos) {
        ResourceLocation loc = new ResourceLocation("labyrinthmod", structureName);
        StructureTemplateManager templateManager = level.getStructureManager();
        Optional<StructureTemplate> optTemplate = templateManager.get(loc);
        if (optTemplate.isEmpty()) return;
        StructureTemplate template = optTemplate.get();
        Vec3i size = template.getSize();
        if (size.getX() == 0 && size.getY() == 0 && size.getZ() == 0) return;

        AABB area = new AABB(
                structurePos.getX() - 1, structurePos.getY() - 1, structurePos.getZ() - 1,
                structurePos.getX() + size.getX() + 1, structurePos.getY() + size.getY() + 1, structurePos.getZ() + size.getZ() + 1
        );

        List<Entity> entities = level.getEntities((Entity) null, area, e -> true);
        for (Entity entity : entities) {
            if (!(entity instanceof net.minecraft.server.level.ServerPlayer)) {
                entity.discard();
            }
        }

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false);
        template.placeInWorld(level, structurePos, structurePos, settings, RandomSource.create(level.getSeed()), 3);
    }
}