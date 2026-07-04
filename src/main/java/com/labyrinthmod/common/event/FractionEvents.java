package com.labyrinthmod.common.event;

import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.capability.PlayerFractionData;
import com.labyrinthmod.common.data.CraftRestrictionManager;
import com.labyrinthmod.common.entity.GriverEntity;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.FractionRevealPacket;
import com.labyrinthmod.common.network.packet.SyncFractionPacket;
import com.labyrinthmod.common.zone.ZoneManager;
import com.labyrinthmod.common.zone.ZoneManager.Zone;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SmokerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class FractionEvents {

    private static final java.util.Map<Player, BlockPos> lastSafePositions = new java.util.HashMap<>();
    private static final java.util.Map<Player, Integer> messageCooldown = new java.util.HashMap<>();
    private static final java.util.Map<Player, Boolean> lastZoneStatus = new java.util.HashMap<>();
    /** Кэш применённой фракции — чтобы не пересоздавать атрибуты каждый тик. */
    private static final java.util.Map<java.util.UUID, FractionType> lastAppliedFraction = new java.util.HashMap<>();

    // UUID для модификаторов атрибутов
    private static final UUID RUNNER_SPEED_MODIFIER = UUID.fromString("a1b2c3d4-e5f6-7890-1234-567890abcdef");
    private static final UUID BUTCHER_DAMAGE_MODIFIER = UUID.fromString("b2c3d4e5-f6a7-8901-2345-67890abcdef1");
    private static final UUID OPERATOR_SPEED_MODIFIER = UUID.fromString("d4e5f6a7-b8c9-0123-4567-890abcdef123");

    // Значения атрибутов
    /** Бегун: 1.25× базовой скорости (MULTIPLY_TOTAL +0.25). */
    private static final double RUNNER_SPEED_BONUS = 0.25;
    /** Мясник: +3 к урону (≈ эффект «Сила I»). */
    private static final double BUTCHER_DAMAGE_BONUS = 3.0;
    /** Оператор: +5% скорости. */
    private static final double OPERATOR_SPEED_BONUS = 0.05;

    private static final Map<UUID, Integer> stuckCounter = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        int tick = player.tickCount;

        // Атрибуты — пересоздаём ТОЛЬКО при смене фракции (кэшируем по UUID).
        java.util.UUID uuid = player.getUUID();
        FractionType currentFraction = player.getCapability(FractionProvider.FRACTION)
                .map(PlayerFractionData::getFraction).orElse(FractionType.NONE);
        FractionType cachedFraction = lastAppliedFraction.get(uuid);
        if (cachedFraction != currentFraction) {
            LOGGER.info("[fraction-change] {}: {} -> {}, переприменяю атрибуты",
                    player.getName().getString(), cachedFraction, currentFraction);
            applyAttributes(player);
            lastAppliedFraction.put(uuid, currentFraction);
        }

        // Тихая страховка: раз в 30 секунд для мясника проверяем что бонус ещё прицеплен.
        // Логируем ТОЛЬКО если что-то не так (warn). При штатной работе — без лога.
        if (currentFraction == FractionType.BUTCHER && tick % 600 == 0) {
            AttributeInstance da = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (da != null && da.getModifier(BUTCHER_DAMAGE_MODIFIER) == null) {
                LOGGER.warn("[BUTCHER] {} — модификатор урона пропал, переприменяю",
                        player.getName().getString());
                applyDamageBoost(player);
            }
        }

        // Постоянные бонусы фракций реализованы через атрибуты в applyAttributes()
        // (без видимых эффект-иконок в HUD). MEDIC получает регенерацию через
        // атрибут реген здоровья — applyAttributes ниже.
        // Старые добавления MobEffects.* удалены: бегун/мясник/медик больше не
        // получают эффект, только атрибут.
        if (tick % 200 == 0 && !isOperator(player)) {
            // Снимаем эффекты, которые могли остаться от старой версии.
            if (player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                MobEffectInstance e = player.getEffect(MobEffects.MOVEMENT_SPEED);
                if (e != null && !e.isVisible()) player.removeEffect(MobEffects.MOVEMENT_SPEED);
            }
            if (player.hasEffect(MobEffects.DAMAGE_BOOST)) {
                MobEffectInstance e = player.getEffect(MobEffects.DAMAGE_BOOST);
                if (e != null && !e.isVisible()) player.removeEffect(MobEffects.DAMAGE_BOOST);
            }
            if (player.hasEffect(MobEffects.REGENERATION)) {
                MobEffectInstance e = player.getEffect(MobEffects.REGENERATION);
                if (e != null && !e.isVisible()) player.removeEffect(MobEffects.REGENERATION);
            }
        }

        // Регенерация для медика — без эффект-иконки. Каждые 50 тиков (2.5с) +1 HP при
        // неполном здоровье. Работает только в живом и не-зрительском режиме.
        if (currentFraction == FractionType.MEDIC && tick % 50 == 0
                && player.isAlive() && !player.isSpectator()
                && player.getHealth() < player.getMaxHealth()) {
            player.heal(1.0f);
        }

        // Зоны — каждые 10 тиков (полсекунды). Достаточно, чтобы не выпустить из зоны.
        if (tick % 10 == 0 && !isOperator(player)) {
            checkZoneRestriction(player);
        }
        messageCooldown.put(player, Math.max(0, messageCooldown.getOrDefault(player, 0) - 1));

        // Печь — только если игрок реально открыл печь.
        if (!isOperator(player) && player.containerMenu instanceof FurnaceMenu) {
            returnFoodFromFurnace(player);
        }

        // Имя — раз в секунду (20 тиков), не каждый тик.
        if (tick % 20 == 0) {
            updatePlayerDisplayName(player);
        }
        // Crafting tick обрабатывается отдельным @SubscribeEvent (см. onPlayerTickCrafting).
        // Не вызываем его руками здесь, чтобы избежать двойной обработки.
    }

    // ========== АТРИБУТЫ ==========

    private static void applyAttributes(Player player) {
        if (player.level().isClientSide) return;

        player.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
            if (!data.hasFraction()) {
                removeAllAttributes(player);
                return;
            }

            FractionType fraction = data.getFraction();
            removeAllAttributes(player);

            switch (fraction) {
                case RUNNER:
                    applySpeedBoost(player, RUNNER_SPEED_MODIFIER, "Runner Speed Boost", RUNNER_SPEED_BONUS);
                    break;
                case BUTCHER:
                    applyDamageBoost(player);
                    break;
                case OPERATOR:
                case IMPOSTER:
                    applySpeedBoost(player, OPERATOR_SPEED_MODIFIER, "Operator Speed Boost", OPERATOR_SPEED_BONUS);
                    break;
                default:
                    break;
            }
        });
    }

    private static void removeAllAttributes(Player player) {
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance damageAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);

        if (speedAttr != null) {
            speedAttr.removeModifier(RUNNER_SPEED_MODIFIER);
            speedAttr.removeModifier(OPERATOR_SPEED_MODIFIER);
        }
        if (damageAttr != null) {
            damageAttr.removeModifier(BUTCHER_DAMAGE_MODIFIER);
        }
    }

    private static void applySpeedBoost(Player player, UUID modifierId, String name, double bonus) {
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(modifierId);
            AttributeModifier modifier = new AttributeModifier(
                    modifierId,
                    name,
                    bonus,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            );
            speedAttr.addTransientModifier(modifier);
        }
    }

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("labyrinthmod/fraction-attrs");

    private static void applyDamageBoost(Player player) {
        AttributeInstance damageAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr == null) {
            LOGGER.warn("[BUTCHER] {} — у игрока нет атрибута ATTACK_DAMAGE, бонус не применён",
                    player.getName().getString());
            return;
        }
        damageAttr.removeModifier(BUTCHER_DAMAGE_MODIFIER);
        AttributeModifier modifier = new AttributeModifier(
                BUTCHER_DAMAGE_MODIFIER,
                "Butcher Damage Boost",
                BUTCHER_DAMAGE_BONUS,
                AttributeModifier.Operation.ADDITION
        );
        damageAttr.addTransientModifier(modifier);
        // Лог с подробностями: базовый/итоговый/бонус, чтобы наглядно видеть применение.
        double base   = damageAttr.getBaseValue();
        double total  = damageAttr.getValue();
        boolean has   = damageAttr.getModifier(BUTCHER_DAMAGE_MODIFIER) != null;
        LOGGER.info("[BUTCHER] {} — applyDamageBoost: bonus=+{} base={} total={} modifierAttached={}",
                player.getName().getString(), BUTCHER_DAMAGE_BONUS, base, total, has);
    }

    // ========== ОПЕРАТОР ==========

    private static boolean isOperator(Player player) {
        return player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction() == FractionType.OPERATOR)
                .orElse(false);
    }

    // ========== ЧАТ ==========

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerChat(net.minecraftforge.event.ServerChatEvent event) {
        if (!isOperator(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    // ========== ПЕЧЬ ==========

    private static void returnFoodFromFurnace(Player player) {
        if (player.containerMenu instanceof FurnaceMenu furnaceMenu) {
            Slot inputSlot = furnaceMenu.slots.get(0);
            Slot resultSlot = furnaceMenu.slots.get(2);

            ItemStack input = inputSlot.getItem();
            ItemStack result = resultSlot.getItem();

            if (isRawFood(input)) {
                ItemStack toReturn = input.copy();
                if (!player.getInventory().add(toReturn)) {
                    player.drop(toReturn, false);
                }
                inputSlot.set(ItemStack.EMPTY);
            }

            if (!result.isEmpty() && result.getItem().isEdible()) {
                resultSlot.set(ItemStack.EMPTY);
            }
        }
    }

    private static boolean isRawFood(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) ||
                stack.is(Items.MUTTON) || stack.is(Items.CHICKEN) ||
                stack.is(Items.RABBIT) || stack.is(Items.COD) ||
                stack.is(Items.SALMON) || stack.is(Items.POTATO) ||
                stack.is(Items.KELP);
    }

    // ========== ЗОНЫ ==========

    private static void checkZoneRestriction(Player player) {
        if (player.level().isClientSide) return;

        // Креатив/наблюдатель - без ограничений
        if (player instanceof ServerPlayer sp) {
            GameType gm = sp.gameMode.getGameModeForPlayer();
            if (gm == GameType.CREATIVE || gm == GameType.SPECTATOR) return;
        }

        ZoneManager manager = ZoneManager.get(player.level());
        if (manager == null) return;

        BlockPos currentPos = player.blockPosition();

        player.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
            FractionType fraction = data.getFraction();

            // Раньше «кто может выходить из зон» был ХАРДКОДЕН (RUNNER/IMPOSTER/OPERATOR).
            // Теперь это per-fraction-флаг в ZoneManager — админ настраивает через
            // GUI «Доступ фракций» (FractionAccessScreen). Дефолты те же.
            boolean canLeaveZone = manager.canFractionLeave(fraction);

            if (canLeaveZone) {
                lastSafePositions.put(player, currentPos);
                updateGameModeByZone(player);
                stuckCounter.remove(player);
                return;
            }

            if (!manager.isZonesEnabled()) {
                updateGameModeByZone(player);
                return;
            }

            boolean isInZone = manager.isInsideAnyZone(currentPos);
            BlockPos lastSafePos = lastSafePositions.get(player);

            // Если игрок пытается выйти из зоны
            if (lastSafePos != null && !isInZone && manager.isInsideAnyZone(lastSafePos)) {
                // Возвращаем на последнюю безопасную позицию (как в невидимый блок)
                player.teleportTo(lastSafePos.getX() + 0.5, lastSafePos.getY(), lastSafePos.getZ() + 0.5);

                // Сбрасываем движение
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = true;

                // БЕЗ СООБЩЕНИЯ И БЕЗ ЧАСТИЦ
            } else if (isInZone) {
                lastSafePositions.put(player, currentPos);
                stuckCounter.remove(player);
            }

            updateGameModeByZone(player);
        });
    }
    /**
     * Проверяет, находится ли блок в любой зоне
     */
    private static boolean isBlockInAnyZone(Level level, BlockPos pos) {
        ZoneManager manager = ZoneManager.get(level);
        if (manager == null) return false;
        return manager.isInsideAnyZone(pos);
    }

    // ========== СОБЫТИЯ ИГРОКА ==========

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        lastSafePositions.put(player, player.blockPosition());
        messageCooldown.put(player, 0);

        // Обновляем отображение имени
        updatePlayerDisplayName(player);
        applyAttributes(player);

        lastZoneStatus.put(player, false);
        updateGameModeByZone(player);

        if (player instanceof ServerPlayer serverPlayer) {
            // Только синхронизируем capability — записка-личное-дело будет видеть текущую роль.
            // Атмосферный экран показываем ТОЛЬКО при выдаче командой (см. onFractionChanged),
            // а не при каждом заходе на сервер.
            syncFractionToClient(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            syncFractionToClient(sp);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            syncFractionToClient(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        lastSafePositions.remove(event.getEntity());
        messageCooldown.remove(event.getEntity());
        lastZoneStatus.remove(event.getEntity());
        lastAppliedFraction.remove(event.getEntity().getUUID());
        removeAllAttributes(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().revive();
        event.getEntity().getCapability(FractionProvider.FRACTION).ifPresent(newData -> {
            event.getOriginal().getCapability(FractionProvider.FRACTION).ifPresent(oldData -> {
                newData.setFraction(oldData.getFraction());
                newData.setImposterMask(oldData.getImposterMask());
            });
        });
        updatePlayerDisplayName(event.getEntity());
        applyAttributes(event.getEntity());
        lastZoneStatus.put(event.getEntity(), false);
        updateGameModeByZone(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer sp) {
            syncFractionToClient(sp);
        }
    }

    // ========== ОТОБРАЖЕНИЕ ==========

    private static String getColorCode(int rgb) {
        switch (rgb) {
            case 0xFFFF55: return "§e";
            case 0xFFAA00: return "§6";
            case 0x55FFFF: return "§b";
            case 0xFFFFFF: return "§f";
            case 0xFF55FF: return "§d";
            case 0xAAAAAA: return "§7";
            case 0xFF0000: return "§c";
            default: return "§f";
        }
    }

    public static void updatePlayerDisplayName(Player player) {
        player.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
            if (data.hasFraction() && data.getFraction() != FractionType.NONE) {
                FractionType fraction = data.getFraction();
                String colorCode = getColorCode(fraction.color);
                String displayName;

                // Для предателя - показываем маскировку
                if (fraction == FractionType.IMPOSTER && data.hasImposterMask()) {
                    String maskName = data.getImposterMask();
                    FractionType maskFraction = FractionType.fromName(maskName);
                    if (maskFraction != null && maskFraction != FractionType.NONE) {
                        String maskColorCode = getColorCode(maskFraction.color);
                        displayName = String.format("%s[%s] §f%s", maskColorCode, maskFraction.displayName, player.getName().getString());
                    } else {
                        displayName = String.format("%s[%s] §f%s", colorCode, fraction.displayName, player.getName().getString());
                    }
                } else {
                    displayName = String.format("%s[%s] §f%s", colorCode, fraction.displayName, player.getName().getString());
                }

                player.setCustomName(Component.literal(displayName));
                player.setCustomNameVisible(true);
            } else {
                player.setCustomName(null);
            }
        });
    }

// Удалите метод updateTabListName - он не нужен




    public static String getFractionDescription(FractionType fraction) {
        switch (fraction) {
            case FARMER: return "Твоя задача следить за урожаем";
            case BUTCHER: return "Твоя задача следить за скотом";
            case RUNNER: return "Твоя задача делать вылазки в лабиринт";
            case COOK: return "Твоя задача готовить еду";
            case MEDIC: return "Твоя задача лечить других";
            case OPERATOR: return "Мы тут главные";
            case IMPOSTER: return "Мешай игрокам. Планшет в помощь!";
            default: return "";
        }
    }

    // ========== УПРАВЛЕНИЕ РЕЖИМОМ ==========

    private static void updateGameModeByZone(Player player) {
        if (isOperator(player)) return;
        if (player.level().isClientSide) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        // Креатив и наблюдатель не сетятся принудительно. Админы и зрители — ходят свободно.
        GameType current = serverPlayer.gameMode.getGameModeForPlayer();
        if (current == GameType.CREATIVE || current == GameType.SPECTATOR) return;

        boolean isNoneFraction = player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction() == FractionType.NONE)
                .orElse(true);

        if (isNoneFraction) {
            if (serverPlayer.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                serverPlayer.setGameMode(GameType.ADVENTURE);
            }
            return;
        }

        ZoneManager manager = ZoneManager.get(player.level());
        if (manager == null) return;

        BlockPos currentPos = player.blockPosition();
        Zone currentZone = manager.getZoneAt(currentPos);
        boolean isInZone = (currentZone != null);

        Boolean wasInZone = lastZoneStatus.get(player);
        if (wasInZone != null && wasInZone == isInZone) return;

        lastZoneStatus.put(player, isInZone);

        if (isInZone) {
            if (serverPlayer.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
                serverPlayer.setGameMode(GameType.SURVIVAL);
            }
        } else {
            if (serverPlayer.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                serverPlayer.setGameMode(GameType.ADVENTURE);
            }
        }
    }

    // ========== ИЗМЕНЕНИЕ ФРАКЦИИ ==========

    private static void showFractionMessage(ServerPlayer player, FractionType fraction) {
        if (fraction == null || fraction == FractionType.NONE) return;

        // Атмосферный экран вместо ванильных title/subtitle.
        String mask = "";
        var capOpt = player.getCapability(FractionProvider.FRACTION).resolve();
        if (capOpt.isPresent() && capOpt.get().hasImposterMask()) {
            mask = capOpt.get().getImposterMask();
        }
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new FractionRevealPacket(fraction.id, mask)
        );
    }

    public static void onFractionChanged(Player player, FractionType oldFraction, FractionType newFraction) {
        updatePlayerDisplayName(player);
        applyAttributes(player);
        // Сбрасываем кэш, чтобы тик-обработчик гарантированно перенакатил атрибуты.
        lastAppliedFraction.put(player.getUUID(), newFraction);

        if (player instanceof ServerPlayer serverPlayer) {
            syncFractionToClient(serverPlayer);
            showFractionMessage(serverPlayer, newFraction);
        }
    }

    /** Шлём фракцию на клиент, чтобы записка-личное-дело видела актуальные данные. */
    public static void syncFractionToClient(ServerPlayer player) {
        player.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
            int id = data.getFraction() != null ? data.getFraction().id : -1;
            String mask = data.hasImposterMask() ? data.getImposterMask() : "";
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SyncFractionPacket(id, mask)
            );
        });
    }

    // ========== ЗАПРЕТЫ ДЛЯ ФЕРМЕРОВ ==========

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractWithFarmland(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (isOperator(player)) return;

        BlockState state = player.level().getBlockState(event.getPos());
        if (state.getBlock() instanceof FarmBlock) {
            player.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
                if (data.getFraction() != FractionType.FARMER) {
                    event.setCanceled(true);
                }
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCropPlant(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (isOperator(player)) return;

        ItemStack item = event.getItemStack();
        boolean isSeed = item.is(Items.WHEAT_SEEDS) || item.is(Items.CARROT) || item.is(Items.POTATO) ||
                item.is(Items.BEETROOT_SEEDS) || item.is(Items.MELON_SEEDS) || item.is(Items.PUMPKIN_SEEDS);

        if (isSeed) {
            player.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
                if (data.getFraction() != FractionType.FARMER) {
                    event.setCanceled(true);
                }
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCropHarvest(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (isOperator(player)) return;

        BlockState state = player.level().getBlockState(event.getPos());
        if (state.getBlock() instanceof CropBlock) {
            player.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
                if (data.getFraction() != FractionType.FARMER) {
                    event.setCanceled(true);
                }
            });
        }
    }

    // ========== ЗАПРЕТЫ НА ЛОМКУ БЛОКОВ ВНЕ ЗОНЫ ==========

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        BlockPos blockPos = event.getPos();
        Level level = player.level();

        if (player.isPassenger() && player.getVehicle() instanceof GriverEntity) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        // Проверяем фракцию игрока
        boolean isOperatorFraction = player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction() == FractionType.OPERATOR)
                .orElse(false);

        // Если игрок имеет фракцию OPERATOR - может ломать всё (и вне зон)
        if (isOperatorFraction) {
            return;
        }

        ZoneManager manager = ZoneManager.get(level);
        if (manager == null) return;

        // Проверяем, находится ли блок в любой зоне
        boolean isInAnyZone = false;
        for (ZoneManager.Zone zone : manager.getAllZones()) {
            if (zone.isInside(blockPos)) {
                isInAnyZone = true;
                break;
            }
        }

        // Если блок НЕ в зоне - НЕЛЬЗЯ ЛОМАТЬ (никому, кроме OPERATOR)
        if (!isInAnyZone) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);

            if (player instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundBlockUpdatePacket(level, blockPos));
            }
            return;
        }

        // Блок ВНУТРИ зоны - МОЖНО ЛОМАТЬ ЛЮБОЙ ФРАКЦИИ
        // Никаких дополнительных проверок!
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        BlockPos blockPos = event.getPos();
        Level level = player.level();

        // Проверяем фракцию игрока
        boolean isOperatorFraction = player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction() == FractionType.OPERATOR)
                .orElse(false);

        // Если игрок имеет фракцию OPERATOR - может ставить всё (и вне зон)
        if (isOperatorFraction) {
            return;
        }

        // ИСКЛЮЧЕНИЯ: контейнеры (сундуки/бочки/шалкеры/ender chest) и предмет в руке
        // тоже контейнер — должны работать В ЛЮБОМ МЕСТЕ ЛАБИРИНТА. Это нужно для
        // CarryOn (поднятие/перенос сундуков и бочек) и для штатной игры — иначе
        // нельзя поставить взятый из спавна сундук в комнате внутри лабиринта.
        BlockState targetState = level.getBlockState(blockPos);
        if (isCarriableContainer(targetState.getBlock())
                || isCarriableContainerItem(event.getItemStack())) {
            return;
        }

        ZoneManager manager = ZoneManager.get(level);
        if (manager == null) return;

        // Проверяем, находится ли блок в любой зоне
        boolean isInAnyZone = false;
        for (ZoneManager.Zone zone : manager.getAllZones()) {
            if (zone.isInside(blockPos)) {
                isInAnyZone = true;
                break;
            }
        }

        // Если блок НЕ в зоне - НЕЛЬЗЯ СТАВИТЬ (никому, кроме OPERATOR)
        if (!isInAnyZone) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        // Блок ВНУТРИ зоны - МОЖНО СТАВИТЬ ЛЮБОЙ ФРАКЦИИ
        // Никаких дополнительных проверок!
    }

    /** Контейнер, который игроки должны мочь брать/ставить везде (и в лабиринте). */
    private static boolean isCarriableContainer(Block block) {
        if (block == null) return false;
        return block instanceof net.minecraft.world.level.block.ChestBlock
                || block instanceof net.minecraft.world.level.block.BarrelBlock
                || block instanceof net.minecraft.world.level.block.TrappedChestBlock
                || block instanceof net.minecraft.world.level.block.EnderChestBlock
                || block instanceof net.minecraft.world.level.block.ShulkerBoxBlock
                || block instanceof net.minecraft.world.level.block.HopperBlock
                || block instanceof net.minecraft.world.level.block.AbstractFurnaceBlock
                || block instanceof net.minecraft.world.level.block.DispenserBlock;
    }

    /** Предмет в руке = сундук/бочка → ставим где угодно. Для CarryOn-носимого предмета
     *  это покрывает кейс «несу сундук, ставлю в лабиринте». */
    private static boolean isCarriableContainerItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        net.minecraft.world.item.Item item = stack.getItem();
        if (!(item instanceof net.minecraft.world.item.BlockItem bi)) return false;
        return isCarriableContainer(bi.getBlock());
    }

    // ========== ЗАПРЕТЫ ДЛЯ МЯСНИКОВ ==========

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFeedAnimal(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (isOperator(player)) return;

        if (event.getTarget() instanceof Animal animal) {
            ItemStack item = event.getItemStack();
            if (animal.isFood(item) && !animal.isBaby()) {
                player.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
                    if (data.getFraction() != FractionType.BUTCHER) {
                        event.setCanceled(true);
                    }
                });
            }
        }
    }

    // ========== ЗАПРЕТЫ ДЛЯ ПОВАРОВ ==========

    private static boolean isCook(Player player) {
        return player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction() == FractionType.COOK)
                .orElse(false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSmokerInteract(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (isOperator(player)) return;

        BlockState state = player.level().getBlockState(event.getPos());
        if (state.getBlock() instanceof SmokerBlock && !isCook(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCampfireInteract(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (isOperator(player)) return;

        BlockState state = player.level().getBlockState(event.getPos());
        if (state.getBlock() instanceof CampfireBlock && !isCook(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFurnaceInteract(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (isOperator(player)) return;

        BlockState state = player.level().getBlockState(event.getPos());
        if (state.getBlock() == Blocks.FURNACE && isRawFood(player.getMainHandItem())) {
            event.setCanceled(true);
        }
    }

    // ========== КРАФТ ==========

    private static boolean isFoodItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem().isEdible();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCraftItem(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (isOperator(player)) return;

        ItemStack result = event.getCrafting();
        if (isFoodItem(result) && !isCook(player)) {
            event.getInventory().setItem(0, ItemStack.EMPTY);
            return;
        }

        String playerFraction = getPlayerFraction(player);
        if (!CraftRestrictionManager.canCraft(result.getItem(), playerFraction)) {
            event.getInventory().setItem(0, ItemStack.EMPTY);
        }
    }

    @SubscribeEvent
    public static void onPlayerTickCrafting(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        // Достаточно проверять крафт-результат раз в 5 тиков — игрок не успеет забрать предмет.
        if (player.tickCount % 5 != 0) return;
        if (isOperator(player)) return;

        if (player.containerMenu != null && !player.containerMenu.slots.isEmpty()) {
            Slot resultSlot = player.containerMenu.slots.get(0);
            ItemStack result = resultSlot.getItem();

            if (!result.isEmpty()) {
                if (isFoodItem(result) && !isCook(player)) {
                    resultSlot.set(ItemStack.EMPTY);
                    return;
                }

                if (!CraftRestrictionManager.canCraft(result.getItem(), getPlayerFraction(player))) {
                    resultSlot.set(ItemStack.EMPTY);
                }
            }
        }
    }

    // ========== АТАКИ ==========

    private static boolean canAttackEntity(Player player) {
        if (isOperator(player)) return true;
        return player.getCapability(FractionProvider.FRACTION)
                .map(data -> {
                    FractionType fraction = data.getFraction();
                    return fraction == FractionType.RUNNER || fraction == FractionType.BUTCHER;
                })
                .orElse(false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (!canAttackEntity(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof Player player && event.getEntity() instanceof LivingEntity) {
            if (!canAttackEntity(player)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRangedWeaponUse(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack item = event.getItemStack();
        boolean isWeapon = item.getItem() instanceof net.minecraft.world.item.BowItem ||
                item.getItem() instanceof net.minecraft.world.item.CrossbowItem ||
                item.getItem() instanceof net.minecraft.world.item.SwordItem ||
                item.getItem() instanceof net.minecraft.world.item.AxeItem ||
                item.getItem() instanceof net.minecraft.world.item.TridentItem ||
                item.is(Items.EGG) || item.is(Items.SNOWBALL) ||
                item.is(Items.ENDER_PEARL) || item.is(Items.SPLASH_POTION) ||
                item.is(Items.LINGERING_POTION);

        if (isWeapon && !canAttackEntity(player)) {
            event.setCanceled(true);
        }
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private static String getPlayerFraction(Player player) {
        return player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.hasFraction() ? data.getFraction().name() : "NONE")
                .orElse("NONE");
    }
    // Вызывайте этот метод для операторов, чтобы они видели границы
    private static void showZoneBoundaryForOperator(Player player, ZoneManager manager) {
        if (!(player instanceof ServerPlayer)) return;
        if (player.tickCount % 40 != 0) return; // Раз в 2 секунды

        BlockPos playerPos = player.blockPosition();

        for (ZoneManager.Zone zone : manager.getAllZones()) {
            // Проверяем, находится ли игрок рядом с зоной (в радиусе 20 блоков)
            if (Math.abs(playerPos.getX() - (zone.minX + zone.maxX) / 2) > 25) continue;
            if (Math.abs(playerPos.getZ() - (zone.minZ + zone.maxZ) / 2) > 25) continue;

            // Показываем границы зоны (только для оператора)
            // Вертикальные линии по углам зоны
            int[][] corners = {
                    {zone.minX, zone.minZ}, {zone.minX, zone.maxZ},
                    {zone.maxX, zone.minZ}, {zone.maxX, zone.maxZ}
            };

            for (int[] corner : corners) {
                for (int y = zone.minY; y <= zone.maxY; y += 2) {
                    ((ServerLevel)player.level()).sendParticles(
                            net.minecraft.core.particles.ParticleTypes.GLOW,
                            corner[0] + 0.5, y, corner[1] + 0.5,
                            1,
                            0, 0, 0,
                            0
                    );
                }
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreakLowest(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (player.hasPermissions(2)) return; // Операторы могут всё

        BlockPos blockPos = event.getPos();
        Level level = player.level();

        // Проверяем, находится ли блок ВНЕ зоны
        ZoneManager manager = ZoneManager.get(level);
        if (manager == null) return;

        boolean isBlockOutsideZone = !manager.isInsideAnyZone(blockPos);

        if (isBlockOutsideZone) {
            // БЛОК ЗА ПРЕДЕЛАМИ ЗОНЫ - НЕЛЬЗЯ ЛОМАТЬ
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);

            // Дополнительно: сбрасываем разрушение блока
            if (!player.level().isClientSide && player instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundBlockUpdatePacket(level, blockPos));
            }
        }
    }
    // ========== ТИХОЕ ПЕРЕКЛЮЧЕНИЕ (F3+F6) ==========

    public static void onFractionChangedSilent(Player player, FractionType oldFraction, FractionType newFraction) {
        updatePlayerDisplayName(player);
        applyAttributes(player);
        lastAppliedFraction.put(player.getUUID(), newFraction);

        if (player instanceof ServerPlayer serverPlayer) {
            syncFractionToClient(serverPlayer);

            // Формируем сообщение с иконкой предмета при наведении
            ItemStack icon = FractionSwitcherData.getIcon(newFraction);
            Component msg = Component.literal("§7[§eF3+F6§7] §aФракция изменена на: ")
                    .append(Component.literal("[" + newFraction.displayName + "]").withStyle(style ->
                            style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(icon)))
                    ));

            serverPlayer.sendSystemMessage(msg);
        }
    }

}