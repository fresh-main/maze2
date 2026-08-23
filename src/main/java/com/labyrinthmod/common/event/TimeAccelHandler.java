package com.labyrinthmod.common.event;

import com.labyrinthmod.client.TimeAccelScreen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class TimeAccelHandler {

    // Множитель: >1 = ускорение, <1 = замедление, 1 = обычно
    public static volatile double speed = 1.0;

    private static double virtualTime = -1;
    private static long lastApplied = -1;

    public static void setSpeed(double s) {
        speed = Math.max(0.05, Math.min(1000, s));
        virtualTime = -1;
        lastApplied = -1;
    }

    public static String formatStatus() {
        if (speed == 1.0) return "§aВремя: обычное";
        if (speed > 1) return "§aУскорение x" + speed;
        return "§aЗамедление x" + speed;
    }

    // ===== ОДНА КОМАНДА НА ВСЁ =====
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("timecontrol")
                .executes(ctx -> openScreen())
                .then(Commands.argument("mult", DoubleArgumentType.doubleArg(0.05, 1000))
                        .executes(ctx -> {
                            setSpeed(DoubleArgumentType.getDouble(ctx, "mult"));
                            ctx.getSource().sendSuccess(() -> Component.literal(formatStatus()), true);
                            return 1;
                        })));

        // Алиасы по старым названиям
        d.register(Commands.literal("timeaccel").executes(ctx -> openScreen()));
        d.register(Commands.literal("timeslow").executes(ctx -> openScreen()));
    }

    private static int openScreen() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.setScreen(new TimeAccelScreen()));
        }
        return 1;
    }

    // ===== ПЛАВНОЕ И УСКОРЕНИЕ, И ЗАМЕДЛЕНИЕ =====
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;
        if (!event.level.dimension().equals(Level.OVERWORLD)) return;

        if (speed == 1.0) {
            virtualTime = -1;
            lastApplied = -1;
            return;
        }

        long current = serverLevel.getDayTime();
        // Если время поменяли извне (/time set, сон) — синхронизируемся
        if (virtualTime < 0 || Math.abs(current - lastApplied) > 4) {
            virtualTime = current;
        }

        // virtualTime += speed: при speed>1 время летит быстрее,
        // при speed<1 — ползёт медленнее, всегда ПЛАВНО
        virtualTime += speed;
        long target = (long) virtualTime;
        serverLevel.setDayTime(target);
        lastApplied = target;
    }
}