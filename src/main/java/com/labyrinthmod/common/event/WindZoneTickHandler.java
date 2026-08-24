package com.labyrinthmod.common.event;

import com.labyrinthmod.common.zone.WindZoneManager;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class WindZoneTickHandler {

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;

        tickCounter++;
        // Проверяем каждые 4 тика (5 раз в секунду) - достаточно для эффектов пыли
        if (tickCounter % 4 != 0) return;
        if (tickCounter > 100) tickCounter = 0; // Сброс для предотвращения переполнения

        WindZoneManager manager = WindZoneManager.get(level);
        if (manager != null && manager.isEnabled()) {
            manager.tick(level);
        }
    }

    /**
     * Увеличивает лимит размеров структурного блока с 48 до нужного значения.
     */
    @Mod.EventBusSubscriber(modid = "labyrinthmod", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class StructureSizeLimiter {

        // ★ НОВЫЙ ЛИМИТ (можно поставить хоть 256) ★
        public static final int NEW_MAX_SIZE = 256;

        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(StructureSizeLimiter::applyNewLimits);
        }

        private static void applyNewLimits() {
            try {
                // 1. StructureBlockEntity.MAX_SIZE_PER_AXIS (int)
                setStaticFinalInt(
                        "net.minecraft.world.level.block.entity.StructureBlockEntity",
                        "MAX_SIZE_PER_AXIS",
                        NEW_MAX_SIZE
                );
                System.out.println("[StructureSizeLimiter] StructureBlockEntity.MAX_SIZE_PER_AXIS = " + NEW_MAX_SIZE);

                // 2. StructureTemplate.MAX_SIZE (Vec3i)
                setStaticFinalField(
                        "net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate",
                        "MAX_SIZE",
                        new Vec3i(NEW_MAX_SIZE, NEW_MAX_SIZE, NEW_MAX_SIZE)
                );
                System.out.println("[StructureSizeLimiter] StructureTemplate.MAX_SIZE = " + NEW_MAX_SIZE + "x" + NEW_MAX_SIZE + "x" + NEW_MAX_SIZE);

            } catch (Exception e) {
                System.err.println("[StructureSizeLimiter] Failed to override structure size limits!");
                e.printStackTrace();
            }
        }

        // ===== УТИЛИТЫ ДЛЯ СНЯТИЯ FINAL =====

        private static void setStaticFinalInt(String className, String fieldName, int value) throws Exception {
            Class<?> clazz = Class.forName(className);
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);

            // Снимаем модификатор final
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

            field.setInt(null, value);
        }

        private static void setStaticFinalField(String className, String fieldName, Object value) throws Exception {
            Class<?> clazz = Class.forName(className);
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

            field.set(null, value);
        }
    }
}