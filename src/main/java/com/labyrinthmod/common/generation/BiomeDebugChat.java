package com.labyrinthmod.common.generation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class BiomeDebugChat {

    private static volatile MinecraftServer server;

    private static final AtomicBoolean enabled = new AtomicBoolean(true);

    private static final Map<String, Long> lastTimes = new ConcurrentHashMap<>();
    private static final Queue<String> pending = new ConcurrentLinkedQueue<>();

    public static void setServer(MinecraftServer newServer) {
        server = newServer;

        if (newServer != null) {
            flushPending();
        }
    }

    public static boolean isEnabled() {
        return enabled.get();
    }

    public static void setEnabled(boolean value) {
        enabled.set(value);
    }

    public static void clear() {
        lastTimes.clear();
        pending.clear();
    }

    public static void raw(String message) {
        if (!enabled.get()) {
            return;
        }

        System.out.println("[LabyDebug] " + message);
        broadcast(message);
    }

    public static void timed(String key, String message, long intervalMs) {
        if (!enabled.get()) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = lastTimes.get(key);

        if (last != null && now - last < intervalMs) {
            return;
        }

        lastTimes.put(key, now);
        raw(message);
    }

    private static void broadcast(String message) {
        MinecraftServer target = server;

        if (target == null) {
            if (pending.size() < 200) {
                pending.add(message);
            }
            return;
        }

        target.execute(() -> {
            if (!target.isRunning()) {
                return;
            }

            target.getPlayerList().broadcastSystemMessage(
                    Component.literal("[LabyDebug] " + message).withStyle(ChatFormatting.GRAY),
                    false
            );
        });
    }

    private static void flushPending() {
        MinecraftServer target = server;

        if (target == null) {
            return;
        }

        int sent = 0;
        String message;

        while (sent < 50 && (message = pending.poll()) != null) {
            final String finalMessage = message;

            target.execute(() -> {
                if (!target.isRunning()) {
                    return;
                }

                target.getPlayerList().broadcastSystemMessage(
                        Component.literal("[LabyDebug] " + finalMessage).withStyle(ChatFormatting.GRAY),
                        false
                );
            });

            sent++;
        }
    }
}