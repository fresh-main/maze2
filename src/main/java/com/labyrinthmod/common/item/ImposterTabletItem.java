package com.labyrinthmod.common.item;

import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.init.ModBlocks;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.OpenImposterScreenPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ImposterTabletItem extends Item {

    private static final long COOLDOWN_MS = 3600000; // 1 час
    private static final ConcurrentHashMap<UUID, Long> lastAttackTime = new ConcurrentHashMap<>();
    private static UUID currentGlobalTarget = null;
    private static String currentTargetName = null;
    private static long currentAttackEndTime = 0;

    public ImposterTabletItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant());
    }

    // Проверка, есть ли уже планшет у игрока
    public static boolean hasTablet(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof ImposterTabletItem) {
                return true;
            }
        }
        return false;
    }

    // Выдать планшет, только если его нет
    public static void giveTabletIfNotExists(ServerPlayer player) {
        if (!hasTablet(player)) {
            ItemStack tablet = new ItemStack(ModBlocks.IMPOSTER_TABLET.get());
            if (!player.getInventory().add(tablet)) {
                player.drop(tablet, false);
            }
        }
    }

    public static boolean isAttackActive() {
        return currentGlobalTarget != null && System.currentTimeMillis() < currentAttackEndTime;
    }

    public static UUID getCurrentTarget() {
        return currentGlobalTarget;
    }

    public static void setCurrentTarget(UUID target, String targetName) {
        currentGlobalTarget = target;
        currentTargetName = targetName;
        currentAttackEndTime = System.currentTimeMillis() + 30000;
    }

    public static void clearCurrentTarget() {
        currentGlobalTarget = null;
        currentTargetName = null;
        currentAttackEndTime = 0;
    }

    public static void recordAttack(Player player) {
        lastAttackTime.put(player.getUUID(), System.currentTimeMillis());
    }

    public static long getRemainingCooldown(Player player) {
        Long lastTime = lastAttackTime.get(player.getUUID());
        if (lastTime == null) return 0;
        long elapsed = System.currentTimeMillis() - lastTime;
        return Math.max(0, COOLDOWN_MS - elapsed);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }


        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }

        // Проверяем, что игрок - предатель
        boolean isImposter = player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction() == FractionType.IMPOSTER)
                .orElse(false);

        if (!isImposter) {
            serverPlayer.sendSystemMessage(Component.literal("§cТолько предатели могут использовать этот предмет!"));
            return InteractionResultHolder.fail(stack);
        }

        // Проверяем, есть ли уже активная атака
        if (isAttackActive()) {
            long remaining = (currentAttackEndTime - System.currentTimeMillis()) / 1000;
            serverPlayer.sendSystemMessage(Component.literal("§cГриверы уже атакуют " + currentTargetName + "! Осталось " + remaining + " секунд."));
            return InteractionResultHolder.fail(stack);
        }

        // Проверяем кулдаун
        long remainingCooldown = getRemainingCooldown(player);
        if (remainingCooldown > 0) {
            long minutes = remainingCooldown / 60000;
            long seconds = (remainingCooldown % 60000) / 1000;
            serverPlayer.sendSystemMessage(Component.literal("§cДо следующей атаки: " + minutes + "м " + seconds + "с"));
            return InteractionResultHolder.fail(stack);
        }

        // Открываем GUI с выбором цели
        List<OpenImposterScreenPacket.PlayerInfo> onlinePlayers = OpenImposterScreenPacket.getOnlinePlayers(serverPlayer);
        if (onlinePlayers.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.literal("§cНет доступных целей!"));
            return InteractionResultHolder.fail(stack);
        }

        // В методе use, после проверок:
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                new OpenImposterScreenPacket(OpenImposterScreenPacket.getOnlinePlayers(serverPlayer)));

        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.literal("ПКМ - открыть меню выбора жертвы").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Натравить гриверов на игрока").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("§cКулдаун: 1 час").withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("§cТолько один предатель может атаковать!").withStyle(ChatFormatting.RED));
    }
    public static long getAttackEndTime() {
        return currentAttackEndTime;
    }

    public static String getCurrentTargetName() {
        return currentTargetName;
    }
}