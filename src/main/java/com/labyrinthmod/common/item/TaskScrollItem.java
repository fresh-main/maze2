package com.labyrinthmod.common.item;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.client.screen.TaskViewScreen;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.CompleteScrollPacket;
import com.labyrinthmod.common.network.packet.CompleteTaskPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public class TaskScrollItem extends Item {
    public static final DeferredRegister<Item> TASK_SCROLL_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, LabyrinthMod.MOD_ID);
    public static final RegistryObject<Item> TASK_SCROLL = TASK_SCROLL_ITEMS.register("task_scroll", () -> new TaskScrollItem(new Item.Properties().stacksTo(1)));
    public static void register(IEventBus modEventBus) {
        TASK_SCROLL_ITEMS.register(modEventBus);
    }

    public TaskScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        InteractionHand hand = context.getHand();

        // ==========================================================
        // SHIFT + ПКМ ПО ДОСКЕ -> ПОЛОЖИТЬ СВИТОК НА ДОСКУ
        // ==========================================================
        if (player.isSecondaryUseActive()) {
            if (level.getBlockEntity(pos) instanceof com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity board) {
                if (!level.isClientSide) {
                    if (board.placeScrollOnBoard(stack, player)) {
                        stack.shrink(1);

                        if (player.containerMenu != null) {
                            player.containerMenu.broadcastChanges();
                        }
                    }
                }

                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            // Если Shift зажат, но кликнули не по доске — ничего не открываем
            return InteractionResult.PASS;
        }

        // ==========================================================
        // ОБЫЧНЫЙ ПКМ ПО БЛОКУ -> ОТКРЫТЬ ЭКРАН СВИТКА
        // ==========================================================
        if (level.isClientSide) {
            openScrollScreen(player, stack, hand);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // ==========================================================
        // SHIFT ЗАЖАТ -> НЕ ОТКРЫВАЕМ СВИТОК
        // ==========================================================
        if (player.isSecondaryUseActive()) {
            return InteractionResultHolder.pass(stack);
        }

        // ==========================================================
        // ОБЫЧНЫЙ ПКМ В ВОЗДУХЕ -> ОТКРЫТЬ ЭКРАН СВИТКА
        // ==========================================================
        if (level.isClientSide) {
            openScrollScreen(player, stack, hand);
        }

        return InteractionResultHolder.success(stack);
    }

    private void openScrollScreen(Player player, ItemStack stack, InteractionHand hand) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.labyrinthmod.client.screen.TaskViewScreen) {
            return;
        }

        int slot;
        if (hand == InteractionHand.MAIN_HAND) {
            slot = player.getInventory().selected;
        } else {
            slot = 40; // Слот оффхенда
        }

        ItemStack copy = stack.copy();

        net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.labyrinthmod.client.screen.TaskViewScreen(
                        copy,
                        () -> {
                            // Отправляем пакет на сервер для выполнения задания со свитка
                            com.labyrinthmod.common.network.NetworkHandler.CHANNEL.sendToServer(
                                    new com.labyrinthmod.common.network.packet.CompleteScrollPacket(slot)
                            );
                        },
                        slot
                )
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        if (stack.hasTag()) {
            CompoundTag tag = stack.getTag();
            String title = tag.getString("Title");
            boolean completed = tag.getBoolean("Completed");

            if (completed) {
                tooltipComponents.add(Component.literal("§6" + title + " §7(выполнено)"));
            } else {
                tooltipComponents.add(Component.literal("§6" + title));

                // Отображение списка требуемых предметов
                if (tag.contains("RequiredItems", Tag.TAG_LIST)) {
                    ListTag reqItems = tag.getList("RequiredItems", Tag.TAG_COMPOUND);
                    if (!reqItems.isEmpty()) {
                        tooltipComponents.add(Component.literal("§7Требуется:"));
                        for (int i = 0; i < reqItems.size(); i++) {
                            CompoundTag itemTag = reqItems.getCompound(i);
                            tooltipComponents.add(Component.literal("  §8" + itemTag.getInt("Count") + "x " + itemTag.getString("ItemId")));
                        }
                    }
                }
                tooltipComponents.add(Component.literal("§7ПКМ чтобы прочитать"));
            }
        } else {
            tooltipComponents.add(Component.literal("§7Пустой свиток"));
        }
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
    }
    public static int findScrollSlot(net.minecraft.world.entity.player.Player player, ItemStack scroll) {
        if (player == null || scroll == null || scroll.isEmpty()) {
            return -1;
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);

            if (!stack.isEmpty()
                    && stack.getItem() instanceof TaskScrollItem
                    && ItemStack.matches(stack, scroll)) {
                return i;
            }
        }

        return -1;
    }
}