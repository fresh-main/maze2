package com.labyrinthmod.common.init;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LabyrinthMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> LABYRINTH_TAB = CREATIVE_TABS.register("labyrinth_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.labyrinthmod"))
                    .icon(() -> new ItemStack(ModBlocks.GRIVER_SPAWNER.get()))
                    .displayItems((parameters, output) -> {
                        // Добавляем все предметы мода в вкладку

                        // Блоки
                        output.accept(ModBlocks.GRIVER_SPAWNER.get());

                        // Предметы (не блоки)
                        output.accept(ModBlocks.PATROL_STICK.get());
                        output.accept(ModBlocks.ZONE_STICK.get());
                        output.accept(ModBlocks.BOUNDS_STICK.get());
                        output.accept(ModBlocks.IMPOSTER_TABLET.get());
                        output.accept(ModBlocks.GRIVER_SPAWNER_ITEM.get());
                    })
                    .build()
    );

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
        LabyrinthMod.LOGGER.info("Creative tabs registered!");
    }
}