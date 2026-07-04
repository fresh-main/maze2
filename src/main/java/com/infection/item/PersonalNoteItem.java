package com.infection.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * Личная записка-самочувствие. Right-click открывает экран чтения,
 * текст берётся из server-синхронизированной capability игрока (а не из NBT предмета),
 * чтобы текст переживал потерю предмета и был общий на всех копиях.
 *
 * Клиентский экран ОТКРЫВАЕТСЯ через {@code com.infection.client.PersonalNoteClientHook} —
 * прямая ссылка на {@code PersonalNoteScreen} внутри байткода этого класса вызывала
 * RuntimeDistCleaner-ошибку на dedicated сервере (Screen — @OnlyIn(CLIENT)).
 */
public class PersonalNoteItem extends Item {

    public PersonalNoteItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.infection.client.PersonalNoteClientHook.open(player.getUUID()));
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
