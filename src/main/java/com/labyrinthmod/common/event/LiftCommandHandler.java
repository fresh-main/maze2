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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
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

    // ═══════════════════════════════════════════════════════════
    // НОВОЕ: Очередь для ивента лифта по заданиям (БЕЗ телепортации)
    // ═══════════════════════════════════════════════════════════
    private static boolean questLiftEventActive = false;
    private static int questLiftPhase = 0;       // 0=не активен, 1=первая активация, 2=вторая активация, 3=респаун
    private static int questLiftTimer = 0;

    // ═══════════════════════════════════════════════════════════
    // ЛОГИН ИГРОКА (стандартный путь — первый заход)
    // ═══════════════════════════════════════════════════════════
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.level().dimension().equals(Level.OVERWORLD)) return;

        ServerLevel level = (ServerLevel) player.level();

        if (!isLiftGenerated(level)) {
            System.out.println("[LiftCommand] Лифт не сгенерирован. Ивент пропущен для " + player.getName().getString());
            return;
        }

        if (LiftLockManager.isWaiting(player.getUUID())) {
            return;
        }

        if (LiftLockManager.isLocked()) {
            return;
        }

        CompoundTag persistent = player.getPersistentData();
        boolean liftDone = persistent.getBoolean(KEY_LIFT_EXECUTED);
        if (!liftDone) {
            pendingPlayers.put(player.getUUID(), 0);
            LiftLockManager.lock(player.getUUID());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // СЕРВЕРНЫЙ ТИК
    // ═══════════════════════════════════════════════════════════
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // --- Стандартный путь (первый заход игрока) ---
        processPendingPlayers(server);
        processSecondPendingPlayers(server);
        processRespawnPendingPlayers(server);

        // --- НОВЫЙ путь: ивент лифта по заданиям ---
        processQuestLiftEvent(server);
    }

    // ═══════════════════════════════════════════════════════════
    // СТАНДАРТНЫЙ ПУТЬ: первая активация
    // ═══════════════════════════════════════════════════════════
    private static void processPendingPlayers(MinecraftServer server) {
        if (pendingPlayers.isEmpty()) return;

        Iterator<Map.Entry<UUID, Integer>> it = pendingPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            UUID uuid = entry.getKey();
            int ticks = entry.getValue() + 1;

            if (ticks >= DELAY_TICKS) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null && player.level().dimension().equals(Level.OVERWORLD)) {
                    server.getCommands().performPrefixedCommand(
                            player.createCommandSourceStack(), "activate lift");
                    player.teleportTo(0, -15, 0);
                    secondPendingPlayers.put(uuid, 0);
                    player.getPersistentData().putBoolean(KEY_LIFT_EXECUTED, true);
                }
                it.remove();
            } else {
                entry.setValue(ticks);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // СТАНДАРТНЫЙ ПУТЬ: вторая активация
    // ═══════════════════════════════════════════════════════════
    private static void processSecondPendingPlayers(MinecraftServer server) {
        if (secondPendingPlayers.isEmpty()) return;

        Iterator<Map.Entry<UUID, Integer>> it = secondPendingPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            UUID uuid = entry.getKey();
            int ticks = entry.getValue() + 1;

            if (ticks >= SECOND_DELAY_TICKS) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null && player.level().dimension().equals(Level.OVERWORLD)) {
                    server.getCommands().performPrefixedCommand(
                            player.createCommandSourceStack(), "activate lift");
                    respawnPendingPlayers.put(uuid, 0);
                }
                it.remove();
            } else {
                entry.setValue(ticks);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // СТАНДАРТНЫЙ ПУТЬ: респаун структур и разблокировка
    // ═══════════════════════════════════════════════════════════
    private static void processRespawnPendingPlayers(MinecraftServer server) {
        if (respawnPendingPlayers.isEmpty()) return;

        Iterator<Map.Entry<UUID, Integer>> it = respawnPendingPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            UUID uuid = entry.getKey();
            int ticks = entry.getValue() + 1;

            if (ticks >= RESPAWN_DELAY_TICKS) {
                respawnLiftStructure(server, LIFT_1_NAME, LIFT_1_POS);
                respawnLiftStructure(server, LIFT_2_NAME, LIFT_2_POS);
                System.out.println("[LiftCommand] Lift structures refreshed.");
                LiftLockManager.unlockAndReset(server);
                it.remove();
            } else {
                entry.setValue(ticks);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ★ НОВОЕ: ИВЕНТ ЛИФТА ПО ЗАДАНИЯМ (БЕЗ ТЕЛЕПОРТАЦИИ) ★
    // ═══════════════════════════════════════════════════════════

    /**
     * Запускает ивент лифта по выполнению всех заданий.
     * Вызывается из QuestCompletionTracker.
     */
    public static void startQuestLiftEvent() {
        if (questLiftEventActive) return;

        questLiftEventActive = true;
        questLiftPhase = 1;
        questLiftTimer = 0;
        System.out.println("[LiftCommand] ★ Квест-ивент лифта запущен. Фаза 1: первая активация через " + DELAY_TICKS + " тиков.");
    }

    /**
     * Обработка квест-ивента лифта каждый тик.
     * Фазы:
     *   1 → ждём DELAY_TICKS → первая активация (без телепорта)
     *   2 → ждём SECOND_DELAY_TICKS → вторая активация
     *   3 → ждём RESPAWN_DELAY_TICKS → респаун структур → разблокировка
     */
    private static void processQuestLiftEvent(MinecraftServer server) {
        if (!questLiftEventActive) return;

        questLiftTimer++;

        switch (questLiftPhase) {
            case 1: // Ждём до первой активации
                if (questLiftTimer >= DELAY_TICKS) {
                    System.out.println("[LiftCommand] Квест-ивент: ПЕРВАЯ АКТИВАЦИЯ лифта (без телепортации).");
                    executeLiftActivation(server);
                    questLiftPhase = 2;
                    questLiftTimer = 0;
                }
                break;

            case 2: // Ждём до второй активации
                if (questLiftTimer >= SECOND_DELAY_TICKS) {
                    System.out.println("[LiftCommand] Квест-ивент: ВТОРАЯ АКТИВАЦИЯ лифта.");
                    executeLiftActivation(server);
                    questLiftPhase = 3;
                    questLiftTimer = 0;
                }
                break;

            case 3: // Ждём до респауна структур
                if (questLiftTimer >= RESPAWN_DELAY_TICKS) {
                    System.out.println("[LiftCommand] Квест-ивент: РЕСПАУН структур лифта.");
                    respawnLiftStructure(server, LIFT_1_NAME, LIFT_1_POS);
                    respawnLiftStructure(server, LIFT_2_NAME, LIFT_2_POS);

                    // Разблокировка мира
                    LiftLockManager.unlockAndReset(server);

                    // Сброс трекера заданий
                    com.labyrinthmod.common.quest.QuestCompletionTracker.onLiftEventFinished();

                    // Завершение ивента
                    questLiftEventActive = false;
                    questLiftPhase = 0;
                    questLiftTimer = 0;
                    System.out.println("[LiftCommand] Квест-ивент лифта полностью завершён.");
                }
                break;

            default:
                questLiftEventActive = false;
                questLiftPhase = 0;
                questLiftTimer = 0;
                break;
        }
    }

    /**
     * Выполняет активацию лифта БЕЗ телепортации игрока.
     * Отправляет команду "activate lift" от имени сервера.
     */
    private static void executeLiftActivation(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        // Проверяем что лифт ещё существует
        if (!isLiftGenerated(overworld)) {
            System.out.println("[LiftCommand] Квест-ивент: лифт больше не существует, пропускаем активацию.");
            return;
        }

        // Выполняем команду от имени сервера (без привязки к игроку)
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(), "activate lift");
    }

    // ═══════════════════════════════════════════════════════════
    // ВЫХОД ИГРОКА
    // ═══════════════════════════════════════════════════════════
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
        System.out.println("[LiftCommand] Игрок добавлен в очередь лифта: " + uuid);
    }

    // ═══════════════════════════════════════════════════════════
    // РЕСПАУН СТРУКТУР ЛИФТА
    // ═══════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════
    // ПРОВЕРКА СУЩЕСТВОВАНИЯ ЛИФТА
    // ═══════════════════════════════════════════════════════════
    public static boolean isLiftGenerated(ServerLevel level) {
        StructureTemplateManager manager = level.getServer().getStructureManager();
        Optional<StructureTemplate> opt = manager.get(new ResourceLocation("labyrinthmod", "lift_1"));
        if (opt.isEmpty()) return false;

        StructureTemplate template = opt.get();
        BlockPos origin = LIFT_1_POS;
        Vec3i size = template.getSize();

        if (level.getBlockState(origin).isAir()) {
            return false;
        }

        try {
            java.lang.reflect.Field palettesField = template.getClass().getDeclaredField("palettes");
            palettesField.setAccessible(true);
            List<?> palettes = (List<?>) palettesField.get(template);

            if (palettes != null && !palettes.isEmpty()) {
                Object palette = palettes.get(0);
                java.lang.reflect.Method blocksMethod = palette.getClass().getMethod("blocks");
                List<?> blocks = (List<?>) blocksMethod.invoke(palette);

                int uniqueMatches = 0;
                int uniqueChecks = 0;

                for (Object blockInfo : blocks) {
                    java.lang.reflect.Method posMethod = blockInfo.getClass().getMethod("pos");
                    java.lang.reflect.Method stateMethod = blockInfo.getClass().getMethod("state");
                    BlockPos pos = (BlockPos) posMethod.invoke(blockInfo);
                    BlockState state = (BlockState) stateMethod.invoke(blockInfo);

                    if (isCommonNaturalBlock(state.getBlock())) continue;

                    BlockPos worldPos = origin.offset(pos);
                    if (level.getBlockState(worldPos).equals(state)) {
                        uniqueMatches++;
                    }
                    uniqueChecks++;
                    if (uniqueChecks >= 5) break;
                }

                if (uniqueMatches > 0) return true;

                if (size.getX() > 2 && size.getY() > 2 && size.getZ() > 2) {
                    BlockPos center = origin.offset(size.getX() / 2, size.getY() / 2, size.getZ() / 2);
                    BlockState centerState = level.getBlockState(center);
                    if (centerState.isAir() || !isCommonNaturalBlock(centerState.getBlock())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            if (size.getX() > 2 && size.getY() > 2 && size.getZ() > 2) {
                BlockPos center = origin.offset(size.getX() / 2, size.getY() / 2, size.getZ() / 2);
                BlockState centerState = level.getBlockState(center);
                if (centerState.isAir() || !isCommonNaturalBlock(centerState.getBlock())) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isCommonNaturalBlock(Block block) {
        return block == Blocks.STONE ||
                block == Blocks.DEEPSLATE ||
                block == Blocks.DIRT ||
                block == Blocks.GRASS_BLOCK ||
                block == Blocks.AIR ||
                block == Blocks.CAVE_AIR ||
                block == Blocks.GRAVEL ||
                block == Blocks.BEDROCK ||
                block == Blocks.WATER ||
                block == Blocks.TUFF ||
                block == Blocks.ANDESITE ||
                block == Blocks.DIORITE ||
                block == Blocks.GRANITE ||
                block == Blocks.COBBLESTONE ||
                block == Blocks.MOSSY_COBBLESTONE;
    }

    /**
     * Активен ли сейчас квест-ивент лифта.
     */
    public static boolean isQuestLiftEventActive() {
        return questLiftEventActive;
    }
}