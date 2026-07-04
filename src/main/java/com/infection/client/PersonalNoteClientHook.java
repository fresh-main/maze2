package com.infection.client;

import com.infection.client.gui.PersonalNoteScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.UUID;

/**
 * Изолятор для запуска клиентского экрана записки. Существует, чтобы PersonalNoteItem
 * не содержал прямой ссылки на PersonalNoteScreen в своём байткоде — иначе на
 * dedicated server Forge RuntimeDistCleaner падает при инспекции метода use(),
 * потому что Screen помечен @OnlyIn(CLIENT) и недоступен в DEDICATED_SERVER dist.
 *
 * Этот класс грузится только когда invokestatic из PersonalNoteItem реально срабатывает,
 * что бывает лишь на клиенте (внутри DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)).
 */
@OnlyIn(Dist.CLIENT)
public final class PersonalNoteClientHook {

    private PersonalNoteClientHook() {}

    public static void open(UUID playerId) {
        Minecraft.getInstance().setScreen(new PersonalNoteScreen(playerId));
    }
}
