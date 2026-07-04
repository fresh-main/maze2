package com.labyrinthmod.common.util;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.core.BlockPos;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Отдельный логгер мода — пишет в config/labyrinthmod/logs/YYYY-MM-DD.log
 * Не засоряет основные логи Minecraft.
 */
public class ModLogger {

    private static BufferedWriter writer;
    private static String currentDate = null;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static void init() {
        rotateIfNeeded();
        log("BOOT", "ModLogger initialized");
    }

    private static synchronized void rotateIfNeeded() {
        String today = LocalDate.now().format(DATE_FMT);
        if (today.equals(currentDate) && writer != null) return;

        closeSilent();
        try {
            Path dir = Paths.get(System.getProperty("user.dir"), "config", LabyrinthMod.MOD_ID, "logs");
            Files.createDirectories(dir);
            writer = new BufferedWriter(new FileWriter(dir.resolve(today + ".log").toFile(), true));
            currentDate = today;
        } catch (IOException e) {
            LabyrinthMod.LOGGER.error("Cannot create mod log file", e);
            writer = null;
        }
    }

    private static void closeSilent() {
        if (writer != null) {
            try { writer.close(); } catch (IOException ignored) {}
            writer = null;
        }
    }

    public static synchronized void log(String category, String message) {
        rotateIfNeeded();
        if (writer == null) return;
        try {
            writer.write("[" + LocalTime.now().format(TIME_FMT) + "] [" + category + "] " + message);
            writer.newLine();
            writer.flush();
        } catch (IOException ignored) {}
    }

    // ========== Шорткаты по категориям ==========

    public static void stuck(String griverId, BlockPos pos, int stuckTicks, int stage, String extra) {
        log("STUCK", "griver=" + shortId(griverId)
                + " pos=" + posStr(pos)
                + " ticks=" + stuckTicks
                + " stage=" + stage
                + (extra == null || extra.isEmpty() ? "" : " " + extra));
    }

    public static void path(String griverId, BlockPos from, BlockPos to, int len, String note) {
        log("PATH", "griver=" + shortId(griverId)
                + " " + posStr(from) + "→" + posStr(to)
                + " len=" + len
                + (note == null || note.isEmpty() ? "" : " " + note));
    }

    public static void pathFail(String griverId, BlockPos from, BlockPos to, String reason) {
        log("PATH-FAIL", "griver=" + shortId(griverId)
                + " " + posStr(from) + "→" + posStr(to)
                + " reason=" + reason);
    }

    public static void patrol(String event, String details) {
        log("PATROL", event + (details == null || details.isEmpty() ? "" : ": " + details));
    }

    public static void admin(String playerName, String action, String details) {
        log("ADMIN", "player=" + playerName + " action=" + action
                + (details == null || details.isEmpty() ? "" : " " + details));
    }

    public static void info(String message) { log("INFO", message); }
    public static void warn(String message) { log("WARN", message); }
    public static void error(String message) { log("ERROR", message); }

    private static String shortId(String uuid) {
        if (uuid == null) return "?";
        return uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
    }

    private static String posStr(BlockPos p) {
        return p == null ? "null" : (p.getX() + "," + p.getY() + "," + p.getZ());
    }
}
