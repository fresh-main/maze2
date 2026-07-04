package com.infection.client.minievent;

import com.infection.event.MiniEventState;
import com.infection.event.MiniEventType;
import com.infection.sound.InfectionModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиентский state per admin UUID. Используется:
 *   - {@link com.infection.client.render.MiniEventBlackLayer} (чёрный силуэт админа)
 *   - {@link JumpscareOverlay} (вспышка экрана)
 *
 * При переходе в ACTIVE из любого состояния — играем jumpscare.ogg один раз.
 */
public final class ClientMiniEventState {

    public record Snapshot(MiniEventType type, MiniEventState state, long activeStartMs, long activeEndMs) {}

    private static final Map<UUID, Snapshot> BY_ADMIN = new ConcurrentHashMap<>();

    private ClientMiniEventState() {}

    /** Hard-cap длительности ACTIVE для ВСЕХ типов мини-ивентов кроме SMOKE/HALLUCINATION_SURGE.
     *  Раньше клиент верил серверу даже если durationTicks был ошибочно большим (race / повторный
     *  пакет / старая сессия) → стробоскоп JUMPSCARE играл вечно. */
    private static final long MAX_ACTIVE_DURATION_MS = 8000L;

    public static void apply(UUID adminId, MiniEventType type, MiniEventState state, int durationTicks) {
        Snapshot prev = BY_ADMIN.get(adminId);
        long now = System.currentTimeMillis();

        if (state == MiniEventState.IDLE) {
            BY_ADMIN.remove(adminId);
            return;
        }

        // КРИТИЧНО: если предыдущее состояние было ACTIVE с тем же типом, не сбрасываем
        // endMs «вперёд» — иначе повторный broadcast (или несколько подряд за один тик)
        // двигает время окончания и стробоскоп играет вечно. Считаем ACTIVE→ACTIVE
        // ИДЕМПОТЕНТНЫМ: сохраняем оба startMs и endMs из prev.
        if (state == MiniEventState.ACTIVE && prev != null
                && prev.state == MiniEventState.ACTIVE && prev.type == type) {
            // Никаких изменений в snapshot — игнорируем дубликат.
            // Также НЕ играем звук повторно.
            return;
        }

        long startMs = (prev != null && prev.state == MiniEventState.ACTIVE) ? prev.activeStartMs : now;
        long endMs;
        if (state == MiniEventState.ACTIVE) {
            // Sanity-cap: длительность не может быть больше MAX_ACTIVE_DURATION_MS для типов
            // с ожидаемой короткой ACTIVE-фазой (JUMPSCARE/LOOMING/FLICKER/BLACK_RUSH).
            // SMOKE намеренно длится «бесконечно» (до ручного выхода) — для него cap не применяем.
            long requested = (long) durationTicks * 50L;
            long maxAllowed = type == MiniEventType.SMOKE ? Long.MAX_VALUE : MAX_ACTIVE_DURATION_MS;
            long clamped = Math.max(0L, Math.min(requested, maxAllowed));
            endMs = now + clamped;
        } else {
            endMs = 0L;
        }
        BY_ADMIN.put(adminId, new Snapshot(type, state, startMs, endMs));

        // Вход в ACTIVE — играем sound (один раз).
        if (state == MiniEventState.ACTIVE && (prev == null || prev.state != MiniEventState.ACTIVE)) {
            playSoundForType(type);
        }
    }

    private static void playSoundForType(MiniEventType type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        try {
            switch (type) {
                case JUMPSCARE -> mc.getSoundManager().play(
                        SimpleSoundInstance.forUI(InfectionModSounds.JUMPSCARE.get(), 1.0f, 1.0f));
            }
        } catch (Throwable ignored) {}
    }

    /** True если у этого админа надо рисовать чёрный силуэт (PREPARING или ACTIVE
     *  + тип инвентика поддерживает силуэт). */
    public static boolean shouldRenderBlack(UUID adminId) {
        Snapshot s = BY_ADMIN.get(adminId);
        if (s == null) return false;
        if (s.state == MiniEventState.IDLE) return false;
        return s.type != null && s.type.hasSilhouette;
    }

    /** Возвращает текущий снапшот для конкретного админа или null. */
    public static Snapshot get(UUID adminId) {
        return BY_ADMIN.get(adminId);
    }

    /** Состояние jumpscare-вспышки локального экрана: 0..1 = active progress, -1 = неактивно. */
    public static float jumpscareProgress() {
        long now = System.currentTimeMillis();
        for (Snapshot s : BY_ADMIN.values()) {
            if (s.state == MiniEventState.ACTIVE && s.type == MiniEventType.JUMPSCARE
                    && now < s.activeEndMs) {
                long total = s.activeEndMs - s.activeStartMs;
                if (total <= 0) return -1f;
                return (now - s.activeStartMs) / (float) total;
            }
        }
        return -1f;
    }

    public static void clear() {
        BY_ADMIN.clear();
    }
}
