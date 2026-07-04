package com.infection.item;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, LabyrinthMod.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LabyrinthMod.MOD_ID);

    public static final RegistryObject<Item> ANTIDOTE_SYRINGE =
            ITEMS.register("antidote_syringe", () ->
                    new AntidoteSyringeItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> PERSONAL_NOTE =
            ITEMS.register("personal_note", () ->
                    new PersonalNoteItem(new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> TAB =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.literal("Infection"))
                    .icon(() -> ANTIDOTE_SYRINGE.get().getDefaultInstance())
                    .displayItems((params, out) -> {
                        out.accept(ANTIDOTE_SYRINGE.get());
                        // PERSONAL_NOTE убран — теперь HUD-карточка на инвентаре, не предмет.
                    })
                    .build());

    private ModItems() {}
}
