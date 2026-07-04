package com.labyrinthmod.common.event;

import com.labyrinthmod.common.capability.PossessionProvider;
import com.labyrinthmod.common.entity.GriverEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class PossessionHandler {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        player.getCapability(PossessionProvider.POSSESSION).ifPresent(data -> {
            if (data.isPossessing()) {
                int entityId = data.getPossessedEntityId();
                var possessed = player.level().getEntity(entityId);
                if (possessed instanceof GriverEntity griver && !griver.isRemoved()) {

                    if (player instanceof ServerPlayer serverPlayer) {
                        // Если игрок уже сидит на гривере как пассажир, ванильная
                        // система пассажиров уже плавно перемещает его каждый тик.
                        // Принудительный teleportTo здесь ломает интерполяцию и даёт
                        // дёргание камеры. Поэтому форсим телепорт ТОЛЬКО когда
                        // игрок possess'ит без посадки (например, через /possess).
                        boolean isPassengerOfGriver = serverPlayer.getVehicle() == griver;

                        if (!isPassengerOfGriver) {
                            serverPlayer.teleportTo(
                                    griver.getX(),
                                    serverPlayer.getY(),
                                    griver.getZ()
                            );
                            serverPlayer.setYRot(griver.getYRot());
                            serverPlayer.setXRot(griver.getXRot());
                            serverPlayer.yHeadRot = griver.yHeadRot;
                            serverPlayer.yBodyRot = griver.yBodyRot;
                            serverPlayer.setDeltaMovement(griver.getDeltaMovement());
                        }

                        // Невидимость + предметы — ставим всегда, даже сидя как пассажир.
                        serverPlayer.setInvisible(true);
                        serverPlayer.noPhysics = !isPassengerOfGriver;
                        serverPlayer.setNoGravity(!isPassengerOfGriver);
                        serverPlayer.getInventory().setChanged();
                    }
                } else {
                    // Цель possess'а пропала (умерла / выгрузился чанк) — снимаем
                    // зависшие флаги, иначе игрок останется навсегда noPhysics+noGravity.
                    data.setPossessing(false);
                    data.setPossessedEntityId(-1);
                    if (player instanceof ServerPlayer sp) {
                        sp.setInvisible(false);
                        sp.noPhysics = false;
                        sp.setNoGravity(false);
                    }
                }
            }
        });
    }
}