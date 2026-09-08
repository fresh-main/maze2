package com.labyrinthmod.common.event;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import com.labyrinthmod.common.quest.DailyQuestManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

// 1. ДОБАВЛЕНА АННОТАЦИЯ ДЛЯ РЕГИСТРАЦИИ В EVENT BUS FORGE
@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DailyQuestSpawner {
    private static boolean hasSpawnedToday = false;
    private static long lastSavedDay = -1;

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        // Игнорируем фазу END и клиентскую сторону
        if (event.phase != TickEvent.Phase.START) return;
        if (event.level.isClientSide || !(event.level instanceof ServerLevel serverLevel)) return;

        // 2. Проверяем только для Overworld, чтобы не спавнить квесты в Аду и Энде
        if (!serverLevel.dimension().equals(Level.OVERWORLD)) return;

        long currentDayTime = serverLevel.getDayTime();
        // Используем Math.floorDiv для корректной работы, если время было изменено командами
        long currentDay = Math.floorDiv(currentDayTime, 24000L);

        // Если наступил новый игровой день
        if (currentDay != lastSavedDay) {
            lastSavedDay = currentDay;
            hasSpawnedToday = false; // Сбрасываем флаг, чтобы разрешить спавн
            LabyrinthMod.LOGGER.info("[DailyQuest] Наступил новый день ({}). Сброс флага спавна.", currentDay);
        }

        // 3. УБРАНО ЖЕСТКОЕ УСЛОВИЕ <= 100.
        // Теперь квесты спавнятся гарантированно при загрузке мира или смене дня.
        if (!hasSpawnedToday) {
            spawnDailyQuests(serverLevel);
            hasSpawnedToday = true;
            LabyrinthMod.LOGGER.info("[DailyQuest] Ежедневные задания успешно заспавнены!");
        }
    }

    private static void spawnDailyQuests(ServerLevel level) {
        List<BulletinBoardBlockEntity> allBoards = BulletinBoardBlockEntity.ALL_BOARDS;
        if (allBoards.isEmpty()) {
            LabyrinthMod.LOGGER.warn("[DailyQuest] Найдено 0 досок объявлений! Установите хотя бы одну доску в мире.");
            return;
        }

        var dailyQuests = DailyQuestManager.getRandomQuests(5);
        List<CompoundTag> questNbtList = new ArrayList<>();
        for (var quest : dailyQuests) {
            questNbtList.add(quest.toNbt());
        }

        for (BulletinBoardBlockEntity board : allBoards) {
            board.spawnDailyQuests(questNbtList);
        }
    }
}