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

import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class ZoneStickItem extends Item {

    private static final Map<UUID, BlockPos> pendingFirstCorner = new HashMap<>();

    public ZoneStickItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
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
            // Shift+ПКМ: удалить зону в этом месте
            m.removeExclusionZoneAt(pos);
            pendingFirstCorner.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("§c[Zone] Зона в этой позиции удалена").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }

        // Обычный ПКМ: pos1 → pos2
        BlockPos first = pendingFirstCorner.get(player.getUUID());
        if (first == null) {
            pendingFirstCorner.put(player.getUUID(), pos);
            player.sendSystemMessage(Component.literal("§6[Zone] Угол 1: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
            player.sendSystemMessage(Component.literal("§7Кликните 2й угол для создания зоны"));
        } else {
            m.addExclusionZone(first, pos);
            pendingFirstCorner.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("§a[Zone] Зона создана: "
                    + first.getX() + "," + first.getY() + "," + first.getZ() + " → "
                    + pos.getX() + "," + pos.getY() + "," + pos.getZ()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.literal("ПКМ 2 блока — создать зону исключения").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Shift+ПКМ — удалить зону в позиции").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Внутри зоны точки не генерируются").withStyle(ChatFormatting.DARK_GRAY));
    }
}
