package com.infection.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public final class KeyBindings {

    public static final String CATEGORY = "key.categories.infection";

    public static final KeyMapping OPEN_LIST = new KeyMapping(
            "key.infection.open_list",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_V,
            CATEGORY
    );

    /** Запуск выбранного инвентика (PREPARING → ACTIVE). По умолчанию G. */
    public static final KeyMapping EVENT_LAUNCH = new KeyMapping(
            "key.infection.event_launch",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            CATEGORY
    );

    /** Отмена инвентика (PREPARING/ACTIVE → IDLE). По умолчанию B. */
    public static final KeyMapping EVENT_CANCEL = new KeyMapping(
            "key.infection.event_cancel",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_B,
            CATEGORY
    );

    /** Запуск SMOKE-инвентика (исчезнуть в дыму, модель админа → чёрный силуэт). По умолчанию R. */
    public static final KeyMapping EVENT_SMOKE_HIDE = new KeyMapping(
            "key.infection.event_smoke_hide",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            CATEGORY
    );

    /** Завершение SMOKE-инвентика (появиться из дыма, модель → нормальный вид). По умолчанию T. */
    public static final KeyMapping EVENT_SMOKE_SHOW = new KeyMapping(
            "key.infection.event_smoke_show",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_T,
            CATEGORY
    );

    private KeyBindings() {}
}
