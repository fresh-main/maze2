package com.labyrinthmod.common.blockentity;

import com.labyrinthmod.common.init.ModBlockEntities;
import com.labyrinthmod.common.item.TaskScrollItem;
import com.labyrinthmod.common.menu.BulletinBoardMenu;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.SyncBoardDataPacket;
import com.labyrinthmod.common.network.packet.SyncTasksPacket;
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

    private int spawnIntervalSeconds = 30;
    private final List<CompoundTag> preloadedTasks = new ArrayList<>();
    private int spawnTimer = 0;
    private int preloadedIndex = 0;

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

    public void removePreloadedTask(int index) {
        if (index >= 0 && index < preloadedTasks.size()) {
            preloadedTasks.remove(index);
            setChanged();
            if (level != null && !level.isClientSide) syncData();
        }
    }

    private void syncData() {
        NetworkHandler.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64, level.dimension()
                )),
                new SyncBoardDataPacket(worldPosition, spawnIntervalSeconds, spawnTimer, preloadedTasks)
        );
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
            return;
        }

        int targetSlot = -1;
        for (int i = 0; i < MAX_TASKS; i++) {
            if (tasks.get(i).isEmpty()) {
                targetSlot = i;
                break;
            }
        }

        if (targetSlot == -1) {
            for (int i = 0; i < MAX_TASKS; i++) {
                if (taskTaken.get(i)) {
                    targetSlot = i;
                    break;
                }
            }
        }

        if (targetSlot == -1) {
            return;
        }

        ItemStack taskStack = createTaskItemStack(preloadedTasks.get(0));
        if (taskStack.isEmpty()) {
            preloadedTasks.remove(0);
            return;
        }

        tasks.set(targetSlot, taskStack);
        taskTaken.set(targetSlot, false);
        preloadedTasks.remove(0);
        preloadedIndex++;
        setChanged();

        if (level != null && !level.isClientSide) {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                            worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                            64, level.dimension()
                    )),
                    new SyncTasksPacket(worldPosition, tasks)
            );

            // ИСПРАВЛЕНИЕ: делаем переменную final с помощью тернарного оператора
            final String taskTitle = (taskStack.hasTag() && taskStack.getTag().contains("Title"))
                    ? taskStack.getTag().getString("Title")
                    : "Новое задание";

            level.players().forEach(p -> {
                if (p.distanceToSqr(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()) <= 64) {
                    p.sendSystemMessage(Component.literal("§6[Доска] §fНовое задание: §a" + taskTitle));
                }
            });
        }
    }

    // Метод для спавна ВСЕХ заданий из очереди сразу
    public void spawnAllTasks() {
        int spawned = 0;
        while (!preloadedTasks.isEmpty() && spawned < MAX_TASKS) {
            // Проверяем есть ли свободный слот
            boolean hasFreeSlot = false;
            for (int i = 0; i < MAX_TASKS; i++) {
                if (tasks.get(i).isEmpty() || taskTaken.get(i)) {
                    hasFreeSlot = true;
                    break;
                }
            }

            if (!hasFreeSlot) {
                break; // Все слоты заняты активными заданиями
            }

            spawnTask();
            spawned++;
        }
    }

    public void spawnSpecificTask(int index) {
        if (index < 0 || index >= preloadedTasks.size()) return;
        ItemStack taskStack = createTaskItemStack(preloadedTasks.get(index));
        if (taskStack.isEmpty()) return;

        int targetSlot = -1;
        for (int i = 0; i < MAX_TASKS; i++) {
            if (tasks.get(i).isEmpty() || taskTaken.get(i)) {
                targetSlot = i;
                break;
            }
        }
        if (targetSlot == -1) return;

        tasks.set(targetSlot, taskStack);
        taskTaken.set(targetSlot, false);
        preloadedTasks.remove(index);
        preloadedIndex++;
        setChanged();

        if (level != null && !level.isClientSide) {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                            worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64, level.dimension()
                    )),
                    new SyncTasksPacket(worldPosition, tasks)
            );
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                            worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64, level.dimension()
                    )),
                    new SyncBoardDataPacket(worldPosition, spawnIntervalSeconds, spawnTimer, preloadedTasks)
            );
        }
    }

    public void takeTaskAsScroll(int slot, Player player) {
        if (slot < 0 || slot >= MAX_TASKS) return;
        ItemStack task = tasks.get(slot);
        if (!task.isEmpty() && task.hasTag()) {
            ItemStack scroll = new ItemStack(TaskScrollItem.TASK_SCROLL.get());
            CompoundTag scrollTag = scroll.getOrCreateTag();
            scrollTag.putString("Title", task.getTag().getString("Title"));
            scrollTag.putString("Description", task.getTag().getString("Description"));
            scrollTag.putString("Reward", task.getTag().getString("Reward"));
            scrollTag.putString("Author", task.getTag().getString("Author"));

            if (task.getTag().contains("RequiredItems", Tag.TAG_LIST)) {
                scrollTag.put("RequiredItems", task.getTag().getList("RequiredItems", Tag.TAG_COMPOUND));
            }
            scrollTag.putBoolean("Completed", false);
            scroll.setTag(scrollTag);

            player.getInventory().add(scroll);
            tasks.set(slot, ItemStack.EMPTY);
            taskTaken.set(slot, false);
            setChanged();

            if (level != null && !level.isClientSide) {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64, level.dimension()
                        )),
                        new SyncTasksPacket(worldPosition, tasks)
                );
            }
            player.sendSystemMessage(Component.literal("§aЗадание взято! Свиток добавлен в инвентарь."));
        }
    }

    // ИСПРАВЛЕННЫЙ TICK - вызываем spawnTask() когда таймер истекает
    public void tick() {
        if (level == null || level.isClientSide) return;

        spawnTimer++;
        int ticksPerSpawn = spawnIntervalSeconds * 20;

        if (spawnTimer >= ticksPerSpawn) {
            spawnTimer = 0;
            spawnTask(); // Спавним одно задание
        }
    }

    public ItemStack getTask(int slot) {
        return (slot < 0 || slot >= MAX_TASKS) ? ItemStack.EMPTY : tasks.get(slot);
    }

    public boolean isTaskTaken(int slot) {
        if (slot < 0 || slot >= taskTaken.size()) return false;
        return taskTaken.get(slot);
    }

    public void syncTasksFromServer(List<ItemStack> newTasks) {
        tasks.clear();
        for (int i = 0; i < MAX_TASKS; i++) {
            tasks.add(i < newTasks.size() ? newTasks.get(i) : ItemStack.EMPTY);
        }
    }

    public void syncDataFromServer(int interval, int timer, List<CompoundTag> tasksList) {
        this.spawnIntervalSeconds = interval;
        this.spawnTimer = timer;
        this.preloadedTasks.clear();
        for (CompoundTag tag : tasksList) {
            this.preloadedTasks.add(tag.copy());
        }
    }

    public void sendSyncToPlayer(ServerPlayer player) {
        if (player != null) {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SyncBoardDataPacket(worldPosition, spawnIntervalSeconds, spawnTimer, preloadedTasks)
            );
        }
    }

    public void resetTimer() {
        this.spawnTimer = 0;
        setChanged();
        if (level != null && !level.isClientSide) syncData();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tasks.clear();
        taskTaken.clear();

        ListTag tasksTag = tag.getList("Tasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < MAX_TASKS; i++) {
            tasks.add(i < tasksTag.size() ? ItemStack.of(tasksTag.getCompound(i)) : ItemStack.EMPTY);
        }

        ListTag takenTag = tag.getList("TaskTaken", Tag.TAG_BYTE);
        for (int i = 0; i < MAX_TASKS; i++) {
            taskTaken.add(i < takenTag.size() && ((ByteTag) takenTag.get(i)).getAsByte() != 0);
        }

        spawnIntervalSeconds = tag.getInt("SpawnInterval");
        if (spawnIntervalSeconds <= 0) spawnIntervalSeconds = 30;
        preloadedIndex = tag.getInt("PreloadedIndex");

        ListTag preloadedTag = tag.getList("PreloadedTasks", Tag.TAG_COMPOUND);
        preloadedTasks.clear();
        for (int i = 0; i < preloadedTag.size(); i++) {
            preloadedTasks.add(preloadedTag.getCompound(i).copy());
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag tasksTag = new ListTag();
        for (ItemStack task : tasks) {
            tasksTag.add(task.save(new CompoundTag()));
        }
        tag.put("Tasks", tasksTag);

        ListTag takenTag = new ListTag();
        for (boolean taken : taskTaken) {
            takenTag.add(ByteTag.valueOf(taken));
        }
        tag.put("TaskTaken", takenTag);

        tag.putInt("SpawnInterval", spawnIntervalSeconds);
        tag.putInt("PreloadedIndex", preloadedIndex);

        ListTag preloadedTag = new ListTag();
        for (CompoundTag taskData : preloadedTasks) {
            preloadedTag.add(taskData);
        }
        tag.put("PreloadedTasks", preloadedTag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
    public int getContainerSize() {
        return MAX_TASKS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : tasks) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return getTask(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
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
        taskTaken.clear();
        for (int i = 0; i < MAX_TASKS; i++) {
            tasks.add(ItemStack.EMPTY);
            taskTaken.add(false);
        }
    }
}