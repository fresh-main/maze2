package com.labyrinthmod.common.item;

import com.labyrinthmod.common.patrol.PatrolManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BoundsStickItem extends Item {

    private static final Map<UUID, Boolean> awaitingMin = new HashMap<>();

    public BoundsStickItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!player.hasPermissions(2) && !player.isCreative()) return InteractionResult.PASS;
        if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;

        PatrolManager m = PatrolManager.get(ctx.getLevel());
        if (m == null) return InteractionResult.FAIL;

        BlockPos pos = ctx.getClickedPos();

        if (player.isShiftKeyDown()) {
            m.clearBounds();
            awaitingMin.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("§c[Bounds] Границы сброшены").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }

        boolean isMin = !awaitingMin.getOrDefault(player.getUUID(), false);
        if (isMin) {
            m.setBoundsMin(pos);
            awaitingMin.put(player.getUUID(), true);
            player.sendSystemMessage(Component.literal("§6[Bounds] Min угол: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
            player.sendSystemMessage(Component.literal("§7Кликните 2й угол (Max)"));
        } else {
            m.setBoundsMax(pos);
            awaitingMin.put(player.getUUID(), false);
            player.sendSystemMessage(Component.literal("§a[Bounds] Max угол: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
            BlockPos min = m.getBoundsMin();
            int sx = Math.abs(pos.getX() - min.getX()) + 1;
            int sz = Math.abs(pos.getZ() - min.getZ()) + 1;
            player.sendSystemMessage(Component.literal("§aГраницы: " + sx + "×" + sz + " блоков"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.literal("ПКМ 1й блок — Min угол лабиринта").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("ПКМ 2й блок — Max угол лабиринта").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Shift+ПКМ — сбросить границы").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Автогенерация точек происходит только в этой зоне").withStyle(ChatFormatting.DARK_GRAY));
    }
}
