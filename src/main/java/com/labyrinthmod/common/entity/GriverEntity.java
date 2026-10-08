package com.labyrinthmod.common.entity;

import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.generation.LabyrinthChunkGenerator;
import com.labyrinthmod.common.init.ModSounds;
import com.labyrinthmod.common.patrol.PatrolManager;
import com.labyrinthmod.common.util.ModLogger;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;


import java.util.*;

public class GriverEntity extends Animal implements GeoEntity {

    private static final EntityDataAccessor<Boolean> IS_POSSESSED =
            SynchedEntityData.defineId(GriverEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_PATROLLING =
            SynchedEntityData.defineId(GriverEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_SADDLED =
            SynchedEntityData.defineId(GriverEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> GROUND_PHASE =
            SynchedEntityData.defineId(GriverEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GROUND_PHASE_START =
            SynchedEntityData.defineId(GriverEntity.class, EntityDataSerializers.INT);
    public boolean isPatrolling() {
        return this.entityData.get(IS_PATROLLING);
    }




    // Флаги для синхронизации анимаций с клиентом


    private UUID possessingPlayerUUID = null;
    private boolean isReturningHome = false;
    private int attackDelayCounter = 0;

    // Добавьте с другими полями


    private boolean leavingPatrol = false;
    // Позиция спавнера — сюда возвращаемся при stop patrol
    private BlockPos homePos = null;

    private boolean forceChunkLoading = true;
    private int chunkLoadRadius = 3;
    private int chunkLoadCooldown = 0;
    private boolean isRunning = false;
    private List<BlockPos> lastMicroPath = new ArrayList<>();

    private int attackAnimationTimer = 0;
    private boolean shouldRestartAttackAnim = false;

    // Добавьте с другими полями (в начале класса)
    private int soundCooldown = 0;
    private static final int SOUND_COOLDOWN_TICKS = 180; // Звук каждые 2 секунды (20 тиков * 2)

    // Добавьте с другими полями
    private boolean isAttacking = false;
    private boolean isJumping = false;

    // Звуковые таймеры
    private int walkSoundCooldown = 0;
    private int runSoundCooldown = 0;
    private int alertSoundCooldown = 0;
    private static final int WALK_SOUND_DELAY = 25;  // Медленные шаги (каждые 25 тиков = 1.25 секунды)
    private static final int RUN_SOUND_DELAY = 20;    // Звук бега длится 1 секунду (20 тиков) — кладём кулдаун ровно по длине, чтобы не было перекрытий



    // Глобальная цель и фазы
    private BlockPos currentGlobalTarget = null;
    private boolean inDispersalPhase = false;
    private boolean returning = false;

    private LivingEntity forcedAttackTarget = null;
    private int forcedAttackTimeout = 0;
    private static final int FORCED_ATTACK_RANGE = 500;

    // Очередь из 5 запланированных целей (head = текущая)
    private final List<BlockPos> plannedTargets = new ArrayList<>();

    // Waypoint chain (высокоуровневый план через patrol points до plannedTargets[0])
    private List<BlockPos> waypointChain = null;
    private int waypointIdx = 0;

    // Micro-path (block-by-block от myPos до current waypoint)
    private List<BlockPos> microPath = null;
    private int microIdx = 0;
    private int microReplanCooldown = 0;

    // Детект "не продвигаюсь к цели"
    private int noProgressTicks = 0;
    private double lastDistanceToTarget = Double.MAX_VALUE;
    private int tickSinceLastNavUpdate = 0;

    private static final double WALK_SPEED = 1.2;          // множитель для navigation (0.25 * 1.2)
    private static final double REACH_DISTANCE = 2.5;      // считаем что достигли цели
    private static final int NO_PROGRESS_LIMIT = 60;       // 3 сек без продвижения = replan
    private static final int REPLAN_INTERVAL = 40;         // обновлять путь раз в 2 сек
    private static final int MAX_REPLAN_FAILS = 3;         // недостижимых попыток → новая цель

    private int replanFails = 0;
    private int proximityCheckCooldown = 0;
    private int proximityGracePeriod = 0;
    private int refillRetryCooldown = 0;

    // Stuck recovery
    private BlockPos stuckCheckLastPos = null;
    private int stuckTicks = 0;
    private int recoveryAttempts = 0;
    private int successfulMoveTicks = 0;
    private int noclipTicks = 0;
    private int naturalPatrolStationaryTicks = 0;

    // Create removes moving blocks from the vanilla world and renders/collides them
    // through an entity. Vanilla and our block pathfinder therefore see air. Keep a
    // short lived set of world cells occupied by every nearby Create contraption.
    private final Set<Long> createObstacleCells = new HashSet<>();
    private int createObstacleCacheTick = Integer.MIN_VALUE;

    private int ridingSoundCooldown = 0;
    private static final int RIDING_SOUND_DELAY = 10;

    private static final double RETURN_SPEED_MULTIPLIER = 3.0;
    private boolean returningHome = false;

    // Добавьте с другими полями в начале класса
    private int backwardSoundCooldown = 0;
    private static final int BACKWARD_SOUND_DELAY = 15; // Звук при движении назад

    private final java.util.Set<net.minecraft.server.level.ServerChunkCache> loadedChunks = new java.util.HashSet<>();


    // Добавить с другими полями (примерно строка 70-100)
    private LivingEntity lastHurtByMob = null;
    private int hurtCooldown = 0;
    private boolean isChasingPlayer = false;      // Режим преследования игрока
    private LivingEntity currentTarget = null;    // Текущая цель для преследования
    private int chaseTimeout = 0;                 // Таймаут преследования (если цель потеряна)
    private List<BlockPos> chaseMicroPath = Collections.emptyList();
    private int chaseMicroIndex = 0;
    private int chaseReplanTicks = 0;
    private BlockPos spawnerBlockPos = null;
    private boolean naturalNightSpawn = false;
    private BlockPos dawnRetreatTarget = null;
    private int dawnRetreatTicks = 0;
    private List<BlockPos> dawnRetreatPath = Collections.emptyList();
    private int dawnRetreatPathIndex = 0;
    private int dawnRetreatReplanTicks = 0;
    private int dawnRetreatStationaryTicks = 0;
    private BlockPos dawnRetreatLastPos = null;
    private List<BlockPos> pendingNightRoute = Collections.emptyList();
    private static final int GROUND_NONE = 0;
    private static final int GROUND_EMERGING = 1;
    private static final int GROUND_BURROWING = 2;
    private static final int GROUND_ANIMATION_TICKS = 40;

    /** GeckoLib instance cache — на каждой entity свой, иначе анимации игроков смешиваются. */
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** Имена анимаций — соответствуют ключам в animations/griver.animation.json. */
    private static final RawAnimation RUN_ANIM    = RawAnimation.begin().thenLoop("griver_run");
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay("griver_attack");
    private static final RawAnimation JUMP_ANIM   = RawAnimation.begin().thenPlay("griver_jump");

    /** Контроллер анимаций - определяет когда какую анимацию включить */
    private final GriverAnimationController animationController;

    /** Флаг для синхронизации атаки с клиентом */
    private boolean needsSyncAttack = false;

    /** Флаг для синхронизации прыжка с клиентом */
    private boolean needsSyncJump = false;

    /** Таймер для ограничения частоты синхронизации */
    private int syncCooldown = 0;

    private final Random rng = new Random();

    public GriverEntity(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);

        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.LAVA, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.DOOR_WOOD_CLOSED, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.DOOR_IRON_CLOSED, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.FENCE, 0.0F);

        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);

        GriverPathNavigation navigation = (GriverPathNavigation) this.getNavigation();
        navigation.setCanOpenDoors(true);
        navigation.setCanPassDoors(true);
        navigation.setAvoidSun(false);
        navigation.setCanFloat(true);

        // GeckoLib больше не требует ручной инициализации AnimationSystem — всё
        // зарегистрировано через registerControllers() ниже. Просто refreshDimensions.
        this.refreshDimensions();

        // Создаём контроллер анимаций
        this.animationController = new GriverAnimationController(this);

        // Настраиваем callback'и
        this.animationController.setMovingListeners(
                () -> playWalkAnimation(),
                () -> {}
        );
        this.animationController.setAttackListener(() -> startAttackAnimation());
        this.animationController.setJumpListeners(
                () -> {
                    playJumpAnimation();
                    needsSyncJump = true;
                },
                () -> {}
        );
        this.setSaddled(true);
    }

    public void setSpawnerBlockPos(BlockPos pos) {
        this.spawnerBlockPos = pos;
    }

    public BlockPos getSpawnerBlockPos() {
        return spawnerBlockPos;
    }


    @Override
    protected PathNavigation createNavigation(Level level) {
        return new GriverPathNavigation(this, level);
    }

    /** Базовая скорость гривера. Если поднять выше 0.5 — pathfinding и chunk
     *  forcing могут не успевать. Поднято с 0.25 до 0.5 (×2) по запросу. */
    public static final double GRIVER_BASE_SPEED = 0.4D;

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                .add(Attributes.MOVEMENT_SPEED, GRIVER_BASE_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        // Combat and movement are driven by the state machine in tick(). Running the
        // old attack Goal at the same time lets vanilla navigation stop or replace the
        // custom A* route, which produced one-step patrols and visible jerks.
        this.goalSelector.addGoal(2, new SharedPatrolGoal());
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_POSSESSED, false);
        this.entityData.define(IS_PATROLLING, false);
        this.entityData.define(IS_SADDLED, false);
        this.entityData.define(GROUND_PHASE, GROUND_NONE);
        this.entityData.define(GROUND_PHASE_START, 0);
    }

    public boolean isNaturalNightSpawn() {
        return naturalNightSpawn;
    }

    public boolean canReachNaturalPoint(BlockPos target) {
        if (target == null || this.level().isClientSide) return false;
        List<BlockPos> path = buildMicroPath(this.blockPosition(), target);
        return path != null && !path.isEmpty();
    }

    public float getGroundAnimationOffset(float partialTick) {
        int phase = this.entityData.get(GROUND_PHASE);
        if (phase == GROUND_NONE) return 0.0F;
        float progress = Math.min(1.0F, Math.max(0.0F,
                (this.tickCount - this.entityData.get(GROUND_PHASE_START) + partialTick) / GROUND_ANIMATION_TICKS));
        return phase == GROUND_EMERGING ? -3.2F * (1.0F - progress) : -3.2F * progress;
    }

    public void beginNaturalNightPatrol(List<BlockPos> route) {
        if (this.level().isClientSide || route == null || route.isEmpty()) return;
        naturalNightSpawn = true;
        pendingNightRoute = filterReachableNaturalRoute(route);
        if (pendingNightRoute.isEmpty()) {
            pendingNightRoute = createNextNaturalNightRoute();
        }
        this.entityData.set(GROUND_PHASE, GROUND_EMERGING);
        this.entityData.set(GROUND_PHASE_START, this.tickCount);
        this.setInvulnerable(true);
        this.getNavigation().stop();
        this.setTarget(null);
    }

    public void beginDawnRetreat(BlockPos retreatTarget) {
        if (this.level().isClientSide || !naturalNightSpawn) return;
        dawnRetreatTarget = retreatTarget;
        dawnRetreatTicks = 0;
        dawnRetreatPath = Collections.emptyList();
        dawnRetreatPathIndex = 0;
        dawnRetreatReplanTicks = 20;
        dawnRetreatStationaryTicks = 0;
        dawnRetreatLastPos = this.blockPosition();
        pendingNightRoute = Collections.emptyList();
        plannedTargets.clear();
        waypointChain = null;
        microPath = null;
        microIdx = 0;
        this.entityData.set(IS_PATROLLING, false);
        this.setTarget(null);
        this.currentTarget = null;
        this.forcedAttackTarget = null;
        this.lastHurtByMob = null;
        this.getNavigation().stop();
        dawnRetreatPath = buildMicroPath(this.blockPosition(), retreatTarget);
        if (dawnRetreatPath == null || dawnRetreatPath.isEmpty()) {
            beginImmediateBurrow();
        }
    }

    public void beginImmediateBurrow() {
        if (this.level().isClientSide || !naturalNightSpawn) return;
        dawnRetreatTarget = null;
        pendingNightRoute = Collections.emptyList();
        plannedTargets.clear();
        waypointChain = null;
        microPath = null;
        this.entityData.set(IS_PATROLLING, false);
        this.setTarget(null);
        this.currentTarget = null;
        this.forcedAttackTarget = null;
        this.lastHurtByMob = null;
        this.entityData.set(GROUND_PHASE, GROUND_BURROWING);
        this.entityData.set(GROUND_PHASE_START, this.tickCount);
        this.setInvulnerable(true);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
    }

    private boolean tickNaturalNightLifecycle() {
        int phase = this.entityData.get(GROUND_PHASE);
        if (phase == GROUND_EMERGING) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            spawnGroundParticles();
            if (this.tickCount - this.entityData.get(GROUND_PHASE_START) >= GROUND_ANIMATION_TICKS) {
                this.entityData.set(GROUND_PHASE, GROUND_NONE);
                this.setInvulnerable(false);
                if (!pendingNightRoute.isEmpty()) {
                    applyExternalPlan(pendingNightRoute);
                    pendingNightRoute = Collections.emptyList();
                }
            }
            return true;
        }
        if (phase == GROUND_BURROWING) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            spawnGroundParticles();
            if (this.tickCount - this.entityData.get(GROUND_PHASE_START) >= GROUND_ANIMATION_TICKS) {
                this.discard();
            }
            return true;
        }
        if (dawnRetreatTarget != null) {
            dawnRetreatTicks++;
            this.setTarget(null);
            this.currentTarget = null;
            if (this.distanceToSqr(Vec3.atCenterOf(dawnRetreatTarget)) <= 4.0D || dawnRetreatTicks >= 1200) {
                this.entityData.set(GROUND_PHASE, GROUND_BURROWING);
                this.entityData.set(GROUND_PHASE_START, this.tickCount);
                this.setInvulnerable(true);
                this.getNavigation().stop();
            } else {
                if (this.tickCount % 10 == 0) {
                    BlockPos current = this.blockPosition();
                    if (dawnRetreatLastPos != null && current.equals(dawnRetreatLastPos)) {
                        dawnRetreatStationaryTicks += 10;
                    } else {
                        dawnRetreatStationaryTicks = 0;
                    }
                    dawnRetreatLastPos = current;
                    if (dawnRetreatStationaryTicks >= 100) {
                        beginImmediateBurrow();
                        return true;
                    }
                }
                this.getNavigation().stop();
                if (dawnRetreatReplanTicks-- <= 0
                        || dawnRetreatPathIndex >= dawnRetreatPath.size()) {
                    dawnRetreatPath = buildMicroPath(this.blockPosition(), dawnRetreatTarget);
                    dawnRetreatPathIndex = 0;
                    dawnRetreatReplanTicks = 20;
                }
                if (dawnRetreatPathIndex < dawnRetreatPath.size()) {
                    BlockPos step = dawnRetreatPath.get(dawnRetreatPathIndex);
                    if (!canStandAt(step)) {
                        dawnRetreatPath = Collections.emptyList();
                        dawnRetreatReplanTicks = 0;
                        this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
                    } else {
                        double tx = step.getX() + 0.5D;
                        double tz = step.getZ() + 0.5D;
                        double dx = tx - this.getX();
                        double dz = tz - this.getZ();
                        if (dx * dx + dz * dz < 0.12D) dawnRetreatPathIndex++;
                        else moveTowards(tx, step.getY(), tz, 1.35D);
                    }
                }
            }
            return true;
        }
        return false;
    }

    private void spawnGroundParticles() {
        if (!(this.level() instanceof ServerLevel server) || this.tickCount % 2 != 0) return;
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                        this.level().getBlockState(this.blockPosition().below())),
                this.getX(), this.getY() + 0.15D, this.getZ(),
                8, 0.55D, 0.12D, 0.55D, 0.08D);
    }
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);

        // Устанавливаем седло при спавне
        this.setSaddled(true);

        return result;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(0.5F, 3.0F);
    }

    @Override
    public int getMaxHeadYRot() {
        return 30;
    }

    // ========== ЕЗДОВОЙ МОБ ==========

    public boolean canBeControlledByRider() {
        return true;
    }

    public boolean canBeRidden(Entity entity) {
        return true;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity passenger = this.getFirstPassenger();
        if (passenger instanceof LivingEntity) return (LivingEntity) passenger;
        return null;
    }


    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // Только операторы могут взаимодействовать с гривером
        if (!player.hasPermissions(2) && !player.isCreative()) {
            player.sendSystemMessage(Component.literal("§cУ вас нет прав на взаимодействие с гривером!"));
            return InteractionResult.FAIL;
        }

        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.getItem() == Items.SADDLE && !this.isSaddled()) {
            itemstack.shrink(1);
            this.setSaddled(true);
            player.sendSystemMessage(Component.literal("§aГривер оседлан!"));
            return InteractionResult.SUCCESS;
        }

        if (this.isSaddled() && !this.isVehicle()) {
            // Перед посадкой отключаем всё ИИ
            disableAIForRiding();
            player.startRiding(this);
            player.sendSystemMessage(Component.literal("§aВы сели на гривера!"));
            return InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, hand);
    }

    public boolean isSaddled() {
        return this.entityData.get(IS_SADDLED);
    }

    public void setSaddled(boolean saddled) {
        this.entityData.set(IS_SADDLED, saddled);
    }

    public void setHomePos(BlockPos pos) {
        this.homePos = pos;
    }

    public BlockPos getHomePos() {
        return homePos;
    }

    // ========== ОБЩИЙ ПАТРУЛЬ ==========

    /**
     * Принудительно устанавливает план от внешнего планировщика (глобальный планировщик).
     */
    public void applyExternalPlan(List<BlockPos> plan) {
        if (this.level().isClientSide) return;
        if (plan == null || plan.isEmpty()) return;

        // КРИТИЧНО: сбрасываем все combat/return-флаги. Иначе если у гривера на момент
        // старта патруля было currentTarget/isAttacking/returning (а на dedicated server'е
        // с активными игроками это норма — гривер их сразу цепляет), tick проходит
        // через attack/return ветки и НЕ доходит до patrol-блока. План применён, но
        // никогда не выполняется. В singleplayer'е этого не видно — в creative-mode
        // одиночке цели не появляются.
        this.setTarget(null);
        this.currentTarget = null;
        this.isChasingPlayer = false;
        this.isAttacking = false;
        this.attackAnimationTimer = 0;
        this.forcedAttackTarget = null;
        this.forcedAttackTimeout = 0;
        this.returning = false;
        this.returningHome = false;
        this.isReturningHome = false;
        this.lastHurtByMob = null;
        this.hurtCooldown = 0;
        // Сбрасываем звуковые кулдауны — иначе после первого патруля они застревают
        // на высоких значениях (run/walk decrement только когда условие speed
        // совпадает; во время return домой условия меняются и кулдауны зависают).
        this.walkSoundCooldown = 0;
        this.runSoundCooldown = 0;
        this.backwardSoundCooldown = 0;
        this.getNavigation().stop();

        this.entityData.set(IS_PATROLLING, true);
        plannedTargets.clear();
        plannedTargets.addAll(plan);

        PatrolManager m = PatrolManager.get(this.level());
        if (m != null) m.setPlannedTargets(this.getUUID(), plannedTargets);

        BlockPos head = plannedTargets.get(0);
        if (m != null) m.setGriverTarget(this.getUUID(), head);
        inDispersalPhase = false;
        assignNewTarget(head);

        ModLogger.patrol("external-plan", "griver=" + this.getUUID().toString().substring(0, 8)
                + " size=" + plan.size() + " head=" + head);
    }

    public void joinGlobalPatrol() {
        if (this.level().isClientSide) return;

        PatrolManager m = PatrolManager.get(this.level());
        if (m == null || m.getPatrolPoints().isEmpty()) return;

        // ПРИНУДИТЕЛЬНАЯ ОЧИСТКА ПЕРЕД СТАРТОМ
        this.currentGlobalTarget = null;
        this.plannedTargets.clear();
        this.waypointChain = null;
        this.microPath = null;
        this.returning = false;
        this.returningHome = false;
        this.isReturningHome = false;

        this.entityData.set(IS_PATROLLING, true);
        ModLogger.patrol("join", "griver=" + this.getUUID().toString().substring(0, 8)
                + " pos=" + this.blockPosition());

        BlockPos spawn = m.getSpawnPoint();
        if (spawn == null) spawn = this.blockPosition();

        Map<UUID, BlockPos> dispersals = new HashMap<>();
        for (var other : gatherOtherGrivers()) {
            BlockPos d = m.getDispersalTarget(other.getUUID());
            if (d != null) dispersals.put(other.getUUID(), d);
        }

        BlockPos dispersal = m.pickDispersalPoint(this.getUUID(), spawn, dispersals);
        plannedTargets.clear();
        if (dispersal != null) {
            m.setDispersalTarget(this.getUUID(), dispersal);
            inDispersalPhase = true;
            plannedTargets.add(dispersal);
        }
        refillPlannedTargets(m);
        if (!plannedTargets.isEmpty()) {
            BlockPos head = plannedTargets.get(0);
            m.setGriverTarget(this.getUUID(), head);
            assignNewTarget(head);
        }
    }


    public void leaveGlobalPatrol() {
        if (this.level().isClientSide) return;

        ModLogger.patrol("leave", "griver=" + this.getUUID().toString().substring(0, 8));

        this.entityData.set(IS_PATROLLING, false);
        inDispersalPhase = false;

        isChasingPlayer = false;
        currentTarget = null;
        setTarget(null);
        chaseTimeout = 0;

        getNavigation().stop();

        isReturningHome = true;
        returning = true;

        microPath = null;
        waypointChain = null;
        waypointIdx = 0;
        microIdx = 0;

        ModLogger.patrol("return-home-start", "griver=" + this.getUUID().toString().substring(0, 8)
                + " home=" + (homePos != null ? homePos : "null"));
    }

    /**
     * Текущий block-by-block путь (только оставшиеся шаги).
     */
    public List<BlockPos> getCurrentMicroPath() {
        if (microPath == null) return Collections.emptyList();
        List<BlockPos> out = new ArrayList<>();
        for (int i = microIdx; i < microPath.size(); i++) out.add(microPath.get(i));
        return out;
    }

    private void assignNewTarget(BlockPos target) {
        currentGlobalTarget = target;
        noProgressTicks = 0;
        lastDistanceToTarget = this.blockPosition().distSqr(target);
        replanFails = 0;
        tickSinceLastNavUpdate = 0;
        stuckTicks = 0;
        recoveryAttempts = 0;

        // Строим waypoint chain через граф patrol points
        PatrolManager m = PatrolManager.get(this.level());
        if (naturalNightSpawn) {
            // Natural routes are already checked segment by segment. Passing them
            // through the legacy patrol graph could insert an unreachable waypoint
            // and leave every freshly spawned griver idle until a riding reset.
            waypointChain = new ArrayList<>();
            waypointChain.add(target);
        } else if (m != null) {
            waypointChain = m.findWaypointChain(this.blockPosition(), target);
            if (waypointChain != null && !waypointChain.isEmpty()) {
                if (!waypointChain.get(waypointChain.size() - 1).equals(target)) {
                    waypointChain.add(target);
                }
            } else {
                waypointChain = new ArrayList<>();
                waypointChain.add(target);
            }
            // Резервируем всю цепочку — другие гриверы не смогут выбрать эти точки
            m.setReservedWaypoints(this.getUUID(), waypointChain);
        } else {
            waypointChain = new ArrayList<>();
            waypointChain.add(target);
        }
        waypointIdx = 0;
        startNavigateToCurrentWaypoint();
    }

    private BlockPos getCurrentWaypoint() {
        if (waypointChain == null || waypointIdx >= waypointChain.size()) return null;
        return waypointChain.get(waypointIdx);
    }

    private void startNavigateToCurrentWaypoint() {
        BlockPos wp = getCurrentWaypoint();
        if (wp != null) {
            microPath = null;
            microReplanCooldown = 0;
        }
    }

    private boolean startNavigateTo(BlockPos target) {
        this.getNavigation().stop();
        BlockPos from = this.blockPosition();
        microPath = buildMicroPath(from, target);
        microIdx = 0;
        microReplanCooldown = 0;
        if (microPath == null || microPath.isEmpty()) {
            ModLogger.pathFail(this.getUUID().toString(), from, target, "no-path");
            return false;
        }
        ModLogger.path(this.getUUID().toString(), from, target, microPath.size(), "");
        return true;
    }

    /**
     * Собственный A* block-by-block, только ортогональные шаги (без diagonals).
     * Это исключает срезание углов — гривер никогда не зацепится за стену.
     */
    /**
     * Собственный A* block-by-block, только ортогональные шаги (без diagonals).
     * Это исключает срезание углов — гривер никогда не зацепится за стену.
     * МОДИФИЦИРОВАНО: добавлена проверка дистанции до стены
     */
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
    private int microPathTimer = 0;
    private static final int MICRO_PATH_INTERVAL = 5; // Раз в 5 тиков

    private List<BlockPos> buildMicroPath(BlockPos start, BlockPos end) {
        refreshCreateObstacleCells();
        BlockPos goal = canStandAt(end) ? end.immutable() : snapToWalkable(end);
        if (goal == null) return Collections.emptyList();

        // Real orthogonal A*. The old implementation greedily stepped toward the
        // target and picked the first free neighbour at a wall, so it could oscillate
        // forever instead of going around that wall.
        PriorityQueue<MicroNode> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<Long, Double> best = new HashMap<>();
        BlockPos origin = start.immutable();
        open.add(new MicroNode(origin, null, 0.0D, heuristic(origin, goal)));
        best.put(origin.asLong(), 0.0D);

        int minX = Math.min(origin.getX(), goal.getX()) - 14;
        int maxX = Math.max(origin.getX(), goal.getX()) + 14;
        int minZ = Math.min(origin.getZ(), goal.getZ()) - 14;
        int maxZ = Math.max(origin.getZ(), goal.getZ()) + 14;
        int iterations = 0;

        while (!open.isEmpty() && iterations++ < 6000) {
            MicroNode current = open.poll();
            if (current.g > best.getOrDefault(current.pos.asLong(), Double.MAX_VALUE)) continue;
            if (current.pos.equals(goal)) {
                List<BlockPos> result = reconstructMicro(current);
                if (!result.isEmpty()) result.remove(0); // current cell is not a movement step
                lastMicroPath = result;
                return result;
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.pos.relative(direction);
                if (next.getX() < minX || next.getX() > maxX
                        || next.getZ() < minZ || next.getZ() > maxZ) continue;
                if (!canStandAt(next)) continue;

                double nextG = current.g + 1.0D + getWallPenalty(next) * 0.15D;
                long key = next.asLong();
                if (nextG >= best.getOrDefault(key, Double.MAX_VALUE)) continue;
                best.put(key, nextG);
                open.add(new MicroNode(next.immutable(), current, nextG,
                        nextG + heuristic(next, goal)));
            }
        }

        lastMicroPath = Collections.emptyList();
        return lastMicroPath;
    }

// ========== НОВЫЕ МЕТОДЫ ДЛЯ ПРОВЕРКИ СТЕН ==========

    /**
     * Проверяет, слишком ли близко клетка к стене.
     *
     * @param pos позиция для проверки
     * @return true если клетка слишком близко к стене
     */
    private boolean isTooCloseToWall(BlockPos pos) {
        int minDistanceFromWall = 1; // Минимальное расстояние до стены в блоках (1 = минимум 1 блок от стены)

        // Проверяем все 4 стороны на наличие стены
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(dir);
            if (!isPassable(neighbor)) {
                // Нашли стену рядом
                return true;
            }

            // Дополнительная проверка: диагональные углы
            Direction perpendicular = dir.getClockWise();
            BlockPos diagonal = pos.relative(dir).relative(perpendicular);
            if (!isPassable(diagonal)) {
                return true;
            }

            // Если хотим расстояние 2 блока от стены, проверяем через один блок
            if (minDistanceFromWall >= 2) {
                BlockPos twoSteps = neighbor.relative(dir);
                if (!isPassable(twoSteps)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Возвращает штраф за близость к стене (для A* pathfinding).
     * Чем ближе к стене, тем выше штраф — путь будет предпочитать клетки подальше от стен.
     *
     * @param pos позиция для проверки
     * @return штраф (0.0 - 5.0)
     */
    private double getWallPenalty(BlockPos pos) {
        double penalty = 0.0;
        int wallCount = 0;

        // Считаем количество стен вокруг клетки
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(dir);
            if (!isPassable(neighbor)) {
                wallCount++;
            }
        }

        // Штраф за каждую соседнюю стену
        switch (wallCount) {
            case 1 -> penalty = 0.5;   // Одна стена рядом
            case 2 -> penalty = 1.5;   // Угол или коридор
            case 3 -> penalty = 3.0;   // Тупик
            case 4 -> penalty = 5.0;   // Полностью окружён стенами (не должно достигаться)
        }

        return penalty;
    }

    /**
     * Альтернативная, более простая версия проверки.
     * Проверяет только непосредственных соседей.
     */
    private boolean hasWallAdjacent(BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(dir);
            if (!isPassable(neighbor)) {
                return true;
            }
        }
        return false;
    }

    private static long packKey(BlockPos p) {
        return (((long) p.getX() & 0x3FFFFFFL) << 38)
                | (((long) p.getZ() & 0x3FFFFFFL) << 12)
                | ((long) p.getY() & 0xFFFL);
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ()) + Math.abs(a.getY() - b.getY()) * 0.5;
    }

    private List<BlockPos> reconstructMicro(MicroNode end) {
        LinkedList<BlockPos> path = new LinkedList<>();
        MicroNode c = end;
        while (c != null) {
            path.addFirst(c.pos);
            c = c.parent;
        }
        return path;
    }

    /**
     * Клетка, на которой гривер физически может стоять: любая ненулевая коллизия пола + 3 пустых блока выше.
     * isTooCloseToWall намеренно НЕ применяется — лабиринт состоит из коридоров шириной 1-3 блока,
     * у каждой клетки стены с боков, иначе snapToWalkable никогда не найдёт валидную клетку и
     * buildMicroPath отвалится. Близость к стене учитывается как ШТРАФ в A*, не как блок.
     */
    private boolean canStandAt(BlockPos pos) {
        refreshCreateObstacleCells();
        if (isCreateObstacle(pos) || isCreateObstacle(pos.above()) || isCreateObstacle(pos.above(2))) {
            return false;
        }
        var below = this.level().getBlockState(pos.below());
        if (below.isAir() || !below.isSolid()) return false;
        if (!isPassable(pos)) return false;
        if (!isPassable(pos.above())) return false;
        return isPassable(pos.above(2));
    }

    private boolean isPassable(BlockPos pos) {
        refreshCreateObstacleCells();
        if (isCreateObstacle(pos)) return false;
        var state = this.level().getBlockState(pos);
        if (state.isAir()) return true;
        if (state.is(BlockTags.LEAVES)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(BlockTags.FENCES)
                || state.is(BlockTags.WALLS)) {
            return false;
        }
        return state.getCollisionShape(this.level(), pos).isEmpty();
    }

    private boolean isCreateObstacle(BlockPos pos) {
        return createObstacleCells.contains(pos.asLong());
    }

    private void refreshCreateObstacleCells() {
        if (this.level().isClientSide) return;
        if (createObstacleCacheTick != Integer.MIN_VALUE
                && this.tickCount - createObstacleCacheTick < 3) return;
        // Contraptions are marked with a generous body margin, so three ticks of
        // caching remain safe while avoiding thousands of transforms every tick.
        createObstacleCacheTick = this.tickCount;
        createObstacleCells.clear();
        AABB search = this.getBoundingBox().inflate(96.0D, 24.0D, 96.0D);
        List<AbstractContraptionEntity> contraptions = this.level().getEntitiesOfClass(
                AbstractContraptionEntity.class, search,
                e -> e.isAlive() && e.collisionEnabled() && e.getContraption() != null);

        for (AbstractContraptionEntity contraptionEntity : contraptions) {
            for (var entry : contraptionEntity.getContraption().getBlocks().entrySet()) {
                var info = entry.getValue();
                if (info.state().isAir()
                        || info.state().getCollisionShape(this.level(), BlockPos.ZERO).isEmpty()) continue;
                Vec3 center = contraptionEntity.toGlobalVector(Vec3.atCenterOf(entry.getKey()), 1.0F);

                // Mark only cells actually overlapped by the moving block. Expanding
                // this to neighbouring cells closes one-block and three-block maze
                // corridors completely, leaving the griver with no first step.
                // A fractional moving block is naturally present in two cells while
                // crossing their boundary.
                int minX = (int) Math.floor(center.x - 0.499D);
                int maxX = (int) Math.floor(center.x + 0.499D);
                int minY = (int) Math.floor(center.y - 0.499D);
                int maxY = (int) Math.floor(center.y + 0.499D);
                int minZ = (int) Math.floor(center.z - 0.499D);
                int maxZ = (int) Math.floor(center.z + 0.499D);
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            createObstacleCells.add(BlockPos.asLong(x, y, z));
                        }
                    }
                }
            }
        }
    }

    /**
     * Находит ближайшую walkable клетку вокруг позиции (включая +-1 по Y).
     */
    private BlockPos snapToWalkable(BlockPos p) {
        if (canStandAt(p)) return p;
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int dy = 0; dy <= 2; dy++) {
                        for (int s : new int[]{1, -1}) {
                            if (dy == 0 && s == -1) continue;
                            BlockPos cand = p.offset(dx, dy * s, dz);
                            if (canStandAt(cand)) return cand;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isInExclusion(BlockPos p, List<PatrolManager.ExclusionZone> zones) {
        for (var z : zones) if (z.contains(p)) return true;
        return false;
    }

    private static boolean isInsideAnyExclusion(BlockPos p, List<PatrolManager.ExclusionZone> zones) {
        return isInExclusion(p, zones);
    }

    /**
     * Кольцевой поиск ближайшей walkable клетки ВНЕ всех exclusion-зон.
     * radius — макс. расстояние в блоках по осям X/Z.
     */
    private BlockPos findNearestNonZoneCell(BlockPos from, List<PatrolManager.ExclusionZone> zones, int radius) {
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int dy = 0; dy <= 2; dy++) {
                        for (int s : new int[]{1, -1}) {
                            if (dy == 0 && s == -1) continue;
                            BlockPos cand = from.offset(dx, dy * s, dz);
                            if (isInExclusion(cand, zones)) continue;
                            if (canStandAt(cand)) return cand;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static class MicroNode {
        final BlockPos pos;
        final MicroNode parent;
        final double g, f;

        MicroNode(BlockPos pos, MicroNode parent, double g, double f) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.f = f;
        }
    }

    /**
     * Ищет достижимую клетку в радиусе вокруг цели, если сама цель недостижима.
     */
    private BlockPos findNearbyReachable(BlockPos target) {
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos p = target.offset(dx, 0, dz);
                    if (this.level().getBlockState(p).isAir()
                            && this.level().getBlockState(p.above()).isAir()
                            && !this.level().getBlockState(p.below()).isAir()) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    private void pickNewRegularTarget(PatrolManager m) {
        inDispersalPhase = false;
        // Восполняем очередь до PLAN_LENGTH
        refillPlannedTargets(m);
        if (plannedTargets.isEmpty()) return;
        BlockPos head = plannedTargets.get(0);
        m.setGriverTarget(this.getUUID(), head);
        assignNewTarget(head);
    }

    private void pickNewNaturalNightTarget(PatrolManager manager) {
        if (plannedTargets.isEmpty()) {
            plannedTargets.addAll(createNextNaturalNightRoute());
        }
        if (plannedTargets.isEmpty()) {
            currentGlobalTarget = null;
            refillRetryCooldown = 20;
            return;
        }
        manager.setPlannedTargets(this.getUUID(), plannedTargets);
        BlockPos next = plannedTargets.get(0);
        manager.setGriverTarget(this.getUUID(), next);
        assignNewTarget(next);
    }

    private void regenerateNaturalNightRoute(PatrolManager manager, String reason) {
        manager.clearGriverTarget(this.getUUID());
        plannedTargets.clear();
        currentGlobalTarget = null;
        waypointChain = null;
        waypointIdx = 0;
        microPath = null;
        microIdx = 0;
        microReplanCooldown = 0;
        stuckTicks = 0;
        recoveryAttempts = 0;
        successfulMoveTicks = 0;
        naturalPatrolStationaryTicks = 0;
        this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        ModLogger.patrol("natural-route-regenerate", "griver="
                + this.getUUID().toString().substring(0, 8) + " reason=" + reason);
        pickNewNaturalNightTarget(manager);
    }

    private List<BlockPos> createNextNaturalNightRoute() {
        if (!(this.level() instanceof ServerLevel server)) return Collections.emptyList();
        int wanted = 4 + this.random.nextInt(4);
        List<BlockPos> route = new ArrayList<>(wanted);
        BlockPos center = this.blockPosition();
        int failures = 0;
        while (route.size() < wanted && failures < 80) {
            BlockPos point = LabyrinthChunkGenerator.findRandomMainMazeCorridor(
                    this.random, center, 25, 1);
            if (point == null || !server.hasChunkAt(point) || !canStandAt(point)
                    || point.distSqr(center) < 36.0D || route.contains(point)) {
                failures++;
                continue;
            }
            List<BlockPos> candidatePath = buildMicroPath(center, point);
            if (candidatePath == null || candidatePath.isEmpty()) {
                failures++;
                continue;
            }
            route.add(point);
            center = point;
        }
        ModLogger.patrol("natural-route-refresh", "griver="
                + this.getUUID().toString().substring(0, 8) + " size=" + route.size());
        return route;
    }

    private List<BlockPos> filterReachableNaturalRoute(List<BlockPos> route) {
        List<BlockPos> reachable = new ArrayList<>();
        BlockPos from = this.blockPosition();
        for (BlockPos point : route) {
            if (point == null || !canStandAt(point)) continue;
            List<BlockPos> path = buildMicroPath(from, point);
            if (path == null || path.isEmpty()) continue;
            reachable.add(point.immutable());
            from = point;
        }
        return reachable;
    }

    /**
     * Добирает plannedTargets до PLAN_LENGTH с учётом других гриверов и своих уже запланированных.
     */
    private void refillPlannedTargets(PatrolManager m) {
        // Позиции ВСЕХ гриверов (включая далёких) для корректного расчёта Voronoi-зон
        Map<UUID, BlockPos> others = new HashMap<>();
        AABB big = new AABB(-30000000, -1000, -30000000, 30000000, 1000, 30000000);
        for (var e : this.level().getEntities(this, big)) {
            if (e instanceof GriverEntity g && g != this) {
                others.put(g.getUUID(), g.blockPosition());
            }
        }

        int sizeBefore = plannedTargets.size();
        BlockPos referencePos = plannedTargets.isEmpty() ? this.blockPosition()
                : plannedTargets.get(plannedTargets.size() - 1);

        int attempts = 0;
        while (plannedTargets.size() < PatrolManager.PLAN_LENGTH && attempts < 30) {
            attempts++;
            Set<BlockPos> alreadyMine = new HashSet<>(plannedTargets);
            // STRICT всегда — никакого fallback на близкие точки
            BlockPos next = m.pickRandomPoint(this.getUUID(), referencePos, others, alreadyMine, rng, true);
            if (next == null) break;
            if (plannedTargets.contains(next)) continue;
            plannedTargets.add(next);
            referencePos = next;
        }
        m.setPlannedTargets(this.getUUID(), plannedTargets);

        ModLogger.patrol("refill", "griver=" + this.getUUID().toString().substring(0, 8)
                + " before=" + sizeBefore + " after=" + plannedTargets.size()
                + " attempts=" + attempts);
    }

    /**
     * Телепорт на N шагов вперёд по microPath. Возвращает true если удалось.
     */
    private boolean teleportAlongPath(int steps) {
        if (microPath == null || microPath.isEmpty()) return false;
        int targetIdx = Math.min(microIdx + steps, microPath.size() - 1);
        if (targetIdx <= microIdx) return false;
        BlockPos tp = microPath.get(targetIdx);
        if (!canStandAt(tp)) return false;
        this.teleportTo(tp.getX() + 0.5, tp.getY(), tp.getZ() + 0.5);
        microIdx = targetIdx + 1;
        return true;
    }

    private void nudgeTowardsTarget(BlockPos target) {
        if (target == null) return;
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) return;
        double nx = dx / len, nz = dz / len;

        // Пробуем несколько позиций: 2 блока к цели, +/- 45°, +/- 90°, назад
        double[] angles = {0, Math.PI / 4, -Math.PI / 4, Math.PI / 2, -Math.PI / 2, Math.PI};
        for (double a : angles) {
            double cos = Math.cos(a), sin = Math.sin(a);
            double rx = nx * cos - nz * sin;
            double rz = nx * sin + nz * cos;
            for (int dist = 2; dist <= 3; dist++) {
                int tx = (int) Math.floor(this.getX() + rx * dist);
                int tz = (int) Math.floor(this.getZ() + rz * dist);
                for (int dy = -1; dy <= 1; dy++) {
                    int ty = (int) Math.floor(this.getY()) + dy;
                    BlockPos tp = new BlockPos(tx, ty, tz);
                    if (isFreeForGriver(tp)) {
                        this.teleportTo(tx + 0.5, ty, tz + 0.5);
                        this.getNavigation().stop();
                        startNavigateTo(target);
                        return;
                    }
                }
            }
        }
    }

    private boolean isFreeForGriver(BlockPos p) {
        return !this.level().getBlockState(p.below()).isAir()
                && this.level().getBlockState(p).isAir()
                && this.level().getBlockState(p.above()).isAir()
                && this.level().getBlockState(p.above(2)).isAir();
    }

    private GriverEntity findClosestGriverTooClose(PatrolManager m) {
        double minSq = (double) m.getMinDistanceBetweenGrivers() * m.getMinDistanceBetweenGrivers();
        GriverEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (GriverEntity other : gatherOtherGrivers()) {
            double dx = other.getX() - this.getX();
            double dz = other.getZ() - this.getZ();
            double d = dx * dx + dz * dz;
            if (d < minSq && d < closestDist) {
                closestDist = d;
                closest = other;
            }
        }
        return closest;
    }

    private List<GriverEntity> gatherOtherGrivers() {
        List<GriverEntity> result = new ArrayList<>();
        AABB big = new AABB(-30000000, -1000, -30000000, 30000000, 1000, 30000000);
        for (var e : this.level().getEntities(this, big)) {
            if (e instanceof GriverEntity g && g != this) result.add(g);
        }
        return result;
    }

    private void onReachedTarget(PatrolManager m) {
        if (currentGlobalTarget != null) {
            m.recordVisit(this.getUUID(), currentGlobalTarget);
        }
        if (naturalNightSpawn && dawnRetreatTarget == null) {
            if (!plannedTargets.isEmpty()) plannedTargets.remove(0);
            pickNewNaturalNightTarget(m);
            return;
        }
        if (inDispersalPhase) {
            m.clearDispersalTarget(this.getUUID());
            inDispersalPhase = false;
        } else {
            m.clearGriverTarget(this.getUUID());
        }
        // Pop head из очереди — мы её достигли
        if (!plannedTargets.isEmpty()) plannedTargets.remove(0);
        // Refill + берём новую head как target
        pickNewRegularTarget(m);
    }

    // ========== TICK ==========

    @Override
    public void tick() {
        super.tick();

        // ========== ДЕКРЕМЕНТ ЗВУКОВЫХ КУЛДАУНОВ ==========
        // Раньше декремент был ВНУТРИ playRunSound/playWalkSound — если функция в каком-то
        // тике не вызывалась (например условие speed не совпадает во время return-home),
        // кулдаун замораживался → потом, когда griver снова начинал двигаться, кулдаун
        // ещё был положительным → звук не играл. Делаем декремент безусловно каждый тик.
        if (!this.level().isClientSide) {
            if (walkSoundCooldown > 0) walkSoundCooldown--;
            if (runSoundCooldown > 0) runSoundCooldown--;
            if (backwardSoundCooldown > 0) backwardSoundCooldown--;
            if (ridingSoundCooldown > 0) ridingSoundCooldown--;
            if (alertSoundCooldown > 0) alertSoundCooldown--;
        }

        // ========== ОБНОВЛЕНИЕ ТАЙМЕРА АТАКИ ==========
        if (attackAnimationTimer > 0) {
            attackAnimationTimer--;
            if (attackAnimationTimer == 0) {
                isAttacking = false;
                // НЕ форсим NULL_ANIMATION_SYNTETIC — пусть следующий per-tick блок
                // (tick 937 / rideTick 2017 / travel 1825) сам выберет правильную
                // анимацию (RUN / WALK / IDLE) в зависимости от движения. Иначе
                // атака резко обрывается на хвостовых кадрах.
            }
        }

        // Обновление задержки между атаками
        if (attackDelayCounter > 0) {
            attackDelayCounter--;
        }

        // GeckoLib: вся клиентская логика выбора walk/run/idle/jump перенесена в
        // animPredicate() ниже — он читает состояние entity (delta, onGround,
        // attackAnimationTimer, vehicle passenger) каждый кадр и сам решает анимацию.
        // Здесь раньше был блок play*Animation()-вызовов, теперь не нужен.

        // GeckoLib не требует ручного system.tick() — анимации тикают сами в рендер-цикле.

        // ========== КЛИЕНТСКАЯ ЧАСТЬ - ВОЗВРАТ ==========
        if (this.level().isClientSide) return;

        // ========== СЕРВЕРНАЯ ЛОГИКА ==========
        animationController.tick();

        if (naturalNightSpawn && tickNaturalNightLifecycle()) {
            return;
        }

        // ========== SAFETY: МОНИТОР СКОРОСТИ ==========
        // updateReturningLogic временно поднимает MOVEMENT_SPEED до 0.25*3=0.75 для
        // быстрого возврата домой и сбрасывает обратно ТОЛЬКО когда гривер уже дома.
        // Если возврат прерывается раньше (игрок сел верхом, атака, потеря home и
        // т.п.), speed залипает на 0.75 → AI потом бегает в 3 раза быстрее даже
        // после слезания. Здесь форсим базовое значение, если ни один returning-флаг
        // не активен.
        if (!returning && !returningHome && !isReturningHome) {
            var speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null && Math.abs(speedAttr.getBaseValue() - GRIVER_BASE_SPEED) > 0.001D) {
                speedAttr.setBaseValue(GRIVER_BASE_SPEED);
            }
        }

        // ========== ЗВУКИ ШАГОВ (НА СЕРВЕРЕ, СЛЫШНО ВСЕМ ВОКРУГ) ==========
        // Раньше блок был обернут в if (!this.isVehicle()) — из-за этого, когда
        // игрок ездил на гривере, шагов вообще не было (внутренние playRunSound /
        // playWalkSound уже учитывают наездника через player.zza).
        {
            double speed = this.getDeltaMovement().horizontalDistance();
            boolean isReturningFast = (isReturningHome || returning) && speed > 0.3;

            // Если есть наездник — определяем «двигается ли он» по input'у, не по
            // реальной скорости (он может быть прижат стеной но input есть).
            boolean riderMoving = false;
            if (this.isVehicle() && this.getControllingPassenger() instanceof Player rider) {
                riderMoving = rider.zza != 0 || rider.xxa != 0;
            }

            if (attackAnimationTimer == 0 && !isAttacking) {
                playBackwardSound();

                if (isReturningFast) {
                    playRunSound();
                } else if (speed > 0.2 || riderMoving) {
                    playRunSound();
                } else if (speed > 0.02) {
                    playWalkSound();
                }
            }
        }

        if (this.tickCount % 20 == 0) {
            forceLoadChunksAround();
        }

        // ========== ESCAPE FROM EXCLUSION ZONE ==========
        // Если гривер оказался внутри зоны иммунитета (заспавнен там, зону добавили
        // после, attack-pathfinding с пустым snapshot'ом завёл) — выкидываем его на
        // ближайшую walkable клетку ВНЕ зоны и сбрасываем агрессию.
        if (this.tickCount % 10 == 0) {
            PatrolManager pm = PatrolManager.get(this.level());
            if (pm != null) {
                BlockPos here = this.blockPosition();
                if (isInsideAnyExclusion(here, pm.getExclusionZones())) {
                    BlockPos out = findNearestNonZoneCell(here, pm.getExclusionZones(), 30);
                    if (out != null) {
                        ModLogger.patrol("zone-escape", "griver=" + this.getUUID().toString().substring(0, 8)
                                + " from=" + here + " to=" + out);
                        this.teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
                        this.setTarget(null);
                        this.currentTarget = null;
                        this.forcedAttackTarget = null;
                        this.isChasingPlayer = false;
                        this.getNavigation().stop();
                    }
                }
            }
        }

        // ========== ПРИОРИТЕТЫ ДЕЙСТВИЙ ==========

        // 1. Верхом на игроке
        if (isVehicle() && getFirstPassenger() instanceof Player) {
            if (getNavigation().isInProgress()) {
                getNavigation().stop();
            }
            return;
        }

        // 2. АТАКА — САМЫЙ ВЫСОКИЙ ПРИОРИТЕТ
        // Если есть активная цель — продолжаем атаковать
        if (currentTarget != null && currentTarget.isAlive()) {
            continueAttackingTarget();
            return;
        }

        // Если нет активной цели, но есть обидчик — начинаем атаку
        if (lastHurtByMob != null && lastHurtByMob.isAlive()
                && !isProtectedFromGriver(lastHurtByMob) && distanceTo(lastHurtByMob) < 30.0) {
            startAttackingTarget(lastHurtByMob);
            continueAttackingTarget();
            return;
        }

        // Ищем новую цель
        LivingEntity newTarget = findNewTarget();
        if (newTarget != null) {
            startAttackingTarget(newTarget);
            continueAttackingTarget();
            return;
        }

        // 3. Принудительная атака
        if (forcedAttackTimeout > 0 && forcedAttackTarget != null && forcedAttackTarget.isAlive()) {
            updateForcedAttack();
            return;
        }

        // 4. Обработка урона/обидчика
        if (hurtCooldown > 0) {
            hurtCooldown--;
            if (hurtCooldown <= 0) {
                lastHurtByMob = null;
            }
        }

        // 5. Возврат домой
        if ((isReturningHome || returning) && !isAttacking && attackAnimationTimer == 0) {
            updateReturningLogic();
            return;
        }

        // 6. Патрулирование (только если не атакуем и нет цели)
        PatrolManager m = PatrolManager.get(this.level());
        if (m != null && m.isGlobalPatrolActive() && !isVehicle() && !isReturningHome && !returning && forcedAttackTarget == null && !isAttacking && attackAnimationTimer == 0) {
            if (!isPatrolling()) {
                this.entityData.set(IS_PATROLLING, true);
                // A naturally spawned griver already owns a fixed all-night route.
                // Rejoining the legacy global patrol after every chase used to clear
                // that route and could leave it with no target after a single step.
                if (naturalNightSpawn) {
                    if (currentGlobalTarget == null) pickNewNaturalNightTarget(m);
                } else {
                    joinGlobalPatrol();
                }
            }
            updatePatrolLogic();
        } else if (!isVehicle() && !isReturningHome && !returning && forcedAttackTarget == null && !isAttacking && attackAnimationTimer == 0) {
            if (!isReturningHome() && homePos != null) {
                returningHome = true;
                returning = true;
            }
        }

        // 7. Обработка принудительной атаки
        if (forcedAttackTimeout > 0) {
            forcedAttackTimeout--;
            if (forcedAttackTimeout <= 0) {
                forcedAttackTarget = null;
            }
        }
    }

// ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private boolean canAttack() {
        return !isVehicle() && !isReturningHome && !returning && forcedAttackTarget == null;
    }

    private boolean isProtectedFromGriver(Entity entity) {
        if (!(entity instanceof Player player)) return false;
        return player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction() == FractionType.OPERATOR
                        || data.getFraction() == FractionType.IMPOSTER)
                .orElse(false);
    }

    private void updateForcedAttack() {
        if (forcedAttackTarget == null || !forcedAttackTarget.isAlive()) return;

        // Если цель в зоне иммунитета — отказываемся от преследования.
        PatrolManager pm = PatrolManager.get(this.level());
        if (pm != null && isInExclusion(forcedAttackTarget.blockPosition(), pm.getExclusionZones())) {
            forcedAttackTarget = null;
            forcedAttackTimeout = 0;
            return;
        }

        double distance = distanceTo(forcedAttackTarget);
        if (distance <= 500) {
            if (isPatrolling()) {
                this.entityData.set(IS_PATROLLING, false);
            }
            setTarget(forcedAttackTarget);
            getLookControl().setLookAt(forcedAttackTarget, 30.0F, 30.0F);
            if (distance <= 3.5) {
                getNavigation().stop();
                doHurtTarget(forcedAttackTarget);
            } else {
                if (getNavigation().isDone()) {
                    getNavigation().moveTo(forcedAttackTarget, 1.5);
                }
            }
        } else {
            forcedAttackTarget = null;
            forcedAttackTimeout = 0;
        }
    }

    private void updateCombatLogic() {
        if (currentTarget == null || !currentTarget.isAlive()) {
            currentTarget = null;
            isChasingPlayer = false;
            return;
        }

        // Если цель в зоне иммунитета — бросаем её.
        PatrolManager pm = PatrolManager.get(this.level());
        if (pm != null && isInExclusion(currentTarget.blockPosition(), pm.getExclusionZones())) {
            currentTarget = null;
            isChasingPlayer = false;
            setTarget(null);
            getNavigation().stop();
            return;
        }

        setTarget(currentTarget);
        getLookControl().setLookAt(currentTarget, 30.0F, 30.0F);
        double distance = distanceTo(currentTarget);

        if (distance <= 3.5) {
            getNavigation().stop();
            doHurtTarget(currentTarget);
        } else if (distance <= 30.0) {
            if (getNavigation().isDone()) {
                getNavigation().moveTo(currentTarget, WALK_SPEED);
            }
        } else {
            currentTarget = null;
            isChasingPlayer = false;
        }
    }


    private void updatePatrolLogic() {
        // НЕ ПРЕРЫВАЕМ ПАТРУЛЬ ЕСЛИ ИДЁТ АТАКА
        if (isAttacking || attackAnimationTimer > 0) {
            return;
        }

        PatrolManager m = PatrolManager.get(this.level());
        if (m == null) return;

        if (!m.isGlobalPatrolActive()) {
            leaveGlobalPatrol();
            return;
        }

        if (currentGlobalTarget == null) {
            if (naturalNightSpawn) {
                if (refillRetryCooldown > 0) refillRetryCooldown--;
                else pickNewNaturalNightTarget(m);
            } else {
                pickNewRegularTarget(m);
            }
            return;
        }

        if (refillRetryCooldown > 0) refillRetryCooldown--;
        if (!naturalNightSpawn && plannedTargets.size() < PatrolManager.PLAN_LENGTH && refillRetryCooldown <= 0) {
            refillRetryCooldown = 60;
            refillPlannedTargets(m);
        }

        BlockPos waypoint = getCurrentWaypoint();
        if (waypoint == null) {
            pickNewRegularTarget(m);
            return;
        }

        this.getNavigation().stop();

        boolean isFinalWaypoint = (waypointIdx >= waypointChain.size() - 1);

        if (microPath == null || microIdx >= microPath.size()) {
            if (microReplanCooldown > 0) {
                microReplanCooldown--;
            } else {
                microPath = buildMicroPath(this.blockPosition(), waypoint);
                microIdx = 0;
                microReplanCooldown = 20;
                if (microPath == null || microPath.isEmpty()) {
                    if (!isFinalWaypoint) {
                        waypointIdx++;
                        microPath = null;
                    } else {
                        rebuildChainToTarget();
                    }
                    return;
                }
            }
        }

        if (microPath != null && microIdx < microPath.size()) {
            BlockPos step = microPath.get(microIdx);
            double stepX = step.getX() + 0.5;
            double stepZ = step.getZ() + 0.5;

            double tx = stepX;
            double tz = stepZ;

            double dx = stepX - this.getX();
            double dz = stepZ - this.getZ();
            double horDistSq = dx * dx + dz * dz;

            if (horDistSq < 0.12D) {
                microIdx++;
                stuckTicks = 0;
                recoveryAttempts = 0;
                if (microIdx >= microPath.size()) {
                    if (isFinalWaypoint) {
                        onReachedTarget(m);
                    } else {
                        waypointIdx++;
                        microPath = null;
                        microReplanCooldown = 0;
                    }
                    return;
                }
            } else {
                // A Create contraption may have moved onto a previously valid path.
                // Stop before contact and rebuild against its current transform.
                refreshCreateObstacleCells();
                if (!canStandAt(step)) {
                    this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
                    microPath = null;
                    microReplanCooldown = 0;
                    return;
                }
                moveTowards(tx, step.getY(), tz);
            }
        }

        // Stuck detection
        if (this.tickCount % 10 == 0) {
            BlockPos cur = this.blockPosition();
            boolean moved = stuckCheckLastPos == null
                    || Math.abs(cur.getX() - stuckCheckLastPos.getX()) >= 1
                    || Math.abs(cur.getZ() - stuckCheckLastPos.getZ()) >= 1;
            if (moved) {
                naturalPatrolStationaryTicks = 0;
                successfulMoveTicks += 10;
                if (successfulMoveTicks >= 40) {
                    stuckTicks = 0;
                    recoveryAttempts = 0;
                }
            } else {
                if (naturalNightSpawn) naturalPatrolStationaryTicks += 10;
                successfulMoveTicks = 0;
                stuckTicks += 10;
            }
            stuckCheckLastPos = cur;

            if (naturalNightSpawn && naturalPatrolStationaryTicks >= 100) {
                regenerateNaturalNightRoute(m, "stationary-5s target=" + currentGlobalTarget);
                return;
            }

            if (stuckTicks >= 30 && recoveryAttempts == 0) {
                recoveryAttempts = 1;
                ModLogger.stuck(this.getUUID().toString(), this.blockPosition(), stuckTicks, 1,
                        "jump+replan target=" + waypoint);
                this.getJumpControl().jump();
                microPath = null;
                microReplanCooldown = 0;
                return;
            }
            if (stuckTicks >= 60 && recoveryAttempts == 1) {
                recoveryAttempts = 2;
                ModLogger.stuck(this.getUUID().toString(), this.blockPosition(), stuckTicks, 2,
                        "skip-waypoint final=" + isFinalWaypoint);
                if (!isFinalWaypoint) {
                    waypointIdx++;
                    microPath = null;
                } else {
                    rebuildChainToTarget();
                }
                stuckTicks = 0;
                return;
            }
            if (stuckTicks >= 100 && recoveryAttempts == 2) {
                recoveryAttempts = 3;
                ModLogger.stuck(this.getUUID().toString(), this.blockPosition(), stuckTicks, 3,
                        "rebuild-chain target=" + currentGlobalTarget);
                rebuildChainToTarget();
                stuckTicks = 0;
                return;
            }
            if (stuckTicks >= 140 && recoveryAttempts == 3) {
                recoveryAttempts = 4;
                noclipTicks = 40;
                ModLogger.stuck(this.getUUID().toString(), this.blockPosition(), stuckTicks, 4,
                        "noclip 2s");
                stuckTicks = 0;
                return;
            }
            if (stuckTicks >= 180) {
                ModLogger.stuck(this.getUUID().toString(), this.blockPosition(), stuckTicks, 5,
                        "new-target from=" + currentGlobalTarget);
                m.clearGriverTarget(this.getUUID());
                pickNewRegularTarget(m);
                stuckTicks = 0;
                recoveryAttempts = 0;
                return;
            }
        }
    }

    /**
     * Строго ортогональное движение к (tx, ty, tz).
     * Определяем dominant axis от текущей клетки к target, идём только по ней;
     * по перпендикуляру — выравниваемся к центру текущей клетки.
     * Это исключает диагональное прижимание к стенам на поворотах.
     */
    private void moveTowards(double tx, double ty, double tz) {
        moveTowards(tx, ty, tz, 1.25D);
    }

    private void moveTowards(double tx, double ty, double tz, double speedMultiplier) {
        double dx = tx - this.getX();
        double dz = tz - this.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.001D) return;
        double maxSpeed = this.getAttributeValue(Attributes.MOVEMENT_SPEED) * speedMultiplier;
        // Micro-path cells are steering markers, not destinations. Braking at every
        // cell made a running griver slower than a walking player.
        double desiredSpeed = maxSpeed;
        double mx = dx / distance * desiredSpeed;
        double mz = dz / distance * desiredSpeed;

        Vec3 dv = this.getDeltaMovement();
        // Ease into the next cell instead of replacing horizontal velocity every
        // tick. This removes the visible jerk at route nodes and 90 degree turns.
        double smoothedX = dv.x * 0.45D + mx * 0.55D;
        double smoothedZ = dv.z * 0.45D + mz * 0.55D;
        this.setDeltaMovement(smoothedX, dv.y, smoothedZ);

        // Если впереди ступенька вверх — прыжок
        if (ty > this.getY() + 0.5 && this.onGround()) {
            this.getJumpControl().jump();
        }

        // Плавный поворот в направлении движения
        double yawDx = tx - this.getX();
        double yawDz = tz - this.getZ();
        float targetYaw = (float) (Math.atan2(yawDz, yawDx) * 180.0 / Math.PI) - 90.0F;
        float currentYaw = this.getYRot();
        float delta = targetYaw - currentYaw;
        while (delta < -180) delta += 360;
        while (delta > 180) delta -= 360;
        float step = Math.signum(delta) * Math.min(8f, Math.abs(delta));
        float newYaw = currentYaw + step;
        this.setYRot(newYaw);
        this.yBodyRot = newYaw;
        this.yHeadRot = newYaw;
    }

    private void rebuildChainToTarget() {
        PatrolManager m = PatrolManager.get(this.level());
        if (m == null || currentGlobalTarget == null) return;
        waypointChain = m.findWaypointChain(this.blockPosition(), currentGlobalTarget);
        if (waypointChain == null || waypointChain.isEmpty()) {
            waypointChain = new ArrayList<>();
            waypointChain.add(currentGlobalTarget);
        } else if (!waypointChain.get(waypointChain.size() - 1).equals(currentGlobalTarget)) {
            waypointChain.add(currentGlobalTarget);
        }
        waypointIdx = 0;
        replanFails = 0;
        noProgressTicks = 0;
        startNavigateToCurrentWaypoint();
    }

    // ========== АТАКА ==========

    @Override
    public boolean doHurtTarget(Entity target) {
        if (isProtectedFromGriver(target)) return false;
        // Если зашли сюда из performAttack (путь райдера) — он уже взвёл
        // attackAnimationTimer и забродкастил event, дублировать нельзя
        // (тройной звук удара). Если timer == 0 — это AI-атака, ванильный
        // mob.tick() сам зовёт doHurtTarget, и тут единственное место где
        // мы можем поднять анимацию + звук + broadcast для клиента.
        boolean firstHit = (attackAnimationTimer == 0);
        if (super.doHurtTarget(target)) {
            if (firstHit) {
                startAttackAnimation();
                playAttackSound();
                if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.broadcastEntityEvent(this, (byte) 1);
                }
            }
            if (target instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 600, 0));
                living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 560, 0));
            }
            return true;
        }
        return false;
    }


    public void performAttack() {
        // Анимация запускается ВСЕГДА, даже на промахе — иначе райдер тыкает
        // ЛКМ и не видит ни звука, ни анимации, если цели нет в конусе.
        startAttackAnimation();
        playAttackSound();
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            serverLevel.broadcastEntityEvent(this, (byte) 1);
        }

        double range = 4.0;
        var entities = this.level().getEntities(this, this.getBoundingBox().inflate(range));
        Vec3 lookVec = this.getLookAngle();
        for (var target : entities) {
            if (target instanceof LivingEntity living && living != this) {
                if (isProtectedFromGriver(target)) continue;
                Vec3 toTarget = target.position().subtract(this.position()).normalize();
                double dot = lookVec.dot(toTarget);
                if (dot > 0.6 && this.distanceTo(target) <= range) {
                    // doHurtTarget применяет урон+эффекты. Анимация уже запущена выше,
                    // повторный запуск из doHurtTarget будет проигнорирован
                    // в startAttackAnimation (timer > 5).
                    this.doHurtTarget(living);
                    break;
                }
            }
        }
    }

    // ========== POSSESSION ==========

    public void setPossessed(boolean possessed, UUID playerUUID) {
        this.entityData.set(IS_POSSESSED, possessed);
        this.possessingPlayerUUID = playerUUID;
    }

    public boolean isPossessed() {
        return this.entityData.get(IS_POSSESSED);
    }

    public UUID getPossessingPlayer() {
        return possessingPlayerUUID;
    }

    // ========== ANIMAL ==========

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob parent) {
        return null;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    // ========== PERSISTENCE ==========

    @Override
    public void checkDespawn() {
        this.noActionTime = 0;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        tag.putBoolean("IsPossessed", isPossessed());
        tag.putBoolean("Saddled", isSaddled());
        tag.putBoolean("IsPatrolling", isPatrolling());
        tag.putBoolean("ReturningHome", returningHome);        // НОВОЕ
        tag.putBoolean("ForceChunkLoading", forceChunkLoading); // НОВОЕ
        tag.putBoolean("NaturalNightSpawn", naturalNightSpawn);
        tag.putInt("ChunkLoadRadius", chunkLoadRadius);        // НОВОЕ
        if (possessingPlayerUUID != null) tag.putUUID("PossessingPlayer", possessingPlayerUUID);
        if (currentGlobalTarget != null) {
            tag.putInt("TgtX", currentGlobalTarget.getX());
            tag.putInt("TgtY", currentGlobalTarget.getY());
            tag.putInt("TgtZ", currentGlobalTarget.getZ());
        }
        tag.putBoolean("DispersalPhase", inDispersalPhase);
        tag.putBoolean("Returning", returning);
        if (homePos != null) {
            tag.putInt("HomeX", homePos.getX());
            tag.putInt("HomeY", homePos.getY());
            tag.putInt("HomeZ", homePos.getZ());
        }
        if (spawnerBlockPos != null) {
            tag.putInt("SpawnerX", spawnerBlockPos.getX());
            tag.putInt("SpawnerY", spawnerBlockPos.getY());
            tag.putInt("SpawnerZ", spawnerBlockPos.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("IsPossessed") && tag.hasUUID("PossessingPlayer")) {
            setPossessed(true, tag.getUUID("PossessingPlayer"));
        }
        setSaddled(tag.getBoolean("Saddled"));
        this.entityData.set(IS_PATROLLING, tag.getBoolean("IsPatrolling"));
        returningHome = tag.getBoolean("ReturningHome");           // НОВОЕ
        // tag.getBoolean возвращает false для отсутствующих ключей. Старые сохранения
        // не имеют этого флага → форс-загрузка отключалась → гривер не патрулировал
        // на dedicated server'е после распаковки. Если ключа нет — оставляем дефолт (true).
        if (tag.contains("ForceChunkLoading")) {
            forceChunkLoading = tag.getBoolean("ForceChunkLoading");
        }
        chunkLoadRadius = tag.getInt("ChunkLoadRadius");
        if (chunkLoadRadius <= 0) chunkLoadRadius = 3;
        if (tag.contains("TgtX")) {
            currentGlobalTarget = new BlockPos(tag.getInt("TgtX"), tag.getInt("TgtY"), tag.getInt("TgtZ"));
        }
        inDispersalPhase = tag.getBoolean("DispersalPhase");
        returning = tag.getBoolean("Returning");
        if (tag.contains("HomeX")) {
            homePos = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
        }
        if (tag.contains("SpawnerX")) {
            spawnerBlockPos = new BlockPos(tag.getInt("SpawnerX"), tag.getInt("SpawnerY"), tag.getInt("SpawnerZ"));
        }
        naturalNightSpawn = tag.getBoolean("NaturalNightSpawn");
    }




    // ========== GeckoLib hooks ==========

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::animPredicate));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /**
     * Главный animation-predicate. GeckoLib дёргает его каждый рендер-фрейм.
     * Читает состояние entity и решает, какая анимация должна играть.
     *
     * Приоритет: ATTACK (по таймеру) → JUMP (в воздухе) → RUN/walk-back (движение) → STOP (idle).
     */
    private PlayState animPredicate(AnimationState<GriverEntity> state) {
        AnimationController<GriverEntity> ctrl = state.getController();

        // 1) Атака — высший приоритет.
        if (this.attackAnimationTimer > 0) {
            if (this.shouldRestartAttackAnim) {
                ctrl.forceAnimationReset();
                this.shouldRestartAttackAnim = false;
            }
            ctrl.setAnimationSpeed(4.0);
            return state.setAndContinue(ATTACK_ANIM);
        }

        // 2) Прыжок — в воздухе с восходящим вектором.
        if (!this.onGround() && this.getDeltaMovement().y > 0.05) {
            ctrl.setAnimationSpeed(1.0);
            return state.setAndContinue(JUMP_ANIM);
        }

        // 3) Движение. Источник правды — limbSwingAmount из AnimationState: это
        // GeckoLib-овская величина, обновляемая рендером по walkAnimation.speed()
        // самого entity. Работает и для наездника в 3-м лице (его клиент сам
        // двигает гривера через travel()), и для всех наблюдателей (клиент
        // обновляет walkAnimation по дельте позиции). rider.zza/xxa нельзя — они
        // не синхронизируются между клиентами и даже у локального наездника
        // могут быть 0 (vehicle input идёт другим путём).
        double horizontalSpeed = this.getDeltaMovement().horizontalDistance();
        float limbSwing = state.getLimbSwingAmount();
        boolean isMoving = limbSwing > 0.05f || horizontalSpeed > 0.02;
        boolean isMovingBackward = false;

        // Если есть наездник с явным «назад» — приоритет инпута. Иначе —
        // dot-product между deltaMovement и направлением взгляда.
        if (this.isVehicle() && this.getControllingPassenger() instanceof Player rider
                && rider.zza < 0) {
            isMovingBackward = true;
            isMoving = true;
        } else if (horizontalSpeed > 0.03) {
            Vec3 delta = this.getDeltaMovement();
            Vec3 lookVec = this.getLookAngle();
            double dot = delta.x * lookVec.x + delta.z * lookVec.z;
            isMovingBackward = dot < -0.5;
        }

        if (isMoving) {
            if (isMovingBackward) {
                ctrl.setAnimationSpeed(-1.5);
            } else if (horizontalSpeed > 0.2 || limbSwing > 0.6f) {
                ctrl.setAnimationSpeed(6.0);
            } else {
                ctrl.setAnimationSpeed(2.0);
            }
            return state.setAndContinue(RUN_ANIM);
        }

        // 4) Idle — никакой анимации, GeckoLib оставит модель в дефолтной позе.
        return PlayState.STOP;
    }

    // ========== GOALS ==========

    /**
     * Пустой goal — патруль управляется в tick(). Нужен только чтобы занять MOVE-слот.
     */
    class SharedPatrolGoal extends Goal {
        public SharedPatrolGoal() {
            this.setFlags(EnumSet.noneOf(Goal.Flag.class));
        }

        @Override
        public boolean canUse() {
            return false;
        }
    }

    class GriverAttackAllGoal extends Goal {
        private LivingEntity target;
        private int attackDelay = 0;
        private int pathUpdateCooldown = 0;
        private static final double MAX_DISTANCE = 30.0;
        private static final double ATTACK_DISTANCE = 3.5;
        private static final int LOST_TARGET_TIMEOUT = 60;

        private int lostTargetTimer = 0;

        public GriverAttackAllGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (isAttacking || attackAnimationTimer > 0) return false;
            if (isVehicle()) return false;
            if (isReturningHome) return false;

            if (lastHurtByMob != null && lastHurtByMob.isAlive() && distanceTo(lastHurtByMob) < MAX_DISTANCE) {
                target = lastHurtByMob;
                return true;
            }

            LivingEntity closest = findClosestVisibleTarget();
            target = closest;
            return target != null && distanceTo(target) < MAX_DISTANCE;
        }

        @Override
        public boolean canContinueToUse() {
            if (isVehicle()) return false;
            if (isReturningHome) return false;
            if (target == null) return false;
            if (!target.isAlive()) return false;

            double dist = distanceTo(target);

            if (dist > MAX_DISTANCE + 10 || !hasLineOfSight(target)) {
                lostTargetTimer++;
                if (lostTargetTimer >= LOST_TARGET_TIMEOUT) {
                    return false;
                }
            } else {
                lostTargetTimer = 0;
            }

            return true;
        }

        @Override
        public void start() {
            attackDelay = 0;
            pathUpdateCooldown = 0;
            lostTargetTimer = 0;
            setTarget(target);
            playDetectionSound();

            ModLogger.patrol("attack-start", "griver=" + getUUID().toString().substring(0, 8)
                    + " target=" + (target != null ? target.getName().getString() : "null"));
        }

        @Override
        public void stop() {
            target = null;
            attackDelay = 0;
            lostTargetTimer = 0;
            setTarget(null);
            getNavigation().stop();

            ModLogger.patrol("attack-stop", "griver=" + getUUID().toString().substring(0, 8));
        }

        @Override
        public void tick() {
            if (target == null || isVehicle() || isReturningHome) return;

            setTarget(target);
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distance = distanceTo(target);

            if (distance <= ATTACK_DISTANCE) {
                getNavigation().stop();
                attackDelay++;
                if (attackDelay >= 10) {
                    doHurtTarget(target);
                    attackDelay = 0;
                }
            } else {
                pathUpdateCooldown--;
                if (pathUpdateCooldown <= 0) {
                    if (getNavigation() instanceof GriverPathNavigation gpn) gpn.syncExclusionZones();
                    getNavigation().moveTo(target, WALK_SPEED);
                    pathUpdateCooldown = 10;
                }
            }
        }

        private LivingEntity findClosestVisibleTarget() {
            double closestDistance = MAX_DISTANCE;
            LivingEntity closest = null;

            if (lastHurtByMob != null && lastHurtByMob.isAlive() && distanceTo(lastHurtByMob) < MAX_DISTANCE + 10) {
                return lastHurtByMob;
            }

            for (Player player : level().players()) {
                if (player.isCreative() || player.isSpectator() || player.isInvisible()) continue;
                if (player == getControllingPassenger()) continue;
                double distance = distanceTo(player);
                if (distance < closestDistance && hasLineOfSight(player)) {
                    closestDistance = distance;
                    closest = player;
                }
            }

            if (closest == null) {
                for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class,
                        GriverEntity.this.getBoundingBox().inflate(MAX_DISTANCE))) {
                    if (living == GriverEntity.this) continue;
                    if (living instanceof Player) continue;
                    if (living instanceof GriverEntity) continue;

                    double distance = distanceTo(living);
                    if (distance < closestDistance && hasLineOfSight(living)) {
                        closestDistance = distance;
                        closest = living;
                    }
                }
            }

            return closest;
        }

        private boolean hasLineOfSight(LivingEntity target) {
            return GriverEntity.this.getSensing().hasLineOfSight(target);
        }
    }


    public boolean isReturningHome() {
        return returningHome;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Запоминаем, кто атаковал гривера
        if (source.getEntity() instanceof Player attacker && !isProtectedFromGriver(attacker)) {
            if (!isVehicle()) {
                lastHurtByMob = attacker;
                hurtCooldown = 100;
            }
        }

        return super.hurt(source, amount);
    }

    @Override
    public void travel(Vec3 travelVector) {
        LivingEntity passenger = this.getControllingPassenger();

        if (this.isAlive() && this.isSaddled() && passenger instanceof Player player) {
            this.getNavigation().stop();
            this.setTarget(null);

            this.setYRot(player.getYRot());
            this.yRotO = this.getYRot();
            this.setXRot(player.getXRot() * 0.5F);
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.getYRot();

            float forward = player.zza;
            float strafe = player.xxa;

            float baseSpeed = (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
            float speed = baseSpeed;
            if (forward > 0) speed = baseSpeed * 0.8f;
            else if (forward < 0) speed = baseSpeed * 0.4f;

            this.setSpeed(speed);

            // GeckoLib: предикат animPredicate() сам читает passenger.zza/xxa и
            // подбирает run/walk-backward; ручные вызовы play*Animation больше не нужны.
            super.travel(new Vec3(strafe, travelVector.y, forward));
        } else {
            super.travel(travelVector);
        }
    }

    // ЭТИ МЕТОДЫ ДОЛЖНЫ БЫТЬ ТОЛЬКО ОДИН РАЗ!
    @Override
    public double getPassengersRidingOffset() {
        return 2.0D;
    }


    public Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        return new Vec3(0, 2.0D, -0.8D);
    }
    // ========== МЕТОДЫ ДЛЯ УПРАВЛЕНИЯ ИИ ПРИ ЕЗДЕ ==========

    private void disableAIForRiding() {
        // Отключаем патруль
        if (isPatrolling()) {
            this.entityData.set(IS_PATROLLING, false);
        }

        // Сбрасываем все цели
        if (currentGlobalTarget != null) {
            currentGlobalTarget = null;
        }

        if (forcedAttackTarget != null) {
            forcedAttackTarget = null;
            forcedAttackTimeout = 0;
        }

        if (currentTarget != null) {
            currentTarget = null;
        }

        if (lastHurtByMob != null) {
            lastHurtByMob = null;
        }

        // Останавливаем навигацию
        getNavigation().stop();

        // Очищаем очереди
        plannedTargets.clear();
        microPath = null;
        waypointChain = null;

        // Сбрасываем флаги
        inDispersalPhase = false;
        isChasingPlayer = false;
        returning = false;
        isReturningHome = false;

        ModLogger.patrol("riding", "griver=" + getUUID().toString().substring(0, 8) + " AI disabled");
    }

    private void enableAIAfterRiding() {
        PatrolManager m = PatrolManager.get(this.level());
        if (m != null && m.isGlobalPatrolActive() && !isVehicle() && !isReturningHome) {
            // ПОЛНАЯ ОЧИСТКА ПЕРЕД ВОССТАНОВЛЕНИЕМ
            this.currentGlobalTarget = null;
            this.plannedTargets.clear();
            this.microPath = null;
            this.waypointChain = null;
            this.returning = false;
            this.returningHome = false;
            this.isReturningHome = false;
            this.inDispersalPhase = false;

            this.entityData.set(IS_PATROLLING, true);
            if (naturalNightSpawn) pickNewNaturalNightTarget(m);
            else joinGlobalPatrol();
            ModLogger.patrol("riding", "griver=" + getUUID().toString().substring(0, 8) + " AI enabled, returning to patrol");
        }
    }



    private final Set<ChunkPos> forcedChunks = new HashSet<>();
    private final Set<ChunkPos> wantedChunks = new HashSet<>(); // ПЕРЕИСПОЛЬЗУЕМ!

    private void forceLoadChunksAround() {
        int chunkX = this.blockPosition().getX() >> 4;
        int chunkZ = this.blockPosition().getZ() >> 4;

        // ОЧИЩАЕМ ВМЕСТО СОЗДАНИЯ НОВОГО
        wantedChunks.clear();

        for (int dx = -chunkLoadRadius; dx <= chunkLoadRadius; dx++) {
            for (int dz = -chunkLoadRadius; dz <= chunkLoadRadius; dz++) {
                wantedChunks.add(new ChunkPos(chunkX + dx, chunkZ + dz));
            }
        }

        // НАХОДИМ РАЗНИЦУ БЕЗ СОЗДАНИЯ НОВЫХ КОЛЛЕКЦИЙ
        for (ChunkPos pos : forcedChunks) {
            if (!wantedChunks.contains(pos)) {
                releaseChunk(pos);
            }
        }

        for (ChunkPos pos : wantedChunks) {
            if (!forcedChunks.contains(pos)) {
                loadChunk(pos);
            }
        }

        // ОБНОВЛЯЕМ forcedChunks БЕЗ СОЗДАНИЯ НОВОГО
        forcedChunks.clear();
        forcedChunks.addAll(wantedChunks);
    }

    /** Освобождает все форс-билеты этого гривера. Вызывать при death/removal/dimension change. */
    private void releaseForcedChunks() {
        if (this.level().isClientSide) return;
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        for (net.minecraft.world.level.ChunkPos cp : forcedChunks) {
            serverLevel.setChunkForced(cp.x, cp.z, false);
        }
        forcedChunks.clear();
    }

    private void forceLoadChunkAt(BlockPos pos) {
        if (!forceChunkLoading) return;
        if (this.level().isClientSide) return;
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        if (pos == null) return;

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        serverLevel.getChunk(chunkX, chunkZ);
    }

// ========== МЕТОД ДЛЯ ПРИНУДИТЕЛЬНОЙ АТАКИ ==========

    public void setForcedAttackTarget(LivingEntity target, int durationTicks) {
        if (target == null) return;
        if (isProtectedFromGriver(target)) return;

        this.forcedAttackTarget = target;
        this.forcedAttackTimeout = durationTicks;

        if (isPatrolling()) {
            this.entityData.set(IS_PATROLLING, false);
            inDispersalPhase = false;
            plannedTargets.clear();
            getNavigation().stop();
            microPath = null;
            waypointChain = null;
        }

        ModLogger.patrol("forced-attack", "griver=" + getUUID().toString().substring(0, 8)
                + " target=" + target.getName().getString());
    }
    @Override
    public void rideTick() {
        super.rideTick();

        if (isVehicle() && getFirstPassenger() instanceof Player player) {

            // ========== НЕВИДИМОСТЬ ==========
            if (!this.level().isClientSide) {
                // Добавляем/обновляем эффект невидимости на 40 тиков (2 секунды)
                // Эффект будет перезаписываться каждый тик, поэтому не пропадёт
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.INVISIBILITY,
                        40, 0, false, false, false));
            }

            // ========== ЗВУКИ ПРИ ЕЗДЕ ==========
            // Раньше тут был отдельный звук, играющий ТОЛЬКО наезднику (через
            // player.playSound). Теперь обычный путь playRunSound/playWalkSound
            // в tick() (см. блок «ЗВУКИ ШАГОВ») играет для всех вокруг — этот
            // дубликат больше не нужен.

            // Отключаем навигацию на сервере
            if (!this.level().isClientSide) {
                if (getNavigation().isInProgress()) getNavigation().stop();
                if (currentGlobalTarget != null) currentGlobalTarget = null;
                if (forcedAttackTarget != null) forcedAttackTarget = null;
                if (currentTarget != null) currentTarget = null;
                if (getTarget() != null) setTarget(null);
            }

            // GeckoLib: predicate animPredicate() сам читает rider.zza/xxa и подбирает анимацию.
        }
    }
    private void playRidingSound(Player rider) {
        boolean isMovingForward = rider.zza > 0;
        boolean isMovingBackward = rider.zza < 0;
        boolean isStrafing = rider.xxa != 0;

        // Движение назад
        if (isMovingBackward && this.onGround() && attackAnimationTimer == 0 && !isAttacking) {
            if (backwardSoundCooldown <= 0) {
                this.level().playSound(null, this.blockPosition(),
                        ModSounds.GRIVER_WALK.get(),  // Звук шагов назад
                        net.minecraft.sounds.SoundSource.HOSTILE,
                        1.2F,
                        0.6F + (this.random.nextFloat() * 0.2F));  // Ниже тон для движения назад
                backwardSoundCooldown = BACKWARD_SOUND_DELAY;
            }
            return;
        }

        // Движение вперёд
        if (isMovingForward && ridingSoundCooldown <= 0 && this.onGround() && attackAnimationTimer == 0 && !isAttacking) {
            this.level().playSound(null, this.blockPosition(),
                    ModSounds.GRIVER_RUN.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE,
                    1.3F,
                    0.8F + (this.random.nextFloat() * 0.3F));
            ridingSoundCooldown = RIDING_SOUND_DELAY;
        }

        // Боковое движение (стрейф) - тоже звук бега
        if (isStrafing && !isMovingForward && !isMovingBackward && ridingSoundCooldown <= 0
                && this.onGround() && attackAnimationTimer == 0 && !isAttacking) {
            this.level().playSound(null, this.blockPosition(),
                    ModSounds.GRIVER_RUN.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE,
                    1.2F,
                    0.9F + (this.random.nextFloat() * 0.2F));
            ridingSoundCooldown = RIDING_SOUND_DELAY;
        }
    }

    // ========== МЕТОДЫ АНИМАЦИИ ==========

    // GeckoLib stubs: animation выбирается в animPredicate(), эти методы оставлены
    // как no-op чтобы не ломать существующие call-site (controllers, ride logic).
    private void stopWalkAnimation() { /* no-op for GeckoLib */ }
    private void playRunAnimation()  { /* no-op for GeckoLib */ }
    private void playJumpAnimation() { /* no-op for GeckoLib */ }
    private void updateReturningLogic() {
        // НЕ ПРЕРЫВАЕМ ВОЗВРАТ ЕСЛИ ИДЁТ АТАКА
        if (isAttacking || attackAnimationTimer > 0) {
            return;
        }

        PatrolManager m = PatrolManager.get(this.level());
        BlockPos home = homePos;
        if (home == null && m != null) home = m.getSpawnPoint();
        if (home == null) {
            returning = false;
            returningHome = false;
            isReturningHome = false;
            return;
        }

        double distSqr = this.blockPosition().distSqr(home);
        if (distSqr <= 4.0) {
            ModLogger.patrol("return-done", "griver=" + this.getUUID().toString().substring(0, 8));
            returning = false;
            returningHome = false;
            isReturningHome = false;
            this.getNavigation().stop();
            microPath = null;
            waypointChain = null;
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(GRIVER_BASE_SPEED);

            if (m != null && m.isGlobalPatrolActive() && !isVehicle()) {
                currentGlobalTarget = null;
                plannedTargets.clear();
                this.entityData.set(IS_PATROLLING, true);
                joinGlobalPatrol();
                ModLogger.patrol("return-restart", "griver=" + this.getUUID().toString().substring(0, 8) + " patrol restarted");
            }
            return;
        }

        double baseSpeed = GRIVER_BASE_SPEED;
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(baseSpeed * RETURN_SPEED_MULTIPLIER);

        this.getNavigation().stop();

        if (waypointChain == null || waypointIdx >= waypointChain.size()) {
            if (m != null) {
                waypointChain = m.findWaypointChain(this.blockPosition(), home);
            }
            if (waypointChain == null || waypointChain.isEmpty()) {
                waypointChain = new ArrayList<>();
                waypointChain.add(home);
            } else if (!waypointChain.get(waypointChain.size() - 1).equals(home)) {
                waypointChain.add(home);
            }
            waypointIdx = 0;
            microPath = null;
            ModLogger.patrol("return-chain", "griver=" + this.getUUID().toString().substring(0, 8)
                    + " len=" + waypointChain.size() + " home=" + home);
        }

        BlockPos waypoint = waypointChain.get(waypointIdx);

        if (this.blockPosition().distSqr(waypoint) <= 4.0) {
            waypointIdx++;
            microPath = null;
            return;
        }

        if (microPath == null || microIdx >= microPath.size()) {
            if (microReplanCooldown > 0) {
                microReplanCooldown--;
            } else {
                microPath = buildMicroPath(this.blockPosition(), waypoint);
                microIdx = 0;
                microReplanCooldown = 20;
                if (microPath == null || microPath.isEmpty()) {
                    ModLogger.pathFail(this.getUUID().toString(), this.blockPosition(), waypoint, "return-no-path");
                    waypointIdx++;
                }
            }
        }

        if (microPath != null && microIdx < microPath.size()) {
            BlockPos step = microPath.get(microIdx);
            double stepX = step.getX() + 0.5;
            double stepZ = step.getZ() + 0.5;
            double dx = stepX - this.getX();
            double dz = stepZ - this.getZ();
            double horDistSq = dx * dx + dz * dz;
            if (horDistSq < 0.5) {
                microIdx++;
            } else {
                moveTowards(stepX, step.getY(), stepZ);
            }
        }
    }
    public List<BlockPos> getCurrentWaypointChain() {
        if (waypointChain == null) return Collections.emptyList();
        List<BlockPos> out = new ArrayList<>();
        for (int i = waypointIdx; i < waypointChain.size(); i++) out.add(waypointChain.get(i));
        return out;
    }

    /**
     * Полный путь по лабиринту block-by-block через все 5 текущих waypoint'ов.
     * Используется только админ-меню для визуализации. Кладём вместе:
     *  1) текущий microPath от позиции до ближайшей точки,
     *  2) затем для каждой пары соседних waypoint'ов — A* через buildMicroPath.
     * Кэшируется на короткий срок чтобы не пересчитывать каждый sync.
     */
    private List<BlockPos> cachedDisplayPath = Collections.emptyList();
    private int displayPathCacheTick = -1000;

    public List<BlockPos> getDisplayPath() {
        if (this.level().isClientSide) return cachedDisplayPath;
        // Перестраиваем не чаще раза в 10 секунд. Раньше было 5 сек, но REQUEST_SYNC
        // тоже идёт каждые 5 сек — каждый второй sync был cache miss → пинг-спайк.
        // 10 сек гарантирует что между периодическими sync'ами кэш гарантированно тёплый.
        // Также ивалидируется в applyExternalPlan / leaveGlobalPatrol при смене состояния.
        if (this.tickCount - displayPathCacheTick < 200 && !cachedDisplayPath.isEmpty()) {
            return cachedDisplayPath;
        }
        displayPathCacheTick = this.tickCount;

        List<BlockPos> out = new ArrayList<>();
        BlockPos cursor = this.blockPosition();

        // Текущий microPath
        if (microPath != null) {
            for (int i = microIdx; i < microPath.size(); i++) {
                out.add(microPath.get(i));
                cursor = microPath.get(i);
            }
        }

        // Дальше — A* между остальными waypoint'ами
        if (waypointChain != null) {
            int startIdx = waypointIdx + 1;
            for (int i = startIdx; i < waypointChain.size(); i++) {
                BlockPos wp = waypointChain.get(i);
                if (cursor.equals(wp)) continue;
                List<BlockPos> seg = buildMicroPath(cursor, wp);
                if (seg == null || seg.isEmpty()) {
                    // Не удалось построить — обрываем визуализацию
                    break;
                }
                out.addAll(seg);
                cursor = wp;
                // Лимит чтобы не отправлять километры
                if (out.size() > 300) break;
            }
        }

        cachedDisplayPath = out;
        return out;
    }
    public void resetAndJoinPatrol() {
        if (this.level().isClientSide) return;

        // Полный сброс состояния
        this.getNavigation().stop();
        this.setTarget(null);
        this.currentGlobalTarget = null;
        this.plannedTargets.clear();
        this.microPath = null;
        this.waypointChain = null;
        this.waypointIdx = 0;
        this.microIdx = 0;
        this.returning = false;
        this.returningHome = false;
        this.isReturningHome = false;
        this.inDispersalPhase = false;

        // Включаем патруль
        this.entityData.set(IS_PATROLLING, true);
        joinGlobalPatrol();

        ModLogger.patrol("reset-join", "griver=" + this.getUUID().toString().substring(0, 8) + " patrol reset and joined");
    }


    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);

        if (passenger instanceof Player player) {
            // Убираем эффект невидимости и любые зависшие флаги от possess-режима
            player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            player.setInvisible(false);
            player.noPhysics = false;
            player.setNoGravity(false);
            // Сбрасываем capability possession, если был активен (например, через /possess).
            player.getCapability(com.labyrinthmod.common.capability.PossessionProvider.POSSESSION)
                    .ifPresent(data -> {
                        if (data.isPossessing()) {
                            data.setPossessing(false);
                            data.setPossessedEntityId(-1);
                        }
                    });
        }

        // Сбрасываем стейт-таймеры, чтобы при следующем mount'е не было залипших значений
        this.attackAnimationTimer = 0;
        this.isAttacking = false;
        this.ridingSoundCooldown = 0;
        this.walkSoundCooldown = 0;
        this.runSoundCooldown = 0;

        if (!this.level().isClientSide && !this.isVehicle()) {
            enableAIAfterRiding();
        }
    }
    @Override
    public void remove(RemovalReason reason) {
        // Если есть всадник - показать его перед удалением гривера
        if (this.getFirstPassenger() instanceof Player player) {
            player.setInvisible(false);
            player.noPhysics = false;
            player.setNoGravity(false);
            player.getCapability(com.labyrinthmod.common.capability.PossessionProvider.POSSESSION)
                    .ifPresent(data -> {
                        if (data.isPossessing()) {
                            data.setPossessing(false);
                            data.setPossessedEntityId(-1);
                        }
                    });
            player.stopRiding();
        }

        if (!this.level().isClientSide) {
            // Снимаем все форс-билеты этого гривера, иначе чанки залипнут навсегда.
            releaseForcedChunks();

            PatrolManager m = PatrolManager.get(this.level());
            if (m != null) {
                m.clearGriverTarget(this.getUUID());
                m.clearDispersalTarget(this.getUUID());
                m.clearReservedWaypoints(this.getUUID());
                m.clearPlannedTargets(this.getUUID());
                m.clearGriverZone(this.getUUID());
                m.clearVisitHistory(this.getUUID());
            }

            if (reason != RemovalReason.KILLED && isSaddled()) {
                this.level().getServer().execute(() -> {
                    if (!isVehicle() && !isRemoved()) {
                        enableAIAfterRiding();
                    }
                });
            }
        }
        super.remove(reason);
    }



    private void startAttackAnimation() {
        // Дебаунс — не перезапускаем клип атаки если предыдущий ещё активен (>5 тиков
        // = >80% продолжительности). Иначе при спаме клика анимация дёргается.
        if (attackAnimationTimer > 5) return;
        attackAnimationTimer = 30;
        // Флаг для animPredicate: при следующем рендере нужно forceAnimationReset(),
        // иначе GeckoLib видит ATTACK_ANIM (thenPlay) уже завершённой и не
        // проигрывает её повторно — анимация играет только на первом ударе.
        shouldRestartAttackAnim = true;
    }
    @Override
    public void handleEntityEvent(byte id) {
        if (id == 1) {
            startAttackAnimation();
        } else {
            super.handleEntityEvent(id);
        }
    }
    /**
     * Воспроизводит звук шагов (медленная ходьба)
     */


    /**
     * Воспроизводит звук бега (быстрые шаги)
     */
    private void playRunSound() {
        if (this.level().isClientSide) return;
        // Декремент в tick() сверху — здесь только проверяем
        if (runSoundCooldown > 0) return;

        double speed = this.getDeltaMovement().horizontalDistance();
        boolean isRunning = speed > 0.2;

        if (this.isVehicle() && this.getControllingPassenger() instanceof Player player) {
            isRunning = player.zza != 0 || player.xxa != 0;
        }

        // ВОЗВРАТ ДОМОЙ - ТОЛЬКО ЗВУК БЕГА
        boolean isReturningFast = (isReturningHome || returning) && speed > 0.3;

        // НЕ воспроизводим бег если уже воспроизвели в этом тике через ходьбу
        if ((isRunning || isReturningFast) && this.onGround() && attackAnimationTimer == 0 && !isAttacking) {
            this.level().playSound(null, this.blockPosition(),
                    ModSounds.GRIVER_RUN.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE,
                    1.5F,
                    0.7F + (this.random.nextFloat() * 0.3F));
            runSoundCooldown = RUN_SOUND_DELAY;
            // Сбрасываем кулдаун ходьбы, чтобы звуки не накладывались
            walkSoundCooldown = Math.max(walkSoundCooldown, RUN_SOUND_DELAY / 2);
        }
    }

    private void playWalkSound() {
        if (this.level().isClientSide) return;
        if (walkSoundCooldown > 0) return;

        double speed = this.getDeltaMovement().horizontalDistance();
        boolean isWalking = speed > 0.02 && speed < 0.2;

        // ВОЗВРАТ ДОМОЙ - НЕ ВОСПРОИЗВОДИМ ХОДЬБУ
        boolean isReturningFast = (isReturningHome || returning) && speed > 0.3;

        // Если уже бежим или возвращаемся домой - не воспроизводим ходьбу
        if (isWalking && this.onGround() && attackAnimationTimer == 0 && !isAttacking && !isReturningFast) {
            // Дополнительная проверка: если runSoundCooldown активен - не воспроизводим
            if (runSoundCooldown > 0) {
                return;
            }
            this.level().playSound(null, this.blockPosition(),
                    ModSounds.GRIVER_RUN.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE,
                    1.2F,
                    0.9F + (this.random.nextFloat() * 0.2F));
            walkSoundCooldown = WALK_SOUND_DELAY;
        }
    }
    /**
     * Воспроизводит звук атаки
     */
    private void playAttackSound() {
        if (this.level().isClientSide) return;

        this.level().playSound(null, this.blockPosition(),
                ModSounds.GRIVER_ATTACK.get(),
                net.minecraft.sounds.SoundSource.HOSTILE,
                1.8F,  // Громкий звук атаки. Дальность задана в ModSounds (24 блока)
                0.8F + (this.random.nextFloat() * 0.4F));
    }

    private void playDetectionSound() {
        if (this.level().isClientSide || alertSoundCooldown > 0) return;
        this.level().playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR,
                SoundSource.HOSTILE, 1.35F, 0.55F + this.random.nextFloat() * 0.12F);
        // Тихий металлический слой делает рёв менее похожим на обычного моба.
        this.level().playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE,
                SoundSource.HOSTILE, 0.65F, 0.55F + this.random.nextFloat() * 0.10F);
        alertSoundCooldown = 140;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return this.getTarget() != null ? SoundEvents.WARDEN_ANGRY : SoundEvents.WARDEN_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 110;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 1.2F;
    }

    @Override
    public float getVoicePitch() {
        return 0.62F + this.random.nextFloat() * 0.12F;
    }
    // GeckoLib stubs — выбор run/walk/walk-back и его speed multiplier теперь
    // делается в animPredicate() (см. registerControllers).
    private void playWalkAnimation() { /* no-op for GeckoLib */ }
    private void playWalkBackwardAnimation() { /* no-op for GeckoLib */ }
    public void returnHome() {
        if (this.level().isClientSide) return;

        leavingPatrol = true;
        isReturningHome = true;
        returning = true;

        // Останавливаем текущие цели
        if (currentGlobalTarget != null) {
            currentGlobalTarget = null;
        }
        if (forcedAttackTarget != null) {
            forcedAttackTarget = null;
        }
        if (currentTarget != null) {
            currentTarget = null;
        }
        setTarget(null);
        getNavigation().stop();

        // Очищаем очереди
        plannedTargets.clear();
        microPath = null;
        waypointChain = null;

        ModLogger.patrol("return-home", "griver=" + this.getUUID().toString().substring(0, 8));
    }
    /**
     * Воспроизводит звук при движении назад (не на лошади)
     */
    private void playBackwardSound() {
        if (this.level().isClientSide) return;
        if (backwardSoundCooldown > 0) return;

        Vec3 delta = this.getDeltaMovement();
        if (delta.horizontalDistance() > 0.05 && this.onGround() && attackAnimationTimer == 0 && !isAttacking) {
            Vec3 lookVec = this.getLookAngle();
            double dot = delta.x * lookVec.x + delta.z * lookVec.z;
            boolean isMovingBack = dot < -0.5;

            if (isMovingBack) {
                this.level().playSound(null, this.blockPosition(),
                        ModSounds.GRIVER_RUN.get(),
                        net.minecraft.sounds.SoundSource.HOSTILE,
                        1.2F,
                        0.7F + (this.random.nextFloat() * 0.2F));
                backwardSoundCooldown = BACKWARD_SOUND_DELAY;
            }
        }
    }
    /**
     * Проверяет, есть ли живая цель для атаки
     */
    private boolean hasLivingTarget() {
        if (currentTarget != null && currentTarget.isAlive()) return true;
        if (forcedAttackTarget != null && forcedAttackTarget.isAlive()) return true;
        if (lastHurtByMob != null && lastHurtByMob.isAlive()
                && !isProtectedFromGriver(lastHurtByMob)) return true;
        return false;
    }

    /**
     * Получает текущую цель для атаки
     */
    private LivingEntity getCurrentAttackTarget() {
        if (currentTarget != null && currentTarget.isAlive()) return currentTarget;
        if (forcedAttackTarget != null && forcedAttackTarget.isAlive()) return forcedAttackTarget;
        if (lastHurtByMob != null && lastHurtByMob.isAlive()
                && !isProtectedFromGriver(lastHurtByMob)) {
            return lastHurtByMob;
        }
        return null;
    }

    /**
     * Ищет новую цель для атаки
     */
    private LivingEntity cachedTarget = null;
    private int targetCacheTimer = 0;
    private static final int TARGET_CACHE_DURATION = 20; // 1 секунда

    private LivingEntity findNewTarget() {
        // ИСПОЛЬЗУЕМ КЭШИРОВАННУЮ ЦЕЛЬ ЕСЛИ ОНА ВАЛИДНА
        if (targetCacheTimer > 0) {
            targetCacheTimer--;

            if (cachedTarget != null && cachedTarget.isAlive() && !cachedTarget.isRemoved()
                    && !isProtectedFromGriver(cachedTarget)) {
                double distance = this.distanceToSqr(cachedTarget);
                if (distance <= 900.0) { // 30^2
                    return cachedTarget;
                }
            }

            cachedTarget = null;
        }

        // ИЩЕМ НОВУЮ ЦЕЛЬ ТОЛЬКО РАЗ В 20 ТИКОВ
        targetCacheTimer = TARGET_CACHE_DURATION;
        cachedTarget = findNewTargetInternal();
        return cachedTarget;
    }

    private LivingEntity findNewTargetInternal() {
        double range = 30.0;
        Player bestTarget = null;
        double bestDistance = Double.MAX_VALUE;

        for (Player player : level().players()) {
            if (!player.isAlive() || player == this.getControllingPassenger()) continue;
            if (player.isCreative() || player.isSpectator()
                    || player.isInvisible() || isProtectedFromGriver(player)) {
                continue;
            }

            if (player.isCrouching()) {
                Vec3 toPlayer = player.position().subtract(this.position());
                double distance = toPlayer.length();
                Vec3 look = this.getLookAngle();
                double facing = distance > 0.001D ? look.dot(toPlayer.scale(1.0D / distance)) : 1.0D;
                // A crouching player is ignored unless they are very close and directly
                // in front of the griver.
                if (distance > 5.0D || facing < 0.72D) {
                    continue;
                }
            }

            double distance = this.distanceToSqr(player);
            if (distance <= range * range && distance < bestDistance && canSeeEntity(player)) {
                bestDistance = distance;
                bestTarget = player;
            }
        }

        return bestTarget;
    }

    /**
     * Начинает атаку на цель
     */
    private void startAttackingTarget(LivingEntity target) {
        if (target == null) return;

        // Прерываем патруль
        if (isPatrolling()) {
            this.entityData.set(IS_PATROLLING, false);
            ModLogger.patrol("attack-interrupt", "griver=" + getUUID().toString().substring(0, 8));
        }

        // Прерываем возврат домой
        if (isReturningHome || returning) {
            isReturningHome = false;
            returning = false;
            getNavigation().stop();
        }

        currentTarget = target;
        isChasingPlayer = true;
        chaseMicroPath = Collections.emptyList();
        chaseMicroIndex = 0;
        chaseReplanTicks = 0;
        setTarget(target);
        chaseTimeout = 300; // 15 секунд на преследование

        ModLogger.patrol("attack-start", "griver=" + getUUID().toString().substring(0, 8)
                + " target=" + target.getName().getString());
    }

    /**
     * Продолжает атаку на текущую цель
     */
    private void continueAttackingTarget() {
        if (currentTarget == null || !currentTarget.isAlive()) {
            // Цель мертва — очищаем и возвращаемся к патрулю
            currentTarget = null;
            isChasingPlayer = false;
            chaseTimeout = 0;
            ModLogger.patrol("attack-target-dead", "griver=" + getUUID().toString().substring(0, 8));
            return;
        }

        // Игрок переключился в creative/spectator/невидимость — бросаем сразу.
        // Без этого griver продолжал гнаться даже после /gamemode creative.
        if (currentTarget instanceof Player p
                && (p.isCreative() || p.isSpectator() || p.isInvisible()
                || isProtectedFromGriver(p))) {
            currentTarget = null;
            isChasingPlayer = false;
            chaseTimeout = 0;
            setTarget(null);
            getNavigation().stop();
            ModLogger.patrol("attack-target-mode-change",
                    "griver=" + getUUID().toString().substring(0, 8));
            return;
        }

        // Цель в зоне иммунитета — бросаем.
        PatrolManager pmZ = PatrolManager.get(this.level());
        if (pmZ != null && isInExclusion(currentTarget.blockPosition(), pmZ.getExclusionZones())) {
            currentTarget = null;
            isChasingPlayer = false;
            chaseTimeout = 0;
            setTarget(null);
            getNavigation().stop();
            ModLogger.patrol("attack-target-in-zone", "griver=" + getUUID().toString().substring(0, 8));
            return;
        }

        // Таймаут преследования
        chaseTimeout--;
        if (chaseTimeout <= 0 && distanceTo(currentTarget) > 30.0) {
            currentTarget = null;
            isChasingPlayer = false;
            ModLogger.patrol("attack-timeout", "griver=" + getUUID().toString().substring(0, 8));
            return;
        }

        setTarget(currentTarget);
        getLookControl().setLookAt(currentTarget, 30.0F, 30.0F);
        double distance = distanceTo(currentTarget);

        if (distance <= 3.5) {
            getNavigation().stop();
            if (attackDelayCounter <= 0) {
                doHurtTarget(currentTarget);
                attackDelayCounter = 20; // Задержка между атаками (1 секунда)
            } else {
                attackDelayCounter--;
            }
        } else if (distance <= 30.0) {
            getNavigation().stop();
            if (chaseReplanTicks-- <= 0 || chaseMicroIndex >= chaseMicroPath.size()) {
                chaseMicroPath = buildMicroPath(this.blockPosition(), currentTarget.blockPosition());
                chaseMicroIndex = 0;
                chaseReplanTicks = 10;
            }
            if (chaseMicroIndex < chaseMicroPath.size()) {
                BlockPos step = chaseMicroPath.get(chaseMicroIndex);
                if (!canStandAt(step)) {
                    chaseMicroPath = Collections.emptyList();
                    chaseReplanTicks = 0;
                    this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
                    return;
                }
                double tx = step.getX() + 0.5D;
                double tz = step.getZ() + 0.5D;
                double dx = tx - this.getX();
                double dz = tz - this.getZ();
                if (dx * dx + dz * dz < 0.5D) chaseMicroIndex++;
                else moveTowards(tx, step.getY(), tz);
            }
        }
    }
    private BlockPos findAlternative(BlockPos.MutableBlockPos current, BlockPos end) {
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos n = current.relative(dir);
            if (isPassable(n) && canStandAt(n)) return n;
        }
        return null;
    }

    private void releaseChunk(net.minecraft.world.level.ChunkPos pos) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.setChunkForced(pos.x, pos.z, false);
        }
    }

    private void loadChunk(net.minecraft.world.level.ChunkPos pos) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.setChunkForced(pos.x, pos.z, true);
        }
    }

    private boolean canSeeEntity(LivingEntity entity) {
        return this.getSensing().hasLineOfSight(entity);
    }



}
