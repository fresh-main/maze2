package com.otbor.client;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class CuriosVisuals {

    private static final String[] CATEGORY = {
            "head", "necklace", "back", "belt", "hands", "ring", "ring", "charm"
    };
    private static final int[] LOCAL_INDEX = { 0, 0, 0, 0, 0, 0, 1, 0 };

    public record Binding(IItemHandler handler, int localIndex) {}

    private static boolean attempted = false;
    private static boolean available = false;
    private static MethodHandle getCuriosInventory;
    private static MethodHandle getCurios;
    private static MethodHandle getStacks;

    private CuriosVisuals() {}

    private static void tryInit() {
        if (attempted) return;
        attempted = true;
        try {
            MethodHandles.Lookup lk = MethodHandles.publicLookup();
            Class<?> apiCls = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            getCuriosInventory = lk.findStatic(apiCls, "getCuriosInventory",
                    MethodType.methodType(LazyOptional.class,
                            Class.forName("net.minecraft.world.entity.LivingEntity")));

            Class<?> handlerCls = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            getCurios = lk.findVirtual(handlerCls, "getCurios", MethodType.methodType(java.util.Map.class));

            Class<?> stacksCls = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            getStacks = lk.findVirtual(stacksCls, "getStacks",
                    MethodType.methodType(Class.forName("top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler")));

            available = true;
            org.slf4j.LoggerFactory.getLogger("otbor").info("[CuriosVisuals] Curios API bridge ready");
        } catch (Throwable t) {
            available = false;
            org.slf4j.LoggerFactory.getLogger("otbor").warn("[CuriosVisuals] Curios API bridge init failed: {}", t.toString());
        }
    }

    public static Binding getBinding(Player player, int visualIndex) {
        if (player == null) return null;
        if (visualIndex < 0 || visualIndex >= CATEGORY.length) return null;
        tryInit();
        if (!available) return null;
        try {
            LazyOptional<?> lazy = (LazyOptional<?>) getCuriosInventory.invoke(player);
            Object handler0 = lazy.orElse(null);
            if (handler0 == null) {
                if (visualIndex == 0) org.slf4j.LoggerFactory.getLogger("otbor")
                        .info("[CuriosVisuals] getCuriosInventory empty for {}", player);
                return null;
            }
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> curios = (java.util.Map<String, Object>) getCurios.invoke(handler0);
            if (visualIndex == 0) org.slf4j.LoggerFactory.getLogger("otbor")
                    .info("[CuriosVisuals] curios map keys = {}", curios.keySet());
            Object stacksHandler = curios.get(CATEGORY[visualIndex]);
            if (stacksHandler == null) return null;
            Object handler = getStacks.invoke(stacksHandler);
            if (!(handler instanceof IItemHandler h)) return null;
            int localIndex = LOCAL_INDEX[visualIndex];
            if (localIndex >= h.getSlots()) return null;
            return new Binding(h, localIndex);
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("otbor")
                    .warn("[CuriosVisuals] getBinding({}) failed: {}", visualIndex, t.toString());
            return null;
        }
    }

    public static ItemStack getSlotItem(Player player, int visualIndex) {
        Binding b = getBinding(player, visualIndex);
        if (b == null) return null;
        try {
            return b.handler().getStackInSlot(b.localIndex());
        } catch (Throwable t) {
            return null;
        }
    }
}
