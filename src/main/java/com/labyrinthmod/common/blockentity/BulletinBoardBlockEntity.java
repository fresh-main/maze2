package com.labyrinthmod.common.blockentity;

import com.labyrinthmod.common.init.ModBlockEntities;
import com.labyrinthmod.common.menu.BulletinBoardMenu;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.SyncTasksPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.Tag;
import com.labyrinthmod.common.network.packet.SyncBoardDataPacket;
import com.labyrinthmod.common.network.packet.SyncTasksPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
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

    private int spawnIntervalSeconds = 30;
    private final List<CompoundTag> preloadedTasks = new ArrayList<>();
    private int spawnTimer = 0;
    private int preloadedIndex = 0;

    // Статический список всех досок для тика на сервере
    public static final List<BulletinBoardBlockEntity> ALL_BOARDS = new CopyOnWriteArrayList<>();

    public BulletinBoardBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.BULLETIN_BOARD_BE.get(), pos, blockState);
        for (int i = 0; i < MAX_TASKS; i++) {
            tasks.add(ItemStack.EMPTY);
        }
        ALL_BOARDS.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ALL_BOARDS.remove(this);
    }

    public int getSpawnIntervalSeconds() {
        return spawnIntervalSeconds;
    }

    public void setSpawnIntervalSeconds(int seconds) {
        this.spawnIntervalSeconds = Math.max(1, seconds);
        setChanged();
        System.out.println("[BB] Interval set to " + this.spawnIntervalSeconds + " seconds");

        // Отправляем синхронизацию клиенту
        if (level != null && !level.isClientSide) {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.NEAR.with(
                            () -> new PacketDistributor.TargetPoint(
                                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                                    64, level.dimension()
                            )
                    ),
                    new com.labyrinthmod.common.network.packet.SyncBoardDataPacket(
                            worldPosition, spawnIntervalSeconds, spawnTimer, preloadedTasks
                    )
            );
        }
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
        System.out.println("[BB] Added preloaded task: " + taskData.getCompound("tag").getString("Title"));

        // Отправляем синхронизацию клиенту
        if (level != null && !level.isClientSide) {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.NEAR.with(
                            () -> new PacketDistributor.TargetPoint(
                                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                                    64, level.dimension()
                            )
                    ),
                    new com.labyrinthmod.common.network.packet.SyncBoardDataPacket(
                            worldPosition, spawnIntervalSeconds, spawnTimer, preloadedTasks
                    )
            );
        }
    }

    public void removePreloadedTask(int index) {
        if (index >= 0 && index < preloadedTasks.size()) {
            preloadedTasks.remove(index);
            setChanged();
            System.out.println("[BB] Removed preloaded task at index: " + index);

            // Отправляем синхронизацию клиенту
            if (level != null && !level.isClientSide) {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.NEAR.with(
                                () -> new PacketDistributor.TargetPoint(
                                        worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                                        64, level.dimension()
                                )
                        ),
                        new com.labyrinthmod.common.network.packet.SyncBoardDataPacket(
                                worldPosition, spawnIntervalSeconds, spawnTimer, preloadedTasks
                        )
                );
            }
        }
    }

    // Метод для сброса таймера
    public void resetTimer() {
        this.spawnTimer = 0;
        setChanged();
        System.out.println("[BB] Timer reset!");

        // Отправляем синхронизацию клиенту
        if (level != null && !level.isClientSide) {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.NEAR.with(
                            () -> new PacketDistributor.TargetPoint(
                                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                                    64, level.dimension()
                            )
                    ),
                    new SyncBoardDataPacket(worldPosition, spawnIntervalSeconds, spawnTimer, preloadedTasks)
            );
        }
    }

    private ItemStack createTaskItemStack(CompoundTag taskData) {
        CompoundTag tag = taskData.getCompound("tag");
        String title = tag.getString("Title");
        String description = tag.getString("Description");
        String reward = tag.getString("Reward");
        String author = tag.getString("Author");

        ResourceLocation itemId = ResourceLocation.fromNamespaceAndPath("labyrinthmod", "task_item");
        Item item = ForgeRegistries.ITEMS.getValue(itemId);

        if (item == null) {
            System.out.println("[BB] ERROR: task_item not found in registry!");
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

    // Отправить синхронизацию конкретному игроку
    public void sendSyncToPlayer(ServerPlayer player) {
        if (player != null) {
            NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new SyncBoardDataPacket(worldPosition, spawnIntervalSeconds, spawnTimer, preloadedTasks)
            );
        }
    }

    public void spawnTask() {
        if (preloadedTasks.isEmpty()) {
            return;
        }

        // Берём ПЕРВОЕ задание из очереди
        CompoundTag taskData = preloadedTasks.get(0);
        ItemStack taskStack = createTaskItemStack(taskData);

        if (taskStack.isEmpty()) {
            return;
        }

        // Ищем пустой слот
        int emptySlot = -1;
        for (int i = 0; i < MAX_TASKS; i++) {
            if (tasks.get(i).isEmpty()) {
                emptySlot = i;
                break;
            }
        }

        if (emptySlot != -1) {
            // Есть место: занимаем свободный слот
            tasks.set(emptySlot, taskStack);
        } else {
            // Места нет: заменяем самое старое задание (слот 0)
            tasks.set(0, taskStack);
            System.out.println("[BB] All slots full! Replacing slot 0");
        }

        // Удаляем задание из очереди ПОСЛЕ того, как оно успешно размещено на доске
        preloadedTasks.remove(0);
        preloadedIndex++;
        setChanged();

        // Отправляем пакет синхронизации всем игрокам рядом
        if (level != null && !level.isClientSide) {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.NEAR.with(
                            () -> new PacketDistributor.TargetPoint(
                                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                                    64, level.dimension()
                            )
                    ),
                    new SyncTasksPacket(worldPosition, tasks)
            );

            // Сообщение в консоль
            String title = taskData.getCompound("tag").getString("Title");
            System.out.println("[BB] Задание появилось: " + title);

            // Сообщение в чат всем игрокам рядом
            level.players().forEach(player -> {
                if (player.distanceToSqr(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()) <= 64) {
                    player.sendSystemMessage(Component.literal("§6[Доска] Новое задание: §f" + title));
                }
            });
        }
    }

    public void tick() {
        spawnTimer++;
        int ticksPerSpawn = spawnIntervalSeconds * 20;

        // Выводим только когда таймер истекает или раз в 100 тиков
        if (spawnTimer % 100 == 0 || spawnTimer >= ticksPerSpawn) {
            System.out.println("[BB tick] spawnTimer=" + spawnTimer + ", ticksPerSpawn=" + ticksPerSpawn + ", interval=" + spawnIntervalSeconds);
        }

        if (spawnTimer >= ticksPerSpawn) {
            spawnTimer = 0;
            System.out.println("[BB] Timer expired! Spawning...");
            spawnTask();
        }
    }

    public ItemStack takeTask(int slot, Player player) {
        if (slot < 0 || slot >= MAX_TASKS) return ItemStack.EMPTY;
        ItemStack task = tasks.get(slot);
        if (!task.isEmpty()) {
            tasks.set(slot, ItemStack.EMPTY);
            setChanged();

            // Отправляем пакет синхронизации всем игрокам рядом
            if (level != null && !level.isClientSide) {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.NEAR.with(
                                () -> new PacketDistributor.TargetPoint(
                                        worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                                        64, level.dimension()
                                )
                        ),
                        new SyncTasksPacket(worldPosition, tasks)
                );
            }
            return task;
        }
        return ItemStack.EMPTY;
    }

    public ItemStack getTask(int slot) {
        if (slot < 0 || slot >= MAX_TASKS) return ItemStack.EMPTY;
        return tasks.get(slot);
    }

    // Метод для синхронизации заданий с сервера
    public void syncTasksFromServer(List<ItemStack> newTasks) {
        tasks.clear();
        for (int i = 0; i < MAX_TASKS; i++) {
            tasks.add(i < newTasks.size() ? newTasks.get(i) : ItemStack.EMPTY);
        }
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
        // Дефолтные задания НЕ добавляются
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag tasksTag = new ListTag();
        for (ItemStack task : tasks) {
            tasksTag.add(task.save(new CompoundTag()));
        }
        tag.put("Tasks", tasksTag);

        tag.putInt("SpawnInterval", spawnIntervalSeconds);
        tag.putInt("PreloadedIndex", preloadedIndex);

        ListTag preloadedTag = new ListTag();
        for (CompoundTag taskData : preloadedTasks) {
            preloadedTag.add(taskData);
        }
        tag.put("PreloadedTasks", preloadedTag);
    }

    // Синхронизация данных с сервера (для клиента)
    public void syncDataFromServer(int interval, int timer, List<CompoundTag> tasks) {
        this.spawnIntervalSeconds = interval;
        this.spawnTimer = timer;
        this.preloadedTasks.clear();
        for (CompoundTag tag : tasks) {
            this.preloadedTasks.add(tag.copy());
        }
    }

    // Метод для мгновенного спавна конкретного задания по индексу
    public void spawnSpecificTask(int index) {
        if (index < 0 || index >= preloadedTasks.size()) {
            return;
        }

        CompoundTag taskData = preloadedTasks.get(index);
        ItemStack taskStack = createTaskItemStack(taskData);

        if (taskStack.isEmpty()) {
            return;
        }

        int emptySlot = -1;
        for (int i = 0; i < MAX_TASKS; i++) {
            if (tasks.get(i).isEmpty()) {
                emptySlot = i;
                break;
            }
        }

        if (emptySlot != -1) {
            tasks.set(emptySlot, taskStack);
        } else {
            tasks.set(0, taskStack);
        }

        // Удаляем задание из очереди
        preloadedTasks.remove(index);
        preloadedIndex++;
        setChanged();

        // Отправляем пакет синхронизации
        if (level != null && !level.isClientSide) {
            // Синхронизируем задания на доске
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.NEAR.with(
                            () -> new PacketDistributor.TargetPoint(
                                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                                    64, level.dimension()
                            )
                    ),
                    new SyncTasksPacket(worldPosition, tasks)
            );

            // Синхронизируем очередь заданий (чтобы задание исчезло из списка в админке)
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.NEAR.with(
                            () -> new PacketDistributor.TargetPoint(
                                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                                    64, level.dimension()
                            )
                    ),
                    new SyncBoardDataPacket(worldPosition, spawnIntervalSeconds, spawnTimer, preloadedTasks)
            );

            String title = taskData.getCompound("tag").getString("Title");
            System.out.println("[BB] Задание заспавнено вручную: " + title);

            level.players().forEach(player -> {
                if (player.distanceToSqr(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()) <= 64) {
                    player.sendSystemMessage(Component.literal("§6[Доска] Новое задание: §f" + title));
                }
            });
        }
    }

    // Синхронизация с клиентом
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
        for (int i = 0; i < MAX_TASKS; i++) {
            tasks.add(ItemStack.EMPTY);
        }
    }
}