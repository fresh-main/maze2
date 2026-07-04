package com.mazemap.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.List;

/**
 * Клиентское HUD-состояние:
 *  - noteHeld — игрок держит «записку с картой» в руке.
 *  - marker — точка-цель на карте (worldBlockX, worldBlockZ). Установлена
 *    правым кликом по карте; если null — метки нет.
 *  - path — список (worldBlockX, worldBlockZ) клеток от игрока до метки,
 *    построенный BFS по explored пиксельным фрагментам. Пустой если путь
 *    не найден (метка за пределами прорисовки или недостижима).
 *
 * Мини-карта НЕ имеет своего флага — она hold-to-show через
 * MazeMapKeyBindings.HOLD_MINIMAP.isDown().
 */
@OnlyIn(Dist.CLIENT)
public final class ClientHudState {
    private static volatile boolean noteHeld = false;

    /** Метка-цель. Sentinel: Integer.MIN_VALUE == метка отсутствует. */
    private static volatile int markerBlockX = Integer.MIN_VALUE;
    private static volatile int markerBlockZ = Integer.MIN_VALUE;

    /** Путь от игрока до метки. Immutable list. */
    private static volatile List<int[]> path = Collections.emptyList();

    /** true если последний поиск пути не нашёл маршрут (для UI-сообщения). */
    private static volatile boolean pathUnreachable = false;

    private ClientHudState() {}

    // === Note (записка в руке) ===
    public static boolean isNoteHeld() { return noteHeld; }
    public static void setNoteHeld(boolean v) { noteHeld = v; }

    // === Marker ===
    public static boolean hasMarker() {
        return markerBlockX != Integer.MIN_VALUE;
    }
    public static int getMarkerX() { return markerBlockX; }
    public static int getMarkerZ() { return markerBlockZ; }
    public static void setMarker(int x, int z) {
        markerBlockX = x;
        markerBlockZ = z;
    }
    public static void clearMarker() {
        markerBlockX = Integer.MIN_VALUE;
        markerBlockZ = Integer.MIN_VALUE;
        path = Collections.emptyList();
        pathUnreachable = false;
    }

    // === Path ===
    public static List<int[]> getPath() { return path; }
    public static void setPath(List<int[]> p) {
        path = (p == null) ? Collections.emptyList() : p;
        pathUnreachable = path.isEmpty();
    }
    public static boolean isPathUnreachable() { return pathUnreachable; }
}
