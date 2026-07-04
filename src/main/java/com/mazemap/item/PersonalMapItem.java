package com.mazemap.item;

import com.mazemap.network.MazeMapNetwork;
import com.mazemap.network.packet.S2CFragmentSyncPacket;
import com.mazemap.network.packet.S2COpenMapPacket;
import com.mazemap.storage.PlayerMapData;
import com.mazemap.util.MapDurabilityHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PersonalMapItem extends Item {
    public static final String TAG_MAP_DATA = "MapData";
    public static final String TAG_MAP_UUID = "MapUUID";

    // Кэш в памяти для каждой карты (по её UUID), чтобы не дергать NBT каждый тик
    private static final Map<UUID, PlayerMapData> CACHE = new ConcurrentHashMap<>();

    public PersonalMapItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            PlayerMapData data = getData(stack);
            Map<Long, PlayerMapData.Fragment> all = data.getAllFragments();

            // 1. СНАЧАЛА отправляем пакет открытия (он очистит кэш клиента)
            MazeMapNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new S2COpenMapPacket());

            // 2. ПОТОМ отправляем все фрагменты этой конкретной карты
            for (Map.Entry<Long, PlayerMapData.Fragment> e : all.entrySet()) {
                long key = e.getKey();
                int cellX = (int) (key >> 32);
                int cellZ = (int) key;
                PlayerMapData.Fragment frag = e.getValue();
                MazeMapNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> sp),
                        new S2CFragmentSyncPacket(cellX, cellZ, frag.pixels, frag.walkable));
            }
        }
        return InteractionResultHolder.success(stack);
    }

    // Получаем уникальный UUID карты (создается один раз при первом использовании)
    public static UUID getMapUUID(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.hasUUID(TAG_MAP_UUID)) {
            tag.putUUID(TAG_MAP_UUID, UUID.randomUUID());
        }
        return tag.getUUID(TAG_MAP_UUID);
    }

    public static PlayerMapData getData(ItemStack stack) {
        if (stack.isEmpty()) return new PlayerMapData();
        UUID uid = getMapUUID(stack);
        return CACHE.computeIfAbsent(uid, k -> {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains(TAG_MAP_DATA)) {
                return PlayerMapData.fromNbt(tag.getCompound(TAG_MAP_DATA));
            }
            return new PlayerMapData();
        });
    }

    public static void setData(ItemStack stack, PlayerMapData data) {
        if (stack.isEmpty()) return;

        // 🧊 ЗАМОРОЗКА: Если карта сломана, не перезаписываем NBT.
        // При сохранении мира/выходе из игры загрузится ровно то состояние,
        // которое было в момент падения прочности до 0.
        if (com.mazemap.util.MapDurabilityHandler.isBroken(stack)) return;

        UUID uid = getMapUUID(stack);
        CACHE.put(uid, data);
        CompoundTag tag = stack.getOrCreateTag();
        tag.put(TAG_MAP_DATA, data.toNbt());
    }

    public static void clearData(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) return;
        UUID uid = getMapUUID(stack);
        CACHE.remove(uid);
        stack.removeTagKey(TAG_MAP_DATA);
        if (stack.getTag() != null && stack.getTag().isEmpty()) {
            stack.setTag(null);
        }
    }

    public static ItemStack findInInventory(Player player) {
        if (player.getMainHandItem().is(com.mazemap.registry.ModItems.PERSONAL_MAP.get())) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().is(com.mazemap.registry.ModItems.PERSONAL_MAP.get())) {
            return player.getOffhandItem();
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(com.mazemap.registry.ModItems.PERSONAL_MAP.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        MapDurabilityHandler.appendTooltip(stack, tooltip, flag);
    }
    // ===== NBT для сохранения метки =====
    private static final String TAG_HAS_MARKER = "HasMarker";
    private static final String TAG_MARKER_X = "MarkerX";
    private static final String TAG_MARKER_Z = "MarkerZ";

    public static boolean hasMarker(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean(TAG_HAS_MARKER);
    }

    public static int getMarkerX(ItemStack stack) {
        return stack.getOrCreateTag().getInt(TAG_MARKER_X);
    }

    public static int getMarkerZ(ItemStack stack) {
        return stack.getOrCreateTag().getInt(TAG_MARKER_Z);
    }

    public static void setMarker(ItemStack stack, int x, int z) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG_HAS_MARKER, true);
        tag.putInt(TAG_MARKER_X, x);
        tag.putInt(TAG_MARKER_Z, z);
    }

    public static void clearMarker(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.remove(TAG_HAS_MARKER);
        tag.remove(TAG_MARKER_X);
        tag.remove(TAG_MARKER_Z);
    }
// =====================================
}