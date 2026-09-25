package com.labyrinthmod.common.blockentity;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.init.ModBlockEntities;
import com.labyrinthmod.common.item.TaskScrollItem;
import com.labyrinthmod.common.menu.BulletinBoardMenu;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.SyncBoardDataPacket;
import com.labyrinthmod.common.network.packet.SyncTasksPacket;
import com.labyrinthmod.common.quest.DailyQuest;
import com.labyrinthmod.common.quest.DailyQuestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BulletinBoardBlockEntity extends BlockEntity implements Container, net.minecraft.world.MenuProvider {
    private static final int MAX_TASKS = 5;
    private final List<ItemStack> tasks = new ArrayList<>(MAX_TASKS);
    private final List<Boolean> taskTaken = new ArrayList<>(MAX_TASKS);

    private static final boolean CONSUME_ITEMS_ON_COMPLETE = true;

    // Старые поля (оставлены для совместимости с вашими пакетами и GUI)
    private int spawnIntervalSeconds = 30;
    private final List<CompoundTag> preloadedTasks = new ArrayList<>();
    private int spawnTimer = 0;
    private int preloadedIndex = 0;

    // НОВАЯ переменная для ежедневного спавна (сохраняется в NBT)
    private long lastSpawnedDay = -1;

    public static final List<BulletinBoardBlockEntity> ALL_BOARDS = new CopyOnWriteArrayList<>();

    public BulletinBoardBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.BULLETIN_BOARD_BE.get(), pos, blockState);
        for (int i = 0; i < MAX_TASKS; i++) {
            tasks.add(ItemStack.EMPTY);
            taskTaken.add(false);
        }
        ALL_BOARDS.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ALL_BOARDS.remove(this);
    }

    // ==========================================
    // НОВАЯ ЛОГИКА ЕЖЕДНЕВНОГО СПАВНА
    // ==========================================

    /**
     * Новый метод для DailyQuestSpawner: принимает список NBT и заполняет доску.
     */
    public void spawnDailyQuests(List<CompoundTag> questNbtList) {
        // ★ Сброс трекера при спавне новых заданий ★
        com.labyrinthmod.common.quest.QuestCompletionTracker.resetForNewDay();

        clearAllTasks();
        if (level != null) {
            lastSpawnedDay = level.getDayTime() / 24000L;
        }
        for (int slot = 0; slot < MAX_TASKS && slot < questNbtList.size(); slot++) {
            ItemStack taskStack = createTaskItemStack(questNbtList.get(slot));
            if (!taskStack.isEmpty()) {
                tasks.set(slot, taskStack);
                taskTaken.set(slot, false);
            }
        }
        setChanged();

        if (level != null && !level.isClientSide) {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                            worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64, level.dimension()
                    )),
                    new SyncTasksPacket(worldPosition, tasks)
            );
        }

        LabyrinthMod.LOGGER.info("[BulletinBoard] Ежедневные задания успешно обновлены!");
    }

    // ИСПРАВЛЕННЫЙ TICK - теперь проверяет смену игрового дня
    public void tick() {
        if (level == null || level.isClientSide) return;

        long dayTime = level.getDayTime();
        long currentDay = dayTime / 24000L;

        // Если сменился день и сейчас утро (до 2000 тиков, то есть с 6:00 до 8:00)
        if (lastSpawnedDay < currentDay && (dayTime % 24000L) < 2000) {
            refreshDailyQuests();
            lastSpawnedDay = currentDay;
            setChanged(); // Сохраняем день в NBT
        }
    }

    private void refreshDailyQuests() {
        List<DailyQuest> quests = DailyQuestManager.getRandomQuests(5);
        List<CompoundTag> nbtList = new ArrayList<>();
        for (DailyQuest q : quests) nbtList.add(q.toNbt());
        spawnDailyQuests(nbtList);
    }

    // ==========================================
    // СТАРЫЕ МЕТОДЫ (ДЛЯ СОВМЕСТИМОСТИ С ПАКЕТАМИ)
    // ==========================================
    public int getSpawnIntervalSeconds() { return spawnIntervalSeconds; }
    public void setSpawnIntervalSeconds(int seconds) {
        this.spawnIntervalSeconds = Math.max(1, seconds);
        setChanged();
        if (level != null && !level.isClientSide) syncData();
    }
    public List<CompoundTag> getPreloadedTasks() { return preloadedTasks; }
    public int getPreloadedTasksCount() { return preloadedTasks.size(); }
    public int getSpawnTimer() { return spawnTimer; }
    public int getTicksPerSpawn() { return spawnIntervalSeconds * 20; }
    public void addPreloadedTask(CompoundTag taskData) {
        preloadedTasks.add(taskData.copy());
        setChanged();
        if (level != null && !level.isClientSide) syncData();
    }
    public void clearAllTasks() {
        for (int i = 0; i < MAX_TASKS; i++) {
            tasks.set(i, ItemStack.EMPTY);
            taskTaken.set(i, false);
        }
        preloadedTasks.clear();
        setChanged();
    }
    public void spawnAllTasksImmediately() {
        for (int i = 0; i < MAX_TASKS; i++) {
            tasks.set(i, ItemStack.EMPTY);
            taskTaken.set(i, false);
        }
        for (int slot = 0; slot < MAX_TASKS && slot < preloadedTasks.size(); slot++) {
            ItemStack taskStack = createTaskItemStack(preloadedTasks.get(slot));
            if (!taskStack.isEmpty()) {
                tasks.set(slot, taskStack);
                taskTaken.set(slot, false);
            }
        }
        preloadedTasks.clear();
        preloadedIndex = 0;
        setChanged();
        if (level != null && !level.isClientSide) {
            NetworkHandler.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64, level.dimension())), new SyncTasksPacket(worldPosition, tasks));
        }
    }
    public void removePreloadedTask(int index) {
        if (index >= 0 && index < preloadedTasks.size()) {
            preloadedTasks.remove(index);
            setChanged();
            if (level != null && !level.isClientSide) syncData();
        }
    }
    private void syncData() {
        NetworkHandler.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64, level.dimension())), new SyncBoardDataPacket(worldPosition, spawnIntervalSeconds, spawnTimer, preloadedTasks));
    }
    private ItemStack createTaskItemStack(CompoundTag taskData) {
        ResourceLocation itemId = ResourceLocation.fromNamespaceAndPath("labyrinthmod", "task_item");
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        stack.setTag(taskData.getCompound("tag").copy());
        return stack;
    }
    public void spawnTask() {
        if (preloadedTasks.isEmpty()) {
            DailyQuest quest = DailyQuestManager.getRandomQuest();
            if (quest != null) preloadedTasks.add(quest.toNbt());
            else return;
        }
        int targetSlot = -1;
        for (int i = 0; i < MAX_TASKS; i++) { if (tasks.get(i).isEmpty()) { targetSlot = i; break; } }
        if (targetSlot == -1) { for (int i = 0; i < MAX_TASKS; i++) { if (taskTaken.get(i)) { targetSlot = i; break; } } }
        if (targetSlot == -1) return;

        ItemStack taskStack = createTaskItemStack(preloadedTasks.get(0));
        if (taskStack.isEmpty()) { preloadedTasks.remove(0); return; }

        tasks.set(targetSlot, taskStack);
        taskTaken.set(targetSlot, false);
        preloadedTasks.remove(0);
        preloadedIndex++;
        setChanged();
        if (level != null && !level.isClientSide) {
            NetworkHandler.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64, level.dimension())), new SyncTasksPacket(worldPosition, tasks));
        }
    }
    public void spawnAllTasks() {
        int spawned = 0;
        while (!preloadedTasks.isEmpty() && spawned < MAX_TASKS) {
            boolean hasFreeSlot = false;
            for (int i = 0; i < MAX_TASKS; i++) { if (tasks.get(i).isEmpty() || taskTaken.get(i)) { hasFreeSlot = true; break; } }
            if (!hasFreeSlot) break;
            spawnTask();
            spawned++;
        }
    }
    public void spawnSpecificTask(int index) {
        if (index < 0 || index >= preloadedTasks.size()) return;
        ItemStack taskStack = createTaskItemStack(preloadedTasks.get(index));
        if (taskStack.isEmpty()) return;
        int targetSlot = -1;
        for (int i = 0; i < MAX_TASKS; i++) { if (tasks.get(i).isEmpty() || taskTaken.get(i)) { targetSlot = i; break; } }
        if (targetSlot == -1) return;
        tasks.set(targetSlot, taskStack);
        taskTaken.set(targetSlot, false);
        preloadedTasks.remove(index);
        preloadedIndex++;
        setChanged();
        if (level != null && !level.isClientSide) {
            NetworkHandler.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64, level.dimension())), new SyncTasksPacket(worldPosition, tasks));
            NetworkHandler.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64, level.dimension())), new SyncBoardDataPacket(worldPosition, spawnIntervalSeconds, spawnTimer, preloadedTasks));
        }
    }

    // НОВЫЙ МЕТОД ДЛЯ SyncTasksPacket
    public void syncTasksFromServer(List<ItemStack> newTasks) {
        tasks.clear();
        for (int i = 0; i < MAX_TASKS; i++) {
            tasks.add(i < newTasks.size() ? newTasks.get(i) : ItemStack.EMPTY);
        }
        setChanged();
    }


    public void syncDataFromServer(int interval, int timer, List<CompoundTag> tasksList) {
        this.spawnIntervalSeconds = interval;
        this.spawnTimer = timer;
        this.preloadedTasks.clear();
        for (CompoundTag tag : tasksList) this.preloadedTasks.add(tag.copy());
    }
    public void sendSyncToPlayer(ServerPlayer player) {
        if (player == null) return;
        if (level == null || level.isClientSide) return;

        sendTasksToPlayer(player);

        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncBoardDataPacket(
                        worldPosition,
                        spawnIntervalSeconds,
                        spawnTimer,
                        preloadedTasks
                )
        );
    }
    public void resetTimer() {
        this.spawnTimer = 0;
        setChanged();
        if (level != null && !level.isClientSide) syncData();
    }

    public boolean takeTaskAsScroll(int slot, Player player) {
        if (level == null || level.isClientSide) {
            return false;
        }

        if (slot < 0 || slot >= MAX_TASKS) {
            return false;
        }

        ItemStack task = tasks.get(slot);

        if (task.isEmpty() || !task.hasTag()) {
            return false;
        }

        CompoundTag taskTag = task.getTag();
        if (taskTag == null) {
            return false;
        }

        ItemStack scroll = new ItemStack(TaskScrollItem.TASK_SCROLL.get());
        CompoundTag scrollTag = taskTag.copy();
        scroll.setTag(scrollTag);

        scrollTag.putBoolean("Completed", false);
        scrollTag.putInt("QuestSlotIndex", slot);

        // Если инвентарь полный — не забираем задание, чтобы не потерять квест.
        if (!player.getInventory().add(scroll)) {
            player.sendSystemMessage(Component.literal("§cНет места в инвентаре! Задание не забрано."));
            return false;
        }

        // Удаляем карточку только после успешной вставки свитка в инвентарь.
        tasks.set(slot, ItemStack.EMPTY);
        taskTaken.set(slot, true);
        setChanged();

        // Сразу обновляем открытый контейнер, чтобы свиток появился без задержки.
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.containerMenu.broadcastChanges();
            serverPlayer.inventoryMenu.broadcastChanges();
            serverPlayer.getInventory().setChanged();
            sendTasksToPlayer(serverPlayer);
        }
        sendTasksToNear();

        player.sendSystemMessage(Component.literal("§aЗадание взято! Свиток добавлен в инвентарь."));

        return true;
    }

    public ItemStack getTask(int slot) { return (slot < 0 || slot >= MAX_TASKS) ? ItemStack.EMPTY : tasks.get(slot); }
    public boolean isTaskTaken(int slot) { return slot >= 0 && slot < taskTaken.size() && taskTaken.get(slot); }

    // ==========================================
    // СОХРАНЕНИЕ И ЗАГРУЗКА (NBT)
    // ==========================================
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tasks.clear();
        taskTaken.clear();
        ListTag tasksTag = tag.getList("Tasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < MAX_TASKS; i++) tasks.add(i < tasksTag.size() ? ItemStack.of(tasksTag.getCompound(i)) : ItemStack.EMPTY);

        ListTag takenTag = tag.getList("TaskTaken", Tag.TAG_BYTE);
        for (int i = 0; i < MAX_TASKS; i++) taskTaken.add(i < takenTag.size() && ((ByteTag) takenTag.get(i)).getAsByte() != 0);

        // Старые данные
        spawnIntervalSeconds = tag.getInt("SpawnInterval");
        if (spawnIntervalSeconds <= 0) spawnIntervalSeconds = 30;
        preloadedIndex = tag.getInt("PreloadedIndex");
        ListTag preloadedTag = tag.getList("PreloadedTasks", Tag.TAG_COMPOUND);
        preloadedTasks.clear();
        for (int i = 0; i < preloadedTag.size(); i++) preloadedTasks.add(preloadedTag.getCompound(i).copy());

        // НОВАЯ переменная
        if (tag.contains("LastSpawnedDay")) lastSpawnedDay = tag.getLong("LastSpawnedDay");
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag tasksTag = new ListTag();
        for (ItemStack task : tasks) tasksTag.add(task.save(new CompoundTag()));
        tag.put("Tasks", tasksTag);

        ListTag takenTag = new ListTag();
        for (boolean taken : taskTaken) takenTag.add(ByteTag.valueOf(taken));
        tag.put("TaskTaken", takenTag);

        // Старые данные
        tag.putInt("SpawnInterval", spawnIntervalSeconds);
        tag.putInt("PreloadedIndex", preloadedIndex);
        ListTag preloadedTag = new ListTag();
        for (CompoundTag taskData : preloadedTasks) preloadedTag.add(taskData);
        tag.put("PreloadedTasks", preloadedTag);

        // НОВАЯ переменная
        tag.putLong("LastSpawnedDay", lastSpawnedDay);
    }
    public void clearTaskVisual(int slot) {
        if (slot < 0 || slot >= MAX_TASKS) return;

        tasks.set(slot, ItemStack.EMPTY);
        taskTaken.set(slot, true);
    }
    private List<ItemStack> copyTasks() {
        List<ItemStack> copy = new ArrayList<>(MAX_TASKS);

        for (ItemStack stack : tasks) {
            copy.add(stack.copy());
        }

        return copy;
    }

    public void sendTasksToPlayer(ServerPlayer player) {
        if (player == null) return;
        if (level == null || level.isClientSide) return;

        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncTasksPacket(worldPosition, copyTasks())
        );
    }


    public void sendTasksToNear() {
        if (level == null || level.isClientSide) return;

        NetworkHandler.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        worldPosition.getX(),
                        worldPosition.getY(),
                        worldPosition.getZ(),
                        64,
                        level.dimension()
                )),
                new SyncTasksPacket(worldPosition, copyTasks())
        );
    }
    public boolean completeTask(int slot, ServerPlayer player) {
        if (level == null || level.isClientSide) {
            return false;
        }

        if (slot < 0 || slot >= MAX_TASKS) {
            return false;
        }

        if (player == null) {
            return false;
        }

        if (!stillValid(player)) {
            player.sendSystemMessage(Component.literal("§cВы слишком далеко от доски заданий."));
            return false;
        }

        ItemStack task = tasks.get(slot);

        if (task.isEmpty() || !task.hasTag()) {
            player.sendSystemMessage(Component.literal("§cЭто задание уже недоступно."));
            sendTasksToPlayer(player);
            return false;
        }

        if (isTaskTaken(slot)) {
            player.sendSystemMessage(Component.literal("§cЭто задание уже выполнено или забрано."));
            sendTasksToPlayer(player);
            return false;
        }

        CompoundTag taskTag = task.getTag();
        if (taskTag == null) {
            player.sendSystemMessage(Component.literal("§cОшибка чтения данных задания."));
            return false;
        }

        String questTitle = taskTag.getString("Title");
        if (questTitle.isEmpty()) {
            questTitle = "Без названия";
        }

        if (!taskTag.contains("RequiredItems", Tag.TAG_LIST)) {
            finishTask(slot, player, questTitle);
            return true;
        }

        ListTag requiredItems = taskTag.getList("RequiredItems", Tag.TAG_COMPOUND);

        // Проверка наличия предметов
        for (int i = 0; i < requiredItems.size(); i++) {
            CompoundTag itemTag = requiredItems.getCompound(i);

            String itemId = getRequiredItemId(itemTag);
            int count = getRequiredItemCount(itemTag);

            if (itemId.isEmpty()) {
                player.sendSystemMessage(Component.literal("§cВ задании указан пустой предмет."));
                return false;
            }

            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null) {
                player.sendSystemMessage(Component.literal("§cНекорректный ID предмета: " + itemId));
                return false;
            }

            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null || item == Items.AIR) {
                player.sendSystemMessage(Component.literal("§cНеизвестный предмет: " + itemId));
                return false;
            }

            int has = player.getInventory().countItem(item);

            if (has < count) {
                player.sendSystemMessage(
                        Component.literal("§cНе хватает предметов для задания \"" + questTitle + "\".")
                );
                return false;
            }
        }

        // Списывание предметов (если включено)
        if (CONSUME_ITEMS_ON_COMPLETE) {
            for (int i = 0; i < requiredItems.size(); i++) {
                CompoundTag itemTag = requiredItems.getCompound(i);

                String itemId = getRequiredItemId(itemTag);
                int count = getRequiredItemCount(itemTag);

                ResourceLocation rl = ResourceLocation.tryParse(itemId);
                if (rl == null) continue;

                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item == null || item == Items.AIR) continue;

                removeItemsFromInventory(player, item, count);
            }
        }

        finishTask(slot, player, questTitle);
        return true;
    }
    private void finishTask(int slot, ServerPlayer player, String questTitle) {
        tasks.set(slot, ItemStack.EMPTY);
        taskTaken.set(slot, true);

        setChanged();
        com.labyrinthmod.common.quest.QuestCompletionTracker.markQuestCompleted(slot);

        player.sendSystemMessage(Component.literal("§aЗадание \"" + questTitle + "\" выполнено!"));

        // Если вдруг в NБТ есть награда опытом — можно выдать
        ItemStack task = tasks.get(slot);
        if (!task.isEmpty() && task.hasTag()) {
            CompoundTag tag = task.getTag();
            int xpReward = tag.getInt("XpReward");
            if (xpReward > 0) {
                player.giveExperiencePoints(xpReward);
            }
        }

        if (player.containerMenu != null) {
            player.containerMenu.broadcastChanges();
        }

        sendTasksToPlayer(player);
        sendTasksToNear();
    }

    private void removeItemsFromInventory(ServerPlayer player, Item item, int count) {
        int remaining = count;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (remaining <= 0) break;

            ItemStack stack = player.getInventory().getItem(i);

            if (stack.isEmpty()) continue;
            if (stack.getItem() != item) continue;

            int toRemove = Math.min(stack.getCount(), remaining);
            stack.shrink(toRemove);
            remaining -= toRemove;

            if (stack.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static String getRequiredItemId(CompoundTag tag) {
        String[] keys = new String[]{
                "ItemId",
                "itemId",
                "item",
                "id",
                "name"
        };

        for (String key : keys) {
            if (tag.contains(key)) {
                String value = tag.getString(key).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }

        return "";
    }

    private static int getRequiredItemCount(CompoundTag tag) {
        if (tag.contains("count")) {
            int c = tag.getInt("count");
            if (c > 0) return c;
        }

        if (tag.contains("Count")) {
            int c = tag.getInt("Count");
            if (c > 0) return c;
        }

        return 1;
    }
    public boolean placeScrollOnBoard(ItemStack scroll, Player player) {
        if (level == null || level.isClientSide) return false;
        if (scroll.isEmpty() || !scroll.hasTag()) return false;

        CompoundTag scrollTag = scroll.getTag();
        if (scrollTag == null) return false;

        // Сначала возвращаем карточку на её исходное место.
        int targetSlot = -1;
        int originalSlot = scrollTag.contains("QuestSlotIndex", Tag.TAG_INT)
                ? scrollTag.getInt("QuestSlotIndex") : -1;
        if (originalSlot >= 0 && originalSlot < MAX_TASKS && tasks.get(originalSlot).isEmpty()) {
            targetSlot = originalSlot;
        }

        // Если исходное место уже занято, используем любой свободный слот.
        for (int i = 0; targetSlot == -1 && i < MAX_TASKS; i++) {
            if (tasks.get(i).isEmpty() && !taskTaken.get(i)) {
                targetSlot = i;
                break;
            }
        }

        // Если таких нет, ищем среди "взятых" (они тоже пустые)
        if (targetSlot == -1) {
            for (int i = 0; i < MAX_TASKS; i++) {
                if (tasks.get(i).isEmpty()) {
                    targetSlot = i;
                    break;
                }
            }
        }

        // Совсем нет мест
        if (targetSlot == -1) {
            player.sendSystemMessage(Component.literal("§cНа доске нет свободных мест!"));
            return false;
        }

        // Создаём карточку задания из данных свитка
        ItemStack taskStack = createTaskItemFromScroll(scrollTag);
        if (taskStack.isEmpty()) return false;

        tasks.set(targetSlot, taskStack);
        taskTaken.set(targetSlot, false);
        setChanged();

        // Синхронизация
        if (player instanceof ServerPlayer serverPlayer) {
            sendTasksToPlayer(serverPlayer);
        }
        sendTasksToNear();

        player.sendSystemMessage(Component.literal("§aЗадание размещено на доске!"));
        return true;
    }

    /**
     * Создаёт карточку задания (task_item) из NBT свитка.
     */
    private ItemStack createTaskItemFromScroll(CompoundTag scrollTag) {
        ResourceLocation itemId = ResourceLocation.fromNamespaceAndPath("labyrinthmod", "task_item");
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(item);
        CompoundTag tag = scrollTag.copy();
        tag.remove("Completed");
        tag.remove("QuestSlotIndex");

        stack.setTag(tag);
        return stack;
    }
    /**
     * Выполнить задание прямо на доске (без взятия свитка).
     * Вызывается когда игрок нажимает "Выполнить задание" на доске.
     *
     * @param slot   индекс слота задания (0-4)
     * @param player игрок
     * @return true если задание выполнено
     */
    public boolean completeQuestOnBoard(int slot, ServerPlayer player) {
        if (level == null || level.isClientSide) return false;
        if (slot < 0 || slot >= MAX_TASKS) return false;

        ItemStack task = tasks.get(slot);
        if (task.isEmpty() || !task.hasTag()) return false;

        // Здесь должна быть логика проверки инвентаря игрока
        // (аналогично тому, что уже есть в CompleteScrollPacket)
        // Если предметы есть — выполняем

        // Убираем задание с доски
        tasks.set(slot, ItemStack.EMPTY);
        taskTaken.set(slot, true);
        setChanged();

        // ★ УВЕДОМЛЯЕМ ТРЕКЕР ★
        com.labyrinthmod.common.quest.QuestCompletionTracker.markQuestCompleted(slot);

        // Синхронизация
        sendTasksToNear();
        if (player != null) {
            sendTasksToPlayer(player);
        }

        return true;
    }

    @Override public CompoundTag getUpdateTag() { CompoundTag tag = super.getUpdateTag(); saveAdditional(tag); return tag; }
    @Override public void handleUpdateTag(CompoundTag tag) { load(tag); }
    @Nullable @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    // ==========================================
    // ИНТЕРФЕЙСЫ Container И MenuProvider
    // ==========================================
    @Override public Component getDisplayName() { return Component.translatable("block.labyrinthmod.bulletin_board"); }
    @Nullable @Override public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) { return new BulletinBoardMenu(containerId, playerInventory, this); }
    @Override public int getContainerSize() { return MAX_TASKS; }
    @Override public boolean isEmpty() { for (ItemStack stack : tasks) if (!stack.isEmpty()) return false; return true; }
    @Override public ItemStack getItem(int slot) { return getTask(slot); }
    @Override public ItemStack removeItem(int slot, int count) { return ItemStack.EMPTY; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }
    @Override public void setItem(int slot, ItemStack stack) { if (slot >= 0 && slot < MAX_TASKS) { tasks.set(slot, stack); setChanged(); } }
    @Override public boolean stillValid(Player player) { return player.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) <= 64.0; }
    @Override public void clearContent() { tasks.clear(); taskTaken.clear(); for (int i = 0; i < MAX_TASKS; i++) { tasks.add(ItemStack.EMPTY); taskTaken.add(false); } }
}
