package com.labyrinthmod.common.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = "labyrinthmod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LiftCommandHandler {

    private static final int DELAY_TICKS = 300;
    private static final int SECOND_DELAY_TICKS = 3600;
    private static final int RESPAWN_DELAY_TICKS = 400;
    private static final String KEY_LIFT_EXECUTED = "labyrinthmod_lift_executed";

    public static final String LIFT_1_NAME = "lift_1";
    public static final String LIFT_2_NAME = "lift_2";
    public static final BlockPos LIFT_1_POS = new BlockPos(-16, -27, -12);
    public static final BlockPos LIFT_2_POS = new BlockPos(-16, 11, -12);

    private static final Map<UUID, Integer> pendingPlayers = new HashMap<>();
    private static final Map<UUID, Integer> secondPendingPlayers = new HashMap<>();
    private static final Map<UUID, Integer> respawnPendingPlayers = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.level().dimension().equals(Level.OVERWORLD)) return;

        // ★ ИСПРАВЛЕНИЕ №2: Игнорируем игроков, которые сейчас ждут в меню ★
        if (LiftLockManager.isWaiting(player.getUUID())) {
            return;
        }

        // ★ ЕСЛИ МИР УЖЕ ЗАБЛОКИРОВАН (идет ритуал другого игрока) ★
        // Мы НЕ добавляем этого игрока в очередь.
        // Он будет добавлен в очередь позже, когда LiftLockManager его освободит.
        if (LiftLockManager.isLocked()) {
            return;
        }

        CompoundTag persistent = player.getPersistentData();
        boolean liftDone = persistent.getBoolean(KEY_LIFT_EXECUTED);

        if (!liftDone) {
            pendingPlayers.put(player.getUUID(), 0);
            // ★ БЛОКИРУЕМ МИР ДЛЯ НОВЫХ ИГРОКОВ ★
            LiftLockManager.lock(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // 1. ПЕРВАЯ АКТИВАЦИЯ
        if (!pendingPlayers.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> iterator = pendingPlayers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Integer> entry = iterator.next();
                UUID uuid = entry.getKey();
                int ticks = entry.getValue() + 1;
                if (ticks >= DELAY_TICKS) {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player != null && player.level().dimension().equals(Level.OVERWORLD)) {
                        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "activate lift");
                        player.teleportTo(0, -15, 0);
                        secondPendingPlayers.put(uuid, 0);
                        player.getPersistentData().putBoolean(KEY_LIFT_EXECUTED, true);
                    }
                    iterator.remove();
                } else {
                    entry.setValue(ticks);
                }
            }
        }

        // 2. ВТОРАЯ АКТИВАЦИЯ
        if (!secondPendingPlayers.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> iterator2 = secondPendingPlayers.entrySet().iterator();
            while (iterator2.hasNext()) {
                Map.Entry<UUID, Integer> entry = iterator2.next();
                UUID uuid = entry.getKey();
                int ticks = entry.getValue() + 1;
                if (ticks >= SECOND_DELAY_TICKS) {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player != null && player.level().dimension().equals(Level.OVERWORLD)) {
                        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "activate lift");
                        respawnPendingPlayers.put(uuid, 0);
                    }
                    iterator2.remove();
                } else {
                    entry.setValue(ticks);
                }
            }
        }

        // 3. ОБНОВЛЕНИЕ СТРУКТУР И СНЯТИЕ БЛОКИРОВКИ
        if (!respawnPendingPlayers.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> iterator3 = respawnPendingPlayers.entrySet().iterator();
            while (iterator3.hasNext()) {
                Map.Entry<UUID, Integer> entry = iterator3.next();
                UUID uuid = entry.getKey();
                int ticks = entry.getValue() + 1;
                if (ticks >= RESPAWN_DELAY_TICKS) {
                    respawnLiftStructure(server, LIFT_1_NAME, LIFT_1_POS);
                    respawnLiftStructure(server, LIFT_2_NAME, LIFT_2_POS);
                    System.out.println("[LiftCommand] Lift structures refreshed.");

                    // ★ СНЯТИЕ БЛОКИРОВКИ И ЗАПУСК РИТУАЛА ДЛЯ ОЖИДАЮЩИХ ★
                    LiftLockManager.unlockAndReset(server);

                    iterator3.remove();
                } else {
                    entry.setValue(ticks);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        pendingPlayers.remove(uuid);
        secondPendingPlayers.remove(uuid);
        respawnPendingPlayers.remove(uuid);

        LiftLockManager.removeWaiting(uuid);
    }

    public static void addToPending(UUID uuid) {
        pendingPlayers.put(uuid, 0);
        System.out.println("[LiftCommand] Игрок добавлен в очередь лифта после снятия блокировки: " + uuid);
    }

    public static void respawnLiftStructure(MinecraftServer server, String structureName, BlockPos structurePos) {
        ServerLevel level = server.overworld();
        if (level == null) return;
        ResourceLocation loc = new ResourceLocation("labyrinthmod", structureName);
        StructureTemplateManager templateManager = level.getStructureManager();
        Optional<StructureTemplate> optTemplate = templateManager.get(loc);
        if (optTemplate.isEmpty()) return;

        StructureTemplate template = optTemplate.get();
        Vec3i size = template.getSize();
        if (size.getX() == 0 && size.getY() == 0 && size.getZ() == 0) return;

        AABB area = new AABB(
                structurePos.getX() - 1, structurePos.getY() - 1, structurePos.getZ() - 1,
                structurePos.getX() + size.getX() + 1, structurePos.getY() + size.getY() + 1, structurePos.getZ() + size.getZ() + 1
        );

        List<Entity> entities = level.getEntities((Entity) null, area, e -> true);
        for (Entity entity : entities) {
            if (!(entity instanceof ServerPlayer)) entity.discard();
        }

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE).setRotation(Rotation.NONE).setIgnoreEntities(false);

        template.placeInWorld(level, structurePos, structurePos, settings, RandomSource.create(level.getSeed()), 3);
    }
}