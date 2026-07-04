package com.labyrinthmod.common.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Класс управления ездой на гривере (аналог управления лошадью)
 */
public class GriverRideControl {

    private final GriverEntity griver;
    private float forwardImpulse;
    private float strafeImpulse;
    private boolean jumpRequested;

    // Скорость движения
    private static final float WALK_SPEED = 0.22f;
    private static final float TROT_SPEED = 0.35f;
    private static final float CANTER_SPEED = 0.45f;
    private static final float GALLOP_SPEED = 0.55f;

    // Сила прыжка
    private static final double JUMP_POWER = 0.5;

    public GriverRideControl(GriverEntity griver) {
        this.griver = griver;
        this.forwardImpulse = 0;
        this.strafeImpulse = 0;
        this.jumpRequested = false;
    }

    /**
     * Обновление управления (вызывается в travel)
     */
    public void updateRiderControl(LivingEntity rider) {
        if (!(rider instanceof Player player)) return;

        // Получение ввода от игрока
        float forward = player.zza;
        float strafe = player.xxa;

        // Сохраняем импульсы для анимаций и синхронизации
        this.forwardImpulse = forward;
        this.strafeImpulse = strafe;

        // Определяем скорость на основе движения вперёд
        float speed = WALK_SPEED;
        if (forward > 0) {
            speed = getSpeedFromForward(forward);
        } else if (forward < 0) {
            speed = WALK_SPEED * 0.6f;
        }

        // Устанавливаем скорость
        griver.setSpeed(speed);

        // Синхронизация поворота с игроком
        griver.setYRot(player.getYRot());
        griver.yRotO = griver.getYRot();
        griver.setXRot(player.getXRot() * 0.5F);
        griver.yBodyRot = griver.getYRot();
        griver.yHeadRot = griver.getYRot();

        // Обработка прыжка
        if (player.isScoping() && griver.onGround()) {
            jumpRequested = true;
        }

        // СКРЫВАЕМ ПРЕДМЕТЫ В РУКЕ У ИГРОКА
        player.getInventory().setChanged();
    }

    /**
     * Получение скорости в зависимости от силы нажатия вперёд
     */
    private float getSpeedFromForward(float forward) {
        if (forward <= 0.3f) {
            return WALK_SPEED;
        } else if (forward <= 0.6f) {
            return TROT_SPEED;
        } else if (forward <= 0.85f) {
            return CANTER_SPEED;
        } else {
            return GALLOP_SPEED;
        }
    }

    /**
     * Выполнение прыжка
     */
    public void performJump() {
        if (jumpRequested) {
            Vec3 motion = griver.getDeltaMovement();
            griver.setDeltaMovement(motion.x, JUMP_POWER, motion.z);
            jumpRequested = false;
        }
    }

    /**
     * Применение движения (вызывается в travel после обработки rider)
     */
    public void applyMovement(Vec3 travelVector) {
        griver.setDeltaMovement(
                travelVector.x * griver.getSpeed(),
                travelVector.y,
                travelVector.z * griver.getSpeed()
        );
    }

    // ========== GETTERS ==========

    public float getForwardImpulse() {
        return forwardImpulse;
    }

    public float getStrafeImpulse() {
        return strafeImpulse;
    }

    public boolean isJumpRequested() {
        return jumpRequested;
    }

    public void resetJump() {
        jumpRequested = false;
    }

    // ========== ДЛЯ КЛИЕНТ-СЕРВЕРНОЙ СИНХРОНИЗАЦИИ ==========

    /**
     * Обновление управления с сервера (для синхронизации)
     */
    public void updateFromServer(float forward, float strafe, boolean jump) {
        this.forwardImpulse = forward;
        this.strafeImpulse = strafe;
        this.jumpRequested = jump;
    }
}