package com.labyrinthmod.common.entity;

import net.minecraft.world.phys.Vec3;

/**
 * Контроллер анимаций Гривера.
 *
 * РАБОТАЕТ ТОЛЬКО НА СЕРВЕРЕ!
 *
 * Задача:
 * 1. Каждый тик вычислять скорость Гривера
 * 2. Определять, изменилось ли состояние (начал движение, остановился, побежал, прыгнул)
 * 3. Вызывать соответствующие callback'и (run(), jump(), attack())
 *
 * Callback'и настраиваются из GriverEntity в конструкторе.
 * Это точная копия подхода из [ST]Drive (CarMovement класс).
 */
public class GriverAnimationController {

    // ===== ПОЛЯ =====

    /** Гривер, которым управляем */
    private final GriverEntity griver;

    /** Предыдущая позиция для расчёта скорости */
    private Vec3 lastPos = Vec3.ZERO;

    // ===== CALLBACK'и (как в [ST]Drive!) =====
    /** Вызывается, когда Гривер начал движение */
    private Runnable onStartMoving = () -> {};

    /** Вызывается, когда Гривер остановился */
    private Runnable onStopMoving = () -> {};

    /** Вызывается, когда Гривер побежал (скорость > порога) */
    private Runnable onStartRunning = () -> {};

    /** Вызывается при атаке */
    private Runnable onAttack = () -> {};

    /** Вызывается при прыжке */
    private Runnable onJump = () -> {};

    /** Вызывается при приземлении */
    private Runnable onLand = () -> {};

    // ===== СОСТОЯНИЯ ДЛЯ ОТСЛЕЖИВАНИЯ ИЗМЕНЕНИЙ =====
    /** Был ли Гривер в движении в прошлом тике */
    private boolean wasMoving = false;

    /** Бежал ли Гривер в прошлом тике */
    private boolean wasRunning = false;

    /** Был ли Гривер в воздухе в прошлом тике */
    private boolean wasInAir = false;

    /** Счётчик для дебаунса прыжка (чтобы не срабатывал несколько раз) */
    private int jumpCooldown = 0;

    // ===== КОНСТРУКТОР =====

    public GriverAnimationController(GriverEntity griver) {
        this.griver = griver;
        this.lastPos = griver.position();
    }

    // ===== ОСНОВНОЙ МЕТОД =====

    /**
     * Вызывается из GriverEntity.tick() каждый тик.
     * Определяет текущее состояние и вызывает нужные callback'и.
     */
    public void tick() {
        // Контроллер работает только на сервере
        if (griver.level().isClientSide) return;

        // 1. Получаем текущую позицию и вычисляем скорость
        Vec3 currentPos = griver.position();
        double dx = currentPos.x - lastPos.x;
        double dz = currentPos.z - lastPos.z;
        double horizontalSpeed = Math.sqrt(dx * dx + dz * dz);

        // 2. Определяем текущее состояние
        boolean isMoving = horizontalSpeed > 0.02;      // > 0.02 блока/тик = движется
        boolean isRunning = horizontalSpeed > 0.28;     // > 0.28 = бежит
        boolean isInAir = !griver.onGround();           // не на земле = в воздухе

        // 3. Обработка начала/окончания движения
        if (isMoving && !wasMoving) {
            // Только что начал двигаться
            onStartMoving.run();
        } else if (!isMoving && wasMoving) {
            // Только что остановился
            onStopMoving.run();
        }

        // 4. Обработка бега (только если уже движется)
        if (isMoving && isRunning && !wasRunning) {
            // Только что перешёл с шага на бег
            onStartRunning.run();
        }

        // 5. Обработка прыжка и приземления
        if (jumpCooldown > 0) {
            jumpCooldown--;
        }

        // Был в воздухе, а теперь на земле = приземлился
        if (wasInAir && !isInAir && jumpCooldown == 0) {
            onLand.run();
        }

        // Был на земле, а теперь в воздухе И движется = прыжок
        if (!wasInAir && isInAir && isMoving && jumpCooldown == 0) {
            onJump.run();
            jumpCooldown = 10;  // Защита от многократного срабатывания
        }

        // 6. Сохраняем состояния для следующего тика
        wasMoving = isMoving;
        wasRunning = isRunning;
        wasInAir = isInAir;
        lastPos = currentPos;
    }

    /**
     * Вызывается при атаке Гривера.
     * Обычно вызывается из GriverEntity.doHurtTarget()
     */
    public void triggerAttack() {
        if (griver.level().isClientSide) return;
        onAttack.run();
    }

    // ===== МЕТОДЫ ДЛЯ НАСТРОЙКИ CALLBACK'ОВ =====
    // (вызываются из GriverEntity в конструкторе)

    /**
     * Устанавливает слушатели для движения
     * @param onStart - что делать при начале движения
     * @param onStop  - что делать при остановке
     */
    public void setMovingListeners(Runnable onStart, Runnable onStop) {
        this.onStartMoving = onStart;
        this.onStopMoving = onStop;
    }

    /**
     * Устанавливает слушатель для бега
     * @param onRun - что делать когда Гривер побежал
     */
    public void setRunningListener(Runnable onRun) {
        this.onStartRunning = onRun;
    }

    /**
     * Устанавливает слушатель для атаки
     * @param onAttack - что делать при атаке
     */
    public void setAttackListener(Runnable onAttack) {
        this.onAttack = onAttack;
    }

    /**
     * Устанавливает слушатели для прыжка и приземления
     * @param onJump - что делать при прыжке
     * @param onLand - что делать при приземлении
     */
    public void setJumpListeners(Runnable onJump, Runnable onLand) {
        this.onJump = onJump;
        this.onLand = onLand;
    }
}