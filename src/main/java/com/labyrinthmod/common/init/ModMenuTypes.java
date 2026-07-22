package com.labyrinthmod.common.init;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.menu.BulletinBoardMenu;
import com.labyrinthmod.common.menu.WritableTaskMenu;
import com.labyrinthmod.gui.CraftRestrictionMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, LabyrinthMod.MOD_ID);

    public static final RegistryObject<MenuType<CraftRestrictionMenu>> CRAFT_RESTRICTION_MENU =
            MENUS.register("craft_restriction",
                    () -> IForgeMenuType.create(CraftRestrictionMenu::new));

    public static final RegistryObject<MenuType<BulletinBoardMenu>> BULLETIN_BOARD_MENU =
            MENUS.register("bulletin_board_menu",
                    () -> IForgeMenuType.create(BulletinBoardMenu::new));

    public static final RegistryObject<MenuType<WritableTaskMenu>> WRITABLE_TASK_MENU =
            MENUS.register("writable_task_menu",
                    () -> IForgeMenuType.create(WritableTaskMenu::new));

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}