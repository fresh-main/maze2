package com.labyrinthmod.common.event;

import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import com.mazemap.item.PersonalMapItem;
import com.mazemap.network.MazeMapNetwork;
import com.mazemap.network.packet.S2CClearMapPacket;
import ichttt.mods.firstaid.api.event.FirstAidLivingDamageEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public final class NewFractionAbilities {
    private NewFractionAbilities() {}
    private static final TagKey<Item> UNCOOKED_FOOD = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("labyrinthmod", "uncooked_food"));
    private static final UUID SOLDIER_STAMINA_ID = UUID.fromString("c26ed0d1-4ddc-4c2d-96ae-df41440b763d");
    private static final Set<UUID> SOLDIERS_BELOW_TEN_HEALTH = new HashSet<>();

    private static FractionType fraction(Player player) {
        return player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction()).orElse(FractionType.NONE);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        FractionType type = fraction(player);
        if (type == FractionType.SCIENTIST) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack armor = player.getItemBySlot(slot);
                if (!armor.isEmpty()) {
                    player.setItemSlot(slot, ItemStack.EMPTY);
                    if (!player.addItem(armor)) player.drop(armor, false);
                }
            }
        }
        if (type == FractionType.FARMER && player.tickCount % 40 == 0 && player.level() instanceof ServerLevel level) {
            BlockPos center = player.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-5, -2, -5), center.offset(5, 2, 5))) {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof BonemealableBlock plant && plant.isValidBonemealTarget(level, pos, state, false)
                        && plant.isBonemealSuccess(level, level.random, pos, state)) {
                    plant.performBonemeal(level, level.random, pos.immutable(), state);
                }
            }
        }
        updateSoldierRegeneration(player, type);
        updateSoldierStamina(player, type);
    }

    @SubscribeEvent
    public static void onDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        FractionType type = fraction(player);
        if (type == FractionType.MEDIC) event.setAmount(event.getAmount() * 1.3f);
        if (type == FractionType.SOLDIER) event.setAmount(event.getAmount() * 0.8f);
    }

    @SubscribeEvent
    public static void onHeadDamage(FirstAidLivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || fraction(player) != FractionType.SURVIVOR
                || event.getBeforeDamage().HEAD.currentHealth <= event.getAfterDamage().HEAD.currentHealth
                || player.getRandom().nextFloat() >= 0.05f) return;
        ItemStack map = PersonalMapItem.findInInventory(player);
        if (map.isEmpty()) return;
        PersonalMapItem.clearData(map);
        MazeMapNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CClearMapPacket());
        player.displayClientMessage(Component.literal("§cПрогресс карты потерян из-за ранения головы"), true);
    }

    @SubscribeEvent
    public static void onUseFood(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player) || fraction(player) != FractionType.COOK) return;
        ItemStack stack = event.getItem();
        if (isUncookedFood(stack)) event.setCanceled(true);
    }

    public static boolean isUncookedFood(ItemStack stack) {
        return !stack.isEmpty() && stack.is(UNCOOKED_FOOD);
    }

    private static void updateSoldierRegeneration(Player player, FractionType type) {
        UUID id = player.getUUID();
        if (type != FractionType.SOLDIER || !player.isAlive() || player.getHealth() >= 10.0f) {
            SOLDIERS_BELOW_TEN_HEALTH.remove(id);
            return;
        }
        if (SOLDIERS_BELOW_TEN_HEALTH.add(id)) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
        }
    }

    private static void updateSoldierStamina(Player player, FractionType type) {
        Attribute stamina = ForgeRegistries.ATTRIBUTES.getValue(
                ResourceLocation.fromNamespaceAndPath("parcool", "max_stamina"));
        if (stamina == null) return;
        AttributeInstance instance = player.getAttribute(stamina);
        if (instance == null) return;
        if (type == FractionType.SOLDIER) {
            if (instance.getModifier(SOLDIER_STAMINA_ID) == null) {
                instance.addTransientModifier(new AttributeModifier(SOLDIER_STAMINA_ID,
                        "Soldier stamina", -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        } else {
            instance.removeModifier(SOLDIER_STAMINA_ID);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDoor(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        BlockState state = player.level().getBlockState(event.getPos());
        if (state.getBlock() == net.minecraft.world.level.block.Blocks.IRON_DOOR
                && fraction(player) == FractionType.SCIENTIST) {
            boolean open = state.getValue(DoorBlock.OPEN);
            ((DoorBlock) state.getBlock()).setOpen(player, player.level(), state, event.getPos(), !open);
            event.setCanceled(true);
            event.setUseBlock(Event.Result.DENY);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        } else if (net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock()) != null
                && net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock())
                .toString().equals("farmersdelight:cooking_pot") && fraction(player) != FractionType.COOK) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHarvest(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide || fraction(player) != FractionType.FARMER) return;
        BlockState state = event.getState();
        if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop
                && crop.isMaxAge(state) && player.getRandom().nextFloat() < 0.20f) {
            player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - 2));
            BlockPos pos = event.getPos();
            if (player.level() instanceof ServerLevel level) {
                net.minecraft.world.level.block.Block.dropResources(state, level, pos.above(), null, player,
                        player.getMainHandItem());
            }
        }
    }
}
