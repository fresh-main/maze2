package com.labyrinthmod.common.item;

import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.OpenAdminMenuPacket;
import com.labyrinthmod.common.patrol.PatrolManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PatrolStickItem extends Item {

    public PatrolStickItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public @NotNull InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!player.hasPermissions(2) && !player.isCreative()) return InteractionResult.PASS;
        if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;

        PatrolManager manager = PatrolManager.get(ctx.getLevel());
        if (manager == null) return InteractionResult.FAIL;

        BlockPos pos = ctx.getClickedPos().above();

        if (player.isShiftKeyDown()) {
            // Shift+ПКМ по блоку = установить spawn point
            manager.setSpawnPoint(pos);
            player.sendSystemMessage(Component.literal("§6[Patrol] Точка спавна: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
            return InteractionResult.SUCCESS;
        }

        // Простой ПКМ по блоку — toggle точки
        if (manager.hasPoint(pos)) {
            manager.removePatrolPoint(pos);
            player.sendSystemMessage(Component.literal("§c[Patrol] Точка удалена: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
        } else {
            manager.addPatrolPoint(pos);
            int total = manager.getPatrolPoints().size();
            player.sendSystemMessage(Component.literal("§a[Patrol] Точка добавлена #" + total + ": " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.hasPermissions(2) && !player.isCreative()) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        // ПКМ в воздух — открыть админ-меню
        if (player instanceof ServerPlayer sp) {
            PatrolManager manager = PatrolManager.get(level);
            if (manager == null) return InteractionResultHolder.fail(stack);

            OpenAdminMenuPacket pkt = OpenAdminMenuPacket.fromServer(level, manager);
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), pkt);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.literal("ПКМ по блоку — добавить/убрать точку").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Shift+ПКМ по блоку — задать spawn").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("ПКМ в воздух — админ-меню").withStyle(ChatFormatting.GRAY));
    }
}
