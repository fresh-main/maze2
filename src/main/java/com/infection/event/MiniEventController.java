package com.infection.event;

import com.infection.network.Network;
import com.infection.network.packet.S2CForceHallucinationPacket;
import com.infection.network.packet.S2CMiniEventStatePacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный state-machine инвентиков. Один админ — одна сессия.
 *
 *  Жизненный цикл:
 *   IDLE → (admin: select / launchTargeted)  → PREPARING/ACTIVE
 *   PREPARING → (admin: cancel) → IDLE
 *   PREPARING → (admin: launch G)  → ACTIVE (только JUMPSCARE)
 *   ACTIVE → автоматически через DURATION_TICKS → IDLE
 *
 *  Типы:
 *   JUMPSCARE — radius-based, через PREPARING (G — запуск).
 *   HALLUCINATION_SURGE — точечный, без PREPARING (см. C2SHallucinationLaunchPacket).
 *   LOOMING — точечный, ОДНА жертва. Админ телепортируется ей за спину, сразу ACTIVE.
 *             Виден только жертве (vanilla invisibility flag + EntityInvisibleToMixin).
 *             Жертве шлётся forced-приступ с фразами «обернись».
 *   FLICKER_PRESENCE — точечный, ОДНА жертва. Админ телепортируется в случайных
 *             точках в 5..12 блоков вокруг неё за 4 сек. Виден только жертве.
 */
public final class MiniEventController {

    public static final int DURATION_TICKS = 80;
    public static final int HALLUCINATION_DURATION_TICKS = 100;
    public static final int LOOMING_DURATION_TICKS = 60;     // 3 сек
    public static final int FLICKER_DURATION_TICKS = 80;     // 4 сек
    public static final int FLICKER_CYCLE_TICKS = 14;        // ~700ms между телепортами

    // BLACK_RUSH: фазы (в тиках от startTick)
    public static final int RUSH_TURN_END = 28;     // 0..28: плавный поворот жертвы (~1.4 сек)
    public static final int RUSH_PAUSE_END = 36;    // 28..36: админ отступает чуть назад
    public static final int RUSH_ATTACK_END = 48;   // 36..48: админ рывком летит в жертву (~0.6 сек)
    public static final int RUSH_DURATION_TICKS = 56;  // общее ~2.8 сек

    public static final double RADIUS_BLOCKS = 15.0;
    private static final double RADIUS_SQ = RADIUS_BLOCKS * RADIUS_BLOCKS;

    private static final List<String> LOOMING_PHRASES = List.of(
            "ОБЕРНИСЬ", "ОГЛЯНИСЬ", "СЗАДИ", "ЗА ТОБОЙ",
            "ПОВЕРНИСЬ", "ПОСМОТРИ НАЗАД", "ОНО ТАМ"
    );

    /**
     * Сессия админа. Все «дополнительные» поля (target, savedPos, ...) опциональны
     * и используются только для специфичных типов инвентиков.
     */
    public record Session(MiniEventType type,
                          MiniEventState state,
                          long startTick,
                          long endTick,
                          boolean useDefaultPhrases,
                          List<String> customPhrases,
                          UUID targetId,
                          Vec3 savedPos,
                          float savedYaw,
                          float savedPitch,
                          /** Был ли админ невидимым ДО начала события — для toggle-логики при завершении. */
                          boolean adminWasInvisible,
                          // BLACK_RUSH-only: запомненная стартовая позиция за спиной жертвы
                          // и точка, куда админ откатывается перед броском.
                          Vec3 rushBehindPos,
                          Vec3 rushPullbackPos) {

        public Session(MiniEventType type, MiniEventState state, long startTick, long endTick,
                       boolean useDefaultPhrases, List<String> customPhrases,
                       UUID targetId, Vec3 savedPos, float savedYaw, float savedPitch,
                       boolean adminWasInvisible) {
            this(type, state, startTick, endTick, useDefaultPhrases, customPhrases,
                    targetId, savedPos, savedYaw, savedPitch, adminWasInvisible, null, null);
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Random RNG = new Random();

    private MiniEventController() {}

    // ============== ПУБЛИЧНЫЙ API ==============

    /** Простой выбор инвентика без конфигурации (Jumpscare через PREPARING). */
    public static void select(ServerPlayer admin, MiniEventType type) {
        if (!admin.hasPermissions(2)) return;
        long now = admin.serverLevel().getGameTime();
        SESSIONS.put(admin.getUUID(), new Session(
                type, MiniEventState.PREPARING, now, 0,
                true, Collections.emptyList(),
                null, null, 0f, 0f, false));
        broadcast(admin);
        // Без хардкода клавиш — HUD-оверлей покажет актуальные биндинги.
        admin.displayClientMessage(Component.literal(
                "Инвентик: " + type.displayName + " готов — см. подсказку клавиш справа."), true);
    }

    /** Запуск таргет-инвентика (LOOMING / FLICKER_PRESENCE) сразу в ACTIVE. */
    public static void launchTargeted(ServerPlayer admin, MiniEventType type, ServerPlayer target) {
        if (!admin.hasPermissions(2)) return;
        if (target == null) return;

        switch (type) {
            case LOOMING -> startLooming(admin, target);
            case FLICKER_PRESENCE -> startFlicker(admin, target);
            case BLACK_RUSH -> startBlackRush(admin, target);
            default -> admin.displayClientMessage(Component.literal(
                    "Этот инвентик не таргет-режим."), true);
        }
    }

    /**
     * SMOKE event lifecycle:
     *   ACTIVATE (или R/T при отсутствии сессии) → создаёт сессию (модель админа = чёрный силуэт).
     *   R (HIDE)  во время сессии → admin становится невидим (поверх чёрного силуэта).
     *   T (SHOW)  во время сессии → admin снова виден как чёрный силуэт.
     *   B (CANCEL) → деактивация ивента, модель в норму, invisibility снимается.
     */

    /** R-хоткей: сделать админа НЕВИДИМЫМ + дым. Работает ТОЛЬКО если SMOKE-ивент уже активен —
     *  иначе ничего не делает. Раньше R автоматически активировал ивент, что ломало другие
     *  механики (админ внезапно становился невидимым без чёрного силуэта). */
    public static void smokeHide(ServerPlayer admin) {
        if (!admin.hasPermissions(2)) return;
        if (!isSmokeActiveFor(admin)) {
            admin.displayClientMessage(Component.literal(
                    "SMOKE не активен. Открой меню инвентиков и выбери «Дым»."), true);
            return;
        }
        ServerLevel level = admin.serverLevel();
        spawnSmokePuff(level, admin);
        // ВСЕГДА снимаем перед добавлением — иначе при race с InfectionEffects.applyForLevel
        // или другим модом, который дёргает refreshes, addEffect мог быть отвергнут как
        // "уже есть равный" и на клиенте invis не применялся (баг "из 10 раз 9 без инвиза").
        admin.removeEffect(MobEffects.INVISIBILITY);
        admin.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        admin.displayClientMessage(Component.literal("Исчез в дыму"), true);
    }

    /** T-хоткей: сделать админа ВИДИМЫМ как чёрный силуэт + дым. Работает только если SMOKE активен. */
    public static void smokeShow(ServerPlayer admin) {
        if (!admin.hasPermissions(2)) return;
        if (!isSmokeActiveFor(admin)) {
            admin.displayClientMessage(Component.literal(
                    "SMOKE не активен. Открой меню инвентиков и выбери «Дым»."), true);
            return;
        }
        ServerLevel level = admin.serverLevel();
        spawnSmokePuff(level, admin);
        admin.removeEffect(MobEffects.INVISIBILITY);
        admin.displayClientMessage(Component.literal("Появился из дыма"), true);
    }

    private static boolean isSmokeActiveFor(ServerPlayer admin) {
        Session existing = SESSIONS.get(admin.getUUID());
        return existing != null
                && existing.type == MiniEventType.SMOKE
                && existing.state == MiniEventState.ACTIVE;
    }

    /** ACTION_SMOKE_ACTIVATE — клик из меню: только активация без invisibility-toggle.
     *  По умолчанию админ ВИДИМ как чёрный силуэт. */
    public static void smokeActivate(ServerPlayer admin) {
        if (!admin.hasPermissions(2)) return;
        Session existing = SESSIONS.get(admin.getUUID());
        if (existing != null && existing.type == MiniEventType.SMOKE
                && existing.state == MiniEventState.ACTIVE) {
            // Не пишем хардкод R/T/B — они могут быть переназначены. HUD-оверлей
            // покажет актуальные клавиши.
            admin.displayClientMessage(Component.literal(
                    "SMOKE уже активен."), true);
            return;
        }
        ServerLevel level = admin.serverLevel();
        ensureSmokeActive(admin, level);
        spawnSmokePuff(level, admin);
        admin.displayClientMessage(Component.literal(
                "SMOKE активирован — см. подсказку клавиш справа."), true);
    }

    private static void ensureSmokeActive(ServerPlayer admin, ServerLevel level) {
        Session existing = SESSIONS.get(admin.getUUID());
        if (existing != null && existing.type == MiniEventType.SMOKE
                && existing.state == MiniEventState.ACTIVE) {
            return;
        }
        long now = level.getGameTime();
        long farFuture = now + 24L * 60L * 60L * 20L;
        SESSIONS.put(admin.getUUID(), new Session(
                MiniEventType.SMOKE, MiniEventState.ACTIVE, now, farFuture,
                true, Collections.emptyList(),
                null, null, 0f, 0f, admin.hasEffect(MobEffects.INVISIBILITY)));
        broadcastToAllInLevel(admin);
    }

    private static void spawnSmokePuff(ServerLevel level, ServerPlayer admin) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                admin.getX(), admin.getY() + 1.0, admin.getZ(),
                40, 0.4, 0.9, 0.4, 0.04);
        level.sendParticles(ParticleTypes.SMOKE,
                admin.getX(), admin.getY() + 1.2, admin.getZ(),
                25, 0.3, 0.8, 0.3, 0.05);
    }

    /** Шлёт текущее состояние ВСЕМ игрокам уровня (для SMOKE — чёрный силуэт виден любому). */
    private static void broadcastToAllInLevel(ServerPlayer admin) {
        Session s = SESSIONS.get(admin.getUUID());
        if (s == null) return;
        long now = admin.serverLevel().getGameTime();
        int durationTicks = s.endTick > now ? (int) Math.min(s.endTick - now, Integer.MAX_VALUE) : 0;
        S2CMiniEventStatePacket pkt = new S2CMiniEventStatePacket(
                admin.getUUID(), s.type.ordinal(), s.state.ordinal(), durationTicks);
        for (ServerPlayer p : admin.serverLevel().players()) {
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
        }
    }

    /** Версия broadcastClear, шлёт всем игрокам уровня — для SMOKE event. */
    private static void broadcastClearToAll(ServerPlayer admin) {
        S2CMiniEventStatePacket pkt = new S2CMiniEventStatePacket(
                admin.getUUID(), MiniEventType.SMOKE.ordinal(),
                MiniEventState.IDLE.ordinal(), 0);
        for (ServerPlayer p : admin.serverLevel().players()) {
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
        }
    }

    /** Хоткей G — запуск из PREPARING. */
    public static boolean launch(ServerPlayer admin) {
        if (!admin.hasPermissions(2)) return false;
        Session s = SESSIONS.get(admin.getUUID());
        if (s == null || s.state != MiniEventState.PREPARING) {
            admin.displayClientMessage(Component.literal("Нет подготовленного инвентика."), true);
            return false;
        }

        switch (s.type) {
            case JUMPSCARE -> {
                if (!triggerJumpscare(admin, s)) {
                    admin.displayClientMessage(Component.literal(
                            "Нет целей в радиусе " + (int) RADIUS_BLOCKS + " блоков."), true);
                    return false;
                }
            }
            default -> {
                admin.displayClientMessage(Component.literal(
                        "Этот инвентик не запускается хоткеем."), true);
                return false;
            }
        }
        return true;
    }

    /** Хоткей B — отмена. PREPARING/ACTIVE → IDLE. Для таргет-инвентиков восстанавливаем позицию. */
    public static void cancel(ServerPlayer admin) {
        if (!admin.hasPermissions(2)) return;
        Session prev = SESSIONS.remove(admin.getUUID());
        if (prev == null) return;

        // Если был LOOMING / FLICKER / BLACK_RUSH — снимаем invisible, возвращаем позицию,
        // и применяем тот же toggle, что и при автозавершении.
        if (prev.savedPos != null && (prev.type == MiniEventType.LOOMING
                || prev.type == MiniEventType.FLICKER_PRESENCE
                || prev.type == MiniEventType.BLACK_RUSH)) {
            admin.setInvisible(false);
            admin.connection.teleport(prev.savedPos.x, prev.savedPos.y, prev.savedPos.z,
                    prev.savedYaw, prev.savedPitch);
            // Снимаем заморозку у жертвы BLACK_RUSH.
            if (prev.type == MiniEventType.BLACK_RUSH && prev.targetId != null) {
                ServerPlayer target = admin.server.getPlayerList().getPlayer(prev.targetId);
                if (target != null) {
                    target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    target.removeEffect(MobEffects.JUMP);
                    target.removeEffect(MobEffects.WEAKNESS);
                }
            }
            if (!prev.adminWasInvisible) {
                admin.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                        MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
            }
        }

        // SMOKE — сбрасываем invisibility (если она была от R-хоткея во время ивента).
        // adminWasInvisible хранит состояние ДО старта ивента; если до ивента уже был
        // невидим — оставляем как есть.
        if (prev.type == MiniEventType.SMOKE) {
            if (admin.hasEffect(MobEffects.INVISIBILITY) && !prev.adminWasInvisible) {
                admin.removeEffect(MobEffects.INVISIBILITY);
            }
            // SMOKE бродкастили всем уровню — clear тоже всем.
            broadcastClearToAll(admin);
            spawnSmokePuff(admin.serverLevel(), admin);
            admin.displayClientMessage(Component.literal("Дым рассеялся."), true);
            return;
        }

        broadcastClear(admin);
        admin.displayClientMessage(Component.literal("Инвентик отменён."), true);
    }

    /** Защита от множественного вызова tick() за один серверный тик: LevelTickEvent
     *  фирится для КАЖДОГО dimension (overworld/nether/end/...), и без этого FLICKER
     *  телепортировал админа N раз вместо одного, а BLACK_RUSH-фазы пролетали в N раз
     *  быстрее. Сохраняем оверворлдовский gameTime как «обработанный». */
    private static long lastTickGameTime = Long.MIN_VALUE;

    /** Tick — авто-завершение ACTIVE по таймеру + per-tick логика для FLICKER. */
    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        // Запускаем логику только на оверворлде — иначе одна сессия обрабатывается
        // многократно за server-tick. Если оверворлд почему-то не тикает (нет в server),
        // fallback: пускаем первое попавшееся измерение, но не повторно для одного gameTime.
        boolean isOverworld = level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
        if (!isOverworld && now == lastTickGameTime) return;
        lastTickGameTime = now;
        for (Map.Entry<UUID, Session> entry : new ArrayList<>(SESSIONS.entrySet())) {
            UUID adminId = entry.getKey();
            Session s = entry.getValue();
            if (s.state != MiniEventState.ACTIVE) continue;

            ServerPlayer admin = level.getServer().getPlayerList().getPlayer(adminId);
            if (admin == null) {
                SESSIONS.remove(adminId);
                continue;
            }

            // Per-tick логика FLICKER: каждые FLICKER_CYCLE_TICKS — новый телепорт.
            if (s.type == MiniEventType.FLICKER_PRESENCE && s.targetId != null) {
                long elapsed = now - s.startTick;
                if (elapsed > 0 && elapsed % FLICKER_CYCLE_TICKS == 0) {
                    ServerPlayer target = level.getServer().getPlayerList().getPlayer(s.targetId);
                    if (target != null) flickerTeleport(admin, target);
                }
            }

            // Per-tick логика BLACK_RUSH — поворот, отскок, рывок.
            if (s.type == MiniEventType.BLACK_RUSH && s.targetId != null) {
                tickBlackRush(admin, s, now);
            }

            if (now < s.endTick) continue;

            // ACTIVE завершилось.
            SESSIONS.remove(adminId);
            ServerLevel adminLevel = admin.serverLevel();
            broadcastClear(admin);

            if (s.type == MiniEventType.JUMPSCARE) {
                adminLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        admin.getX(), admin.getY() + 1.0, admin.getZ(),
                        50, 0.5, 1.0, 0.5, 0.05);
                adminLevel.sendParticles(ParticleTypes.SMOKE,
                        admin.getX(), admin.getY() + 1.2, admin.getZ(),
                        25, 0.4, 0.8, 0.4, 0.06);
                admin.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                        MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
            } else if (s.type == MiniEventType.BLACK_RUSH) {
                // Снимаем эффекты-заморозку у жертвы.
                if (s.targetId != null) {
                    ServerPlayer target = level.getServer().getPlayerList().getPlayer(s.targetId);
                    if (target != null) {
                        target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                        target.removeEffect(MobEffects.JUMP);
                        target.removeEffect(MobEffects.WEAKNESS);
                    }
                }
                admin.setInvisible(false);
                if (s.savedPos != null) {
                    admin.connection.teleport(s.savedPos.x, s.savedPos.y, s.savedPos.z,
                            s.savedYaw, s.savedPitch);
                }
                if (!s.adminWasInvisible) {
                    admin.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                            MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
                }
            } else if (s.type == MiniEventType.LOOMING || s.type == MiniEventType.FLICKER_PRESENCE) {
                // Снимаем entity-флаг
                admin.setInvisible(false);
                if (s.savedPos != null) {
                    admin.connection.teleport(s.savedPos.x, s.savedPos.y, s.savedPos.z,
                            s.savedYaw, s.savedPitch);
                }
                // Лёгкий дымок при возвращении.
                adminLevel.sendParticles(ParticleTypes.SMOKE,
                        admin.getX(), admin.getY() + 1.0, admin.getZ(),
                        15, 0.3, 0.6, 0.3, 0.04);

                // TOGGLE невидимости:
                //  - admin БЫЛ невидим до события → событие СНИМАЕТ невидимость (не выдаём)
                //  - admin НЕ БЫЛ невидим      → событие ВЫДАЁТ невидимость (бесконечно)
                if (!s.adminWasInvisible) {
                    admin.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                            MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
                }
                // Если был невидим — не возвращаем эффект, он остаётся снятым (см. startLooming/startFlicker).
            }
        }
    }

    public static MiniEventState stateOf(UUID adminId) {
        Session s = SESSIONS.get(adminId);
        return s == null ? MiniEventState.IDLE : s.state;
    }

    /** Является ли админ-сессия LOOMING/FLICKER/BLACK_RUSH на конкретного target — для рендер-mixin'ов. */
    public static boolean isTargetedAt(UUID adminId, UUID viewerId) {
        Session s = SESSIONS.get(adminId);
        if (s == null) return false;
        if (s.state != MiniEventState.ACTIVE) return false;
        if (s.type != MiniEventType.LOOMING
                && s.type != MiniEventType.FLICKER_PRESENCE
                && s.type != MiniEventType.BLACK_RUSH) return false;
        return viewerId.equals(s.targetId);
    }

    // ============== JUMPSCARE ==============

    private static boolean triggerJumpscare(ServerPlayer admin, Session s) {
        ServerLevel level = admin.serverLevel();
        List<ServerPlayer> targets = playersInRadius(admin);
        if (targets.isEmpty()) return false;

        double cx = 0, cy = 0, cz = 0;
        for (ServerPlayer t : targets) {
            cx += t.getX();
            cy += t.getY() + t.getEyeHeight();
            cz += t.getZ();
        }
        cx /= targets.size();
        cy /= targets.size();
        cz /= targets.size();
        faceTowards(admin, cx, cy, cz);

        long now = level.getGameTime();
        long endTick = now + DURATION_TICKS;
        SESSIONS.put(admin.getUUID(), new Session(
                MiniEventType.JUMPSCARE, MiniEventState.ACTIVE, now, endTick,
                s.useDefaultPhrases, s.customPhrases,
                null, null, 0f, 0f, false));
        broadcast(admin);
        return true;
    }

    // ============== LOOMING ==============

    private static void startLooming(ServerPlayer admin, ServerPlayer target) {
        ServerLevel level = admin.serverLevel();

        // Сохраняем позицию админа + флаг «был ли он невидим до события».
        Vec3 savedPos = admin.position();
        float savedYaw = admin.getYRot();
        float savedPitch = admin.getXRot();
        boolean wasInvisible = admin.hasEffect(MobEffects.INVISIBILITY);

        // Снимаем эффект на время события — иначе после event-end toggle не сработает корректно.
        if (wasInvisible) admin.removeEffect(MobEffects.INVISIBILITY);

        // Считаем точку 1.5 блока ЗА СПИНОЙ цели.
        Vec3 lookDir = target.getViewVector(1.0f);
        double behindX = target.getX() - lookDir.x * 1.5;
        double behindY = target.getY();
        double behindZ = target.getZ() - lookDir.z * 1.5;

        float yaw = target.getYRot();
        admin.setInvisible(true);  // entity flag — для других клиентов admin невидим во время события
        admin.connection.teleport(behindX, behindY, behindZ, yaw, 0f);

        long now = level.getGameTime();
        long endTick = now + LOOMING_DURATION_TICKS;
        SESSIONS.put(admin.getUUID(), new Session(
                MiniEventType.LOOMING, MiniEventState.ACTIVE, now, endTick,
                false, Collections.emptyList(),
                target.getUUID(), savedPos, savedYaw, savedPitch, wasInvisible));

        // Шлём состояние ТОЛЬКО ЖЕРТВЕ (другим не нужен силуэт).
        sendStateToTarget(admin, target);

        // Плюс forced приступ галлюцинаций с фразами «обернись».
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                new S2CForceHallucinationPacket(false, LOOMING_PHRASES, LOOMING_DURATION_TICKS));

        admin.displayClientMessage(Component.literal(
                "Гриф нависает над " + target.getGameProfile().getName()), true);
    }

    // ============== FLICKER_PRESENCE ==============

    private static void startFlicker(ServerPlayer admin, ServerPlayer target) {
        ServerLevel level = admin.serverLevel();

        Vec3 savedPos = admin.position();
        float savedYaw = admin.getYRot();
        float savedPitch = admin.getXRot();
        boolean wasInvisible = admin.hasEffect(MobEffects.INVISIBILITY);

        if (wasInvisible) admin.removeEffect(MobEffects.INVISIBILITY);

        admin.setInvisible(true);
        flickerTeleport(admin, target);  // первый телепорт сразу

        long now = level.getGameTime();
        long endTick = now + FLICKER_DURATION_TICKS;
        SESSIONS.put(admin.getUUID(), new Session(
                MiniEventType.FLICKER_PRESENCE, MiniEventState.ACTIVE, now, endTick,
                false, Collections.emptyList(),
                target.getUUID(), savedPos, savedYaw, savedPitch, wasInvisible));

        sendStateToTarget(admin, target);

        admin.displayClientMessage(Component.literal(
                "Мерцающий силуэт вокруг " + target.getGameProfile().getName()), true);
    }

    // ============== BLACK_RUSH ==============

    /**
     * Сценарный «бросок чёрной фигуры» на жертву. Полностью авто, ~2.8 сек:
     *   фаза 1 (0..28 ticks) — жертва замирает, админ телепается за спину или в свободное
     *      пространство 6 блоков; жертва плавно поворачивается лицом к нему.
     *   фаза 2 (28..36 ticks) — админ слегка отступает (-1 блок назад от линии «глаза-цель»).
     *   фаза 3 (36..48 ticks) — админ рывком летит на позицию жертвы (~2 блока в тик).
     *   завершение (48+) — админ возвращается на свою стартовую позицию, эффекты снимаются.
     */
    private static void startBlackRush(ServerPlayer admin, ServerPlayer target) {
        ServerLevel level = admin.serverLevel();

        Vec3 savedPos = admin.position();
        float savedYaw = admin.getYRot();
        float savedPitch = admin.getXRot();
        boolean wasInvisible = admin.hasEffect(MobEffects.INVISIBILITY);
        if (wasInvisible) admin.removeEffect(MobEffects.INVISIBILITY);

        // 1) Точка «за спиной» цели — 2.5 блока назад от её взгляда.
        Vec3 lookDir = target.getViewVector(1.0f);
        Vec3 behind = new Vec3(
                target.getX() - lookDir.x * 2.5,
                target.getY(),
                target.getZ() - lookDir.z * 2.5);
        // Проверка проходимости — если за спиной стена, ищем свободное место в радиусе 6.
        if (!isPositionFree(level, behind)) {
            Vec3 alt = findFreeNearby(level, target.position(), 6.0);
            if (alt != null) behind = alt;
        }

        // 2) Точка отскока — на 1.5 блока ДАЛЬШЕ от цели по линии цель→админ
        //    (т.е. админ как-бы откидывается чуть назад перед броском).
        Vec3 dirAwayFromTarget = behind.subtract(target.position());
        double awayLen = Math.max(0.001, dirAwayFromTarget.length());
        Vec3 pullback = new Vec3(
                behind.x + (dirAwayFromTarget.x / awayLen) * 1.5,
                behind.y,
                behind.z + (dirAwayFromTarget.z / awayLen) * 1.5);

        // 3) Замораживаем жертву: SLOWNESS 255 + JUMP_BOOST 128 (запрещает прыжок) на всё время.
        //    Дополнительно делаем игрока marker (cannot move). Эффекты снимутся в end-фазе.
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                RUSH_DURATION_TICKS + 5, 254, false, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.JUMP, RUSH_DURATION_TICKS + 5,
                128, false, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, RUSH_DURATION_TICKS + 5,
                4, false, false, false));

        // 4) Телепорт админа за спину, лицом к цели.
        admin.setInvisible(true);  // entity flag
        Vec3 toTarget = target.position().subtract(behind);
        float yawToTarget = (float) (Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0);
        admin.connection.teleport(behind.x, behind.y, behind.z, yawToTarget, 0f);

        long now = level.getGameTime();
        long endTick = now + RUSH_DURATION_TICKS;
        SESSIONS.put(admin.getUUID(), new Session(
                MiniEventType.BLACK_RUSH, MiniEventState.ACTIVE, now, endTick,
                false, Collections.emptyList(),
                target.getUUID(), savedPos, savedYaw, savedPitch, wasInvisible,
                behind, pullback));

        sendStateToTarget(admin, target);

        // Лёгкий дым на старт.
        level.sendParticles(ParticleTypes.SMOKE,
                behind.x, behind.y + 1.0, behind.z,
                10, 0.3, 0.5, 0.3, 0.02);

        admin.displayClientMessage(Component.literal(
                "Бросок на " + target.getGameProfile().getName()), true);
    }

    /** Per-tick логика BLACK_RUSH: поворот жертвы, отскок, рывок. */
    private static void tickBlackRush(ServerPlayer admin, Session s, long now) {
        if (s.targetId == null) return;
        ServerPlayer target = admin.server.getPlayerList().getPlayer(s.targetId);
        if (target == null) return;
        long elapsed = now - s.startTick;

        if (elapsed <= RUSH_TURN_END) {
            // Фаза 1: плавный поворот жертвы лицом к админу.
            Vec3 toAdmin = admin.position().subtract(target.position());
            float targetYaw = (float) (Math.toDegrees(Math.atan2(toAdmin.z, toAdmin.x)) - 90.0);
            float targetPitch = 0f;

            // Линейная интерполяция: от текущего yaw к targetYaw за 28 тиков.
            float t = Math.min(1f, elapsed / (float) RUSH_TURN_END);
            float curYaw = lerpAngle(target.getYRot(), targetYaw, 0.18f);
            float curPitch = lerpAngle(target.getXRot(), targetPitch, 0.12f);
            target.setYRot(curYaw);
            target.setXRot(curPitch);
            target.setYHeadRot(curYaw);
            // Force-sync на клиент: позиция та же, но yaw/pitch заменяем.
            target.connection.teleport(target.getX(), target.getY(), target.getZ(),
                    curYaw, curPitch);
        } else if (elapsed <= RUSH_PAUSE_END) {
            // Фаза 2: админ отступает к pullback-позиции.
            if (s.rushPullbackPos != null) {
                Vec3 toTarget = target.position().subtract(s.rushPullbackPos);
                float yaw = (float) (Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0);
                admin.connection.teleport(s.rushPullbackPos.x, s.rushPullbackPos.y,
                        s.rushPullbackPos.z, yaw, 0f);
            }
        } else if (elapsed <= RUSH_ATTACK_END) {
            // Фаза 3: рывок. Линейно интерполируем позицию админа от pullback к target.
            float t = (elapsed - RUSH_PAUSE_END) / (float) (RUSH_ATTACK_END - RUSH_PAUSE_END);
            t = Math.min(1f, Math.max(0f, t));
            // Easing — quad in для эффекта «разгона».
            float ease = t * t;
            Vec3 from = s.rushPullbackPos != null ? s.rushPullbackPos : s.rushBehindPos;
            if (from == null) return;
            Vec3 to = target.position();
            double cx = from.x + (to.x - from.x) * ease;
            double cy = from.y + (to.y - from.y) * ease;
            double cz = from.z + (to.z - from.z) * ease;
            Vec3 toTarget = to.subtract(new Vec3(cx, cy, cz));
            float yaw = (float) (Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0);
            admin.connection.teleport(cx, cy, cz, yaw, 0f);
            // На последнем тике — частицы импакта.
            if (elapsed == RUSH_ATTACK_END) {
                ServerLevel level = admin.serverLevel();
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        target.getX(), target.getY() + 1.0, target.getZ(),
                        30, 0.3, 0.6, 0.3, 0.05);
            }
        }
    }

    /** Линейный slerp для углов (учитывает 360°-обёртку). factor 0..1 = доля сдвига к target. */
    private static float lerpAngle(float current, float target, float factor) {
        float diff = ((target - current) % 360f + 540f) % 360f - 180f;
        return current + diff * factor;
    }

    private static boolean isPositionFree(ServerLevel level, Vec3 pos) {
        var bp = new net.minecraft.core.BlockPos((int) Math.floor(pos.x),
                (int) Math.floor(pos.y), (int) Math.floor(pos.z));
        return level.getBlockState(bp).isAir() && level.getBlockState(bp.above()).isAir();
    }

    private static Vec3 findFreeNearby(ServerLevel level, Vec3 origin, double radius) {
        // Пробуем 8 направлений по горизонту на дистанции radius.
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            Vec3 candidate = new Vec3(
                    origin.x + Math.cos(angle) * radius,
                    origin.y,
                    origin.z + Math.sin(angle) * radius);
            if (isPositionFree(level, candidate)) return candidate;
        }
        return null;
    }

    /** Телепортирует админа в случайную точку 5..12 блоков от target, лицом к нему. */
    private static void flickerTeleport(ServerPlayer admin, ServerPlayer target) {
        double angle = RNG.nextDouble() * Math.PI * 2;
        double dist = 5.0 + RNG.nextDouble() * 7.0;
        double newX = target.getX() + Math.cos(angle) * dist;
        double newZ = target.getZ() + Math.sin(angle) * dist;
        double newY = target.getY();

        // Лицом к цели.
        double dx = target.getX() - newX;
        double dz = target.getZ() - newZ;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);

        admin.connection.teleport(newX, newY, newZ, yaw, 0f);
    }

    // ============== УТИЛИТЫ ==============

    private static List<ServerPlayer> playersInRadius(ServerPlayer admin) {
        List<ServerPlayer> targets = new ArrayList<>();
        for (ServerPlayer p : admin.serverLevel().players()) {
            if (p == admin) continue;
            if (p.distanceToSqr(admin) > RADIUS_SQ) continue;
            targets.add(p);
        }
        return targets;
    }

    /** Шлёт состояние сессии всем игрокам в радиусе (для радиус-инвентиков). */
    private static void broadcast(ServerPlayer admin) {
        Session s = SESSIONS.get(admin.getUUID());
        if (s == null) {
            broadcastClear(admin);
            return;
        }
        long now = admin.serverLevel().getGameTime();
        int durationTicks = s.endTick > now ? (int) (s.endTick - now) : 0;
        S2CMiniEventStatePacket pkt = new S2CMiniEventStatePacket(
                admin.getUUID(), s.type.ordinal(), s.state.ordinal(), durationTicks);

        if (s.targetId != null) {
            // Таргет-инвентик: шлём ТОЛЬКО жертве + админу (для своего clientside-state).
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> admin), pkt);
            ServerPlayer t = admin.server.getPlayerList().getPlayer(s.targetId);
            if (t != null) Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> t), pkt);
        } else {
            sendInRadius(admin, pkt);
        }
    }

    /** Версия broadcast() для случаев когда у нас уже есть target. */
    private static void sendStateToTarget(ServerPlayer admin, ServerPlayer target) {
        Session s = SESSIONS.get(admin.getUUID());
        if (s == null) return;
        long now = admin.serverLevel().getGameTime();
        int durationTicks = s.endTick > now ? (int) (s.endTick - now) : 0;
        S2CMiniEventStatePacket pkt = new S2CMiniEventStatePacket(
                admin.getUUID(), s.type.ordinal(), s.state.ordinal(), durationTicks);
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> admin), pkt);
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), pkt);
    }

    private static void broadcastClear(ServerPlayer admin) {
        S2CMiniEventStatePacket pkt = new S2CMiniEventStatePacket(
                admin.getUUID(), 0, MiniEventState.IDLE.ordinal(), 0);
        for (ServerPlayer p : admin.serverLevel().players()) {
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
        }
    }

    private static void sendInRadius(ServerPlayer admin, S2CMiniEventStatePacket pkt) {
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> admin), pkt);
        for (ServerPlayer p : admin.serverLevel().players()) {
            if (p == admin) continue;
            if (p.distanceToSqr(admin) > RADIUS_SQ) continue;
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
        }
    }

    private static void faceTowards(ServerPlayer admin, double x, double y, double z) {
        double dx = x - admin.getX();
        double dy = y - (admin.getY() + admin.getEyeHeight());
        double dz = z - admin.getZ();
        double dHoriz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dHoriz));

        admin.setYRot(yaw);
        admin.setXRot(pitch);
        admin.setYHeadRot(yaw);
        admin.connection.teleport(admin.getX(), admin.getY(), admin.getZ(), yaw, pitch);
    }
}
