package com.labyrinthmod.common.blockentity;

import com.labyrinthmod.common.init.ModBlockEntities;
import com.labyrinthmod.common.item.TaskItem;
import com.labyrinthmod.common.menu.BulletinBoardMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BulletinBoardBlockEntity extends BlockEntity implements Container, net.minecraft.world.MenuProvider {
    private static final int MAX_TASKS = 5; // 5 карточек
    private final List<ItemStack> tasks = new ArrayList<>(MAX_TASKS);

    private int spawnIntervalSeconds = 30;
    private final List<CompoundTag> preloadedTasks = new ArrayList<>();
    private int spawnTimer = 0;
    private int preloadedIndex = 0;

    public BulletinBoardBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.BULLETIN_BOARD_BE.get(), pos, blockState);
        for (int i = 0; i < MAX_TASKS; i++) {
            tasks.add(ItemStack.EMPTY);
        }

        // ===== ДЕФОЛТНЫЕ ЗАДАНИЯ =====
        addDefaultTask("Найти артефакт", "Найди древний артефакт в глубинах лабиринта", "500 монет", "Админ");
        addDefaultTask("Убить гривера", "Уничтожь гривера в северной части лабиринта", "1000 монет", "Админ");
        addDefaultTask("Собрать ресурсы", "Собери 10 единиц редкого ресурса", "300 монет", "Админ");
        addDefaultTask("Исследовать зону", "Исследуй неизведанную зону лабиринта", "200 монет", "Админ");
        addDefaultTask("Доставить послание", "Доставь послание другому игроку", "150 монет", "Админ");
        // =============================
    }

    private void addDefaultTask(String title, String description, String reward, String author) {
        CompoundTag taskData = new CompoundTag();
        CompoundTag tag = new CompoundTag();
        tag.putString("Title", title);
        tag.putString("Description", description);
        tag.putString("Reward", reward);
        tag.putString("Author", author);
        taskData.put("tag", tag);
        preloadedTasks.add(taskData);
        System.out.println("[BulletinBoard] Added default task: " + title);
    }

    public int getSpawnIntervalSeconds() {
        return spawnIntervalSeconds;
    }

    public void setSpawnIntervalSeconds(int seconds) {
        this.spawnIntervalSeconds = Math.max(1, seconds);
        setChanged();
    }

    public List<CompoundTag> getPreloadedTasks() {
        return preloadedTasks;
    }

    public int getPreloadedTasksCount() {
        return preloadedTasks.size();
    }

    public int getSpawnTimer() {
        return spawnTimer;
    }

    public int getTicksPerSpawn() {
        return spawnIntervalSeconds * 20;
    }

    public void addPreloadedTask(CompoundTag taskData) {
        preloadedTasks.add(taskData.copy());
        setChanged();
        System.out.println("[BulletinBoard] Added preloaded task #" + preloadedTasks.size() +
                ": " + taskData.getCompound("tag").getString("Title"));
    }

    public void removePreloadedTask(int index) {
        if (index >= 0 && index < preloadedTasks.size()) {
            preloadedTasks.remove(index);
            setChanged();
        }
    }

    // Создаём ItemStack задания из данных
    private ItemStack createTaskItemStack(CompoundTag taskData) {
        CompoundTag tag = taskData.getCompound("tag");
        String title = tag.getString("Title");
        String description = tag.getString("Description");
        String reward = tag.getString("Reward");
        String author = tag.getString("Author");

        // Получаем предмет через реестр Forge
        ResourceLocation itemId = ResourceLocation.fromNamespaceAndPath("labyrinthmod", "task_item");
        Item item = ForgeRegistries.ITEMS.getValue(itemId);

        if (item == null) {
            System.out.println("[BulletinBoard] ERROR: Task item not found in registry!");
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item);
        CompoundTag itemTag = stack.getOrCreateTag();
        itemTag.putString("Title", title);
        itemTag.putString("Description", description);
        itemTag.putString("Reward", reward);
        itemTag.putString("Author", author);
        stack.setTag(itemTag);

        return stack;
    }

    // Спавн следующего задания по очереди
    public void spawnTask() {
        if (preloadedTasks.isEmpty()) {
            System.out.println("[BulletinBoard] No preloaded tasks to spawn!");
            return;
        }

        // Найти первый пустой слот
        int emptySlot = -1;
        for (int i = 0; i < MAX_TASKS; i++) {
            if (tasks.get(i).isEmpty()) {
                emptySlot = i;
                break;
            }
        }

        if (emptySlot == -1) {
            System.out.println("[BulletinBoard] All slots full! Waiting for player to take tasks.");
            return;
        }

        // Берём следующее задание по индексу (по кругу)
        CompoundTag taskData = preloadedTasks.get(preloadedIndex % preloadedTasks.size());
        preloadedIndex++;

        ItemStack taskStack = createTaskItemStack(taskData);

        if (!taskStack.isEmpty()) {
            tasks.set(emptySlot, taskStack);
            setChanged();
            System.out.println("[BulletinBoard] Spawned task at slot " + emptySlot +
                    " (preloaded #" + ((preloadedIndex - 1) % preloadedTasks.size()) +
                    "): " + taskData.getCompound("tag").getString("Title"));
        }
    }

    // Тик вызывается каждый тик на сервере
    public void tick() {
        spawnTimer++;
        int ticksPerSpawn = spawnIntervalSeconds * 20;

        if (spawnTimer >= ticksPerSpawn) {
            spawnTimer = 0;
            System.out.println("[BulletinBoard] Timer expired! Spawning next task...");
            spawnTask();
        }
    }

    public boolean addTask(ItemStack taskStack, Player player) {
        if (!(taskStack.getItem() instanceof TaskItem)) return false;
        for (int i = 0; i < MAX_TASKS; i++) {
            if (tasks.get(i).isEmpty()) {
                tasks.set(i, taskStack.split(1));
                setChanged();
                return true;
            }
        }
        return false;
    }

    public ItemStack takeTask(int slot, Player player) {
        if (slot < 0 || slot >= MAX_TASKS) return ItemStack.EMPTY;
        ItemStack task = tasks.get(slot);
        if (!task.isEmpty()) {
            tasks.set(slot, ItemStack.EMPTY);
            setChanged();
            return task;
        }
        return ItemStack.EMPTY;
    }

    public ItemStack getTask(int slot) {
        if (slot < 0 || slot >= MAX_TASKS) return ItemStack.EMPTY;
        return tasks.get(slot);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tasks.clear();
        ListTag tasksTag = tag.getList("Tasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < MAX_TASKS; i++) {
            tasks.add(i < tasksTag.size() ? ItemStack.of(tasksTag.getCompound(i)) : ItemStack.EMPTY);
        }

        spawnIntervalSeconds = tag.getInt("SpawnInterval");
        if (spawnIntervalSeconds <= 0) spawnIntervalSeconds = 30;

        preloadedIndex = tag.getInt("PreloadedIndex");

        ListTag preloadedTag = tag.getList("PreloadedTasks", Tag.TAG_COMPOUND);
        preloadedTasks.clear();
        for (int i = 0; i < preloadedTag.size(); i++) {
            preloadedTasks.add(preloadedTag.getCompound(i).copy());
        }

        // Если нет сохранённых заданий - добавляем дефолтные
        if (preloadedTasks.isEmpty()) {
            addDefaultTask("Найти артефакт", "Найди древний артефакт в глубинах лабиринта", "500 монет", "Админ");
            addDefaultTask("Убить гривера", "Уничтожь гривера в северной части лабиринта", "1000 монет", "Админ");
            addDefaultTask("Собрать ресурсы", "Собери 10 единиц редкого ресурса", "300 монет", "Админ");
            addDefaultTask("Исследовать зону", "Исследуй неизведанную зону лабиринта", "200 монет", "Админ");
            addDefaultTask("Доставить послание", "Доставь послание другому игроку", "150 монет", "Админ");
        }

        System.out.println("[BulletinBoard] Loaded: interval=" + spawnIntervalSeconds +
                ", preloaded=" + preloadedTasks.size() +
                ", nextIndex=" + preloadedIndex);
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag tasksTag = new ListTag();
        for (ItemStack task : tasks) tasksTag.add(task.save(new CompoundTag()));
        tag.put("Tasks", tasksTag);

        tag.putInt("SpawnInterval", spawnIntervalSeconds);
        tag.putInt("PreloadedIndex", preloadedIndex);

        ListTag preloadedTag = new ListTag();
        for (CompoundTag taskData : preloadedTasks) {
            preloadedTag.add(taskData);
        }
        tag.put("PreloadedTasks", preloadedTag);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.labyrinthmod.bulletin_board");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BulletinBoardMenu(containerId, playerInventory, this);
    }

    @Override
    public int getContainerSize() { return MAX_TASKS; }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : tasks) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) { return getTask(slot); }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack stack = getTask(slot);
        if (!stack.isEmpty()) {
            if (stack.getCount() <= count) {
                tasks.set(slot, ItemStack.EMPTY);
                setChanged();
                return stack;
            } else {
                ItemStack split = stack.split(count);
                setChanged();
                return split;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getTask(slot);
        tasks.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < MAX_TASKS) {
            tasks.set(slot, stack);
            setChanged();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        tasks.clear();
        for (int i = 0; i < MAX_TASKS; i++) tasks.add(ItemStack.EMPTY);
    }
}