package com.labyrinthmod.common.quest;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import com.labyrinthmod.common.event.LiftCommandHandler;
import com.labyrinthmod.common.event.LiftLockManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Отслеживает выполнение ВСЕХ 5 ежедневных заданий.
 * Задания могут быть выполнены:
 *  - Прямо на доске (кнопка "Выполнить")
 *  - Через свиток в инвентаре (кнопка "Выполнить задание")
 *
 * Когда все 5 выполнены → запускает двойную активацию лифта БЕЗ телепортации.
 */
public class QuestCompletionTracker {

    // Какие слоты заданий уже выполнены (0..4)
    private static final Set<Integer> completedSlots = new HashSet<>();

    // Флаг: ивент лифта уже запущен в текущем цикле
    private static boolean liftEventTriggered = false;

    // Флаг: лифт существует в мире
    private static boolean liftExists = false;

    private static final int TOTAL_QUESTS = 5;

    /**
     * Вызывается при обновлении доски (новый день). Сбрасывает всё.
     */
    public static void resetForNewDay() {
        completedSlots.clear();
        liftEventTriggered = false;
        LabyrinthMod.LOGGER.info("[QuestTracker] Сброс трекера заданий для нового дня.");
    }

    /**
     * Отметить задание как выполненное.
     * @param slotIndex индекс слота задания (0-4)
     */
    public static void markQuestCompleted(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= TOTAL_QUESTS) return;
        if (liftEventTriggered) return; // уже запущено, не дублируем

        if (!completedSlots.add(slotIndex)) {
            LabyrinthMod.LOGGER.info("[QuestTracker] Задание #{} уже учтено. Прогресс: {}/{}",
                    slotIndex + 1, completedSlots.size(), TOTAL_QUESTS);
            return;
        }
        LabyrinthMod.LOGGER.info("[QuestTracker] Задание #{} выполнено. Прогресс: {}/{}",
                slotIndex + 1, completedSlots.size(), TOTAL_QUESTS);

        if (completedSlots.size() >= TOTAL_QUESTS) {
            tryTriggerLiftEvent();
        }
    }

    /**
     * Проверяет существование лифта и запускает ивент.
     */
    private static void tryTriggerLiftEvent() {
        if (liftEventTriggered) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        // ★ ПРОВЕРКА: ЛИФТ ДОЛЖЕН СУЩЕСТВОВАТЬ ★
        if (!LiftCommandHandler.isLiftGenerated(overworld)) {
            System.out.println("[QuestTracker] Все задания выполнены, но лифт не сгенерирован. Ивент пропущен.");
            return;
        }

        liftExists = true;
        liftEventTriggered = true;

        System.out.println("[QuestTracker] ★ ВСЕ 5 ЗАДАНИЙ ВЫПОЛНЕНЫ! Запуск двойной активации лифта (без телепортации).");

        // Блокируем мир для новых игроков
        // Используем UUID "quest_system" как идентификатор инициатора
        UUID questInitiator = UUID.nameUUIDFromBytes("quest_lift_event".getBytes());
        LiftLockManager.lock(questInitiator);

        // Запускаем ивент лифта без телепортации
        LiftCommandHandler.startQuestLiftEvent();
    }

    /**
     * Сколько заданий выполнено.
     */
    public static int getCompletedCount() {
        return completedSlots.size();
    }

    /**
     * Все ли задания выполнены.
     */
    public static boolean allCompleted() {
        return completedSlots.size() >= TOTAL_QUESTS;
    }

    /**
     * Запущен ли уже ивент лифта.
     */
    public static boolean isLiftEventTriggered() {
        return liftEventTriggered;
    }

    /**
     * Сброс после завершения ивента лифта (вызывается из LiftCommandHandler).
     */
    public static void onLiftEventFinished() {
        liftEventTriggered = false;
        liftExists = false;
        completedSlots.clear();
        System.out.println("[QuestTracker] Ивент лифта завершён. Трекер сброшен.");
    }
}
