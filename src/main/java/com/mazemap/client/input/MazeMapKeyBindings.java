package com.mazemap.client.input;

import com.labyrinthmod.LabyrinthMod;
import com.mazemap.client.ClientHudState;
import com.mazemap.client.MazePathFinder;
import com.mazemap.network.MazeMapNetwork;
import com.mazemap.network.packet.C2SRequestMapPacket;
import com.mazemap.network.packet.C2STransferFragmentsPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Хоткеи мода:
 *  - OPEN_MAP (M) — открыть полноэкранную карту-записку.
 *  - TOGGLE_NOTE (N) — взять/убрать «записку с картой» в руку.
 *      • 1-е нажатие  → noteHeld = true, иконка справа снизу.
 *      • 2-е нажатие  → entity-raycast 8 блоков. Если в crosshair игрок —
 *        шлём C2STransferFragmentsPacket с его UUID. Если нет — actionbar
 *        «Посмотрите на игрока», записка остаётся в руке.
 *  - HOLD_MINIMAP (J) — ЗАЖИМ. Пока зажата клавиша — мини-карта рисуется
 *    в углу. Отпустил — скрылась. Tick-based чтение через isDown(); сам
 *    хук рисования {@link com.mazemap.client.HudOverlayRenderer} читает то же
 *    самое.
 *
 * Все три клавиши — стандартные KeyMapping, переназначаются через Controls.
 */
@OnlyIn(Dist.CLIENT)
public final class MazeMapKeyBindings {

    public static final KeyMapping OPEN_MAP = new KeyMapping(
            "key." + LabyrinthMod.MOD_ID + ".open_map",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories." + LabyrinthMod.MOD_ID
    );

    public static final KeyMapping TOGGLE_NOTE = new KeyMapping(
            "key." + LabyrinthMod.MOD_ID + ".toggle_note",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories." + LabyrinthMod.MOD_ID
    );

    public static final KeyMapping HOLD_MINIMAP = new KeyMapping(
            "key." + LabyrinthMod.MOD_ID + ".hold_minimap",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories." + LabyrinthMod.MOD_ID
    );



    private static final double LOOK_RANGE = 8.0;
    /** Пересчёт пути не чаще раз в N тиков, чтобы не нагружать клиент BFS-ом. */
    private static final int PATH_RECOMPUTE_INTERVAL_TICKS = 20;
    private static int lastPathRecomputeTick = -1;

    private MazeMapKeyBindings() {}

        public static void register(IEventBus modBus) {
            modBus.addListener(MazeMapKeyBindings::onRegister);
        }

        private static void onRegister(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MAP);
            event.register(TOGGLE_NOTE);
            event.register(HOLD_MINIMAP);
        }



        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            while (OPEN_MAP.consumeClick()) {
                MazeMapNetwork.CHANNEL.sendToServer(new C2SRequestMapPacket());
            }

            while (TOGGLE_NOTE.consumeClick()) {
                if (!ClientHudState.isNoteHeld()) {
                    ClientHudState.setNoteHeld(true);
                } else {
                    tryTransferToLookedAt(mc.player);
                }
            }

            // HOLD_MINIMAP — не consumeClick(), а постоянное чтение isDown() в HUD-рендерере.
            // Никакой логики тут не надо: рендерер сам прочитает isDown() и нарисует.

            // Периодический пересчёт пути от текущей позиции игрока до метки.
            // Без этого путь после клика ПКМ был "застывшим" — на мини-карте он
            // рендерился относительно старой позиции игрока, и при движении
            // вообще не обновлялся. Раз в секунду пересчитываем BFS — нагрузка
            // приемлемая (BFS limit 200×200, обычно < 1ms).
            if (ClientHudState.hasMarker()
                    && mc.player.tickCount - lastPathRecomputeTick >= PATH_RECOMPUTE_INTERVAL_TICKS) {
                lastPathRecomputeTick = mc.player.tickCount;
                int sx = (int) Math.floor(mc.player.getX());
                int sz = (int) Math.floor(mc.player.getZ());
                int mx = ClientHudState.getMarkerX();
                int mz = ClientHudState.getMarkerZ();
                ClientHudState.setPath(MazePathFinder.findPath(sx, sz, mx, mz));
            }
        }

        /**
         * Entity-raycast 8 блоков в направлении взгляда. Логика:
         *  - попал в игрока → отправляем packet, сбрасываем noteHeld;
         *  - не попал (смотрит в воздух/блок) → передача ОТМЕНЯЕТСЯ, noteHeld тоже
         *    сбрасывается, actionbar сообщает что отменено.
         *
         * Так второе нажатие N всегда уводит из состояния «записка в руке»,
         * независимо от цели — можно отменить просто кликнув в пустоту.
         */
    private static void tryTransferToLookedAt(LocalPlayer self) {
        Player target = raycastPlayer(self);
        if (target == null) {
            self.displayClientMessage(
                    Component.literal("§7Передача отменена"), true);
            ClientHudState.setNoteHeld(false);
            return;
        }
        MazeMapNetwork.CHANNEL.sendToServer(
                new C2STransferFragmentsPacket(target.getUUID()));
        ClientHudState.setNoteHeld(false);
    }

    private static Player raycastPlayer(LocalPlayer self) {
        Vec3 eye = self.getEyePosition(1.0F);
        Vec3 look = self.getViewVector(1.0F);
        Vec3 end = eye.add(look.x * LOOK_RANGE, look.y * LOOK_RANGE, look.z * LOOK_RANGE);
        AABB box = self.getBoundingBox()
                .expandTowards(look.scale(LOOK_RANGE))
                .inflate(1.0, 1.0, 1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                self, eye, end, box,
                e -> e instanceof Player && e != self && !e.isSpectator(),
                LOOK_RANGE * LOOK_RANGE);
        if (hit == null) return null;
        Entity e = hit.getEntity();
        return e instanceof Player p ? p : null;
    }
}
