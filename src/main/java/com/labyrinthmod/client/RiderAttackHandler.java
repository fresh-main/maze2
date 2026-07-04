package com.labyrinthmod.client;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.entity.GriverEntity;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.RiderAttackPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, value = Dist.CLIENT)
public class RiderAttackHandler {

    private static int attackCooldown = 0;

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        // Принимаем только PRESS, не REPEAT — иначе зажатый ЛКМ спамит пакетами
        // и на сервере анимация перезапускается каждый раз, выглядит как «дёрг».
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // Проверка прав на клиенте
        if (!player.hasPermissions(2) && !player.isCreative()) return;

        if (player.getVehicle() instanceof GriverEntity) {
            if (attackCooldown > 0) return;
            // 30 тиков = длительность анимации атаки (см. attackAnimationTimer
            // в GriverEntity.startAttackAnimation). Не даём кликам перебивать клип.
            attackCooldown = 30;
            NetworkHandler.CHANNEL.sendToServer(new RiderAttackPacket());
            event.setCanceled(true); // Отменяем стандартную атаку
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().getVehicle() instanceof GriverEntity) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        // Пакет уже послан в onMouseInput
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && attackCooldown > 0) attackCooldown--;
    }
}