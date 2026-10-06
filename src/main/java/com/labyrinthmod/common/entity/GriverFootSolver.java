package com.labyrinthmod.common.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class GriverFootSolver {

    public static final int LEGS = 6;

    private static final double[] R = {0.7, -0.9, 0.75, -0.9, 0.7, -0.9};
    private static final double[] F = {-1.3, -1.3, 0.3, 0.35, -0.45, -0.45};
    private static final float MAX_TILT = (float) Math.toRadians(12.0);
    private static final double RAY_UP = 0.6;
    private static final double RAY_DOWN = 1.5;
    private static final double MAX_SHIFT = 0.6;
    private static final double MAX_LEG = 1.0;
    private static final float SMOOTH = 0.25f;

    public final double[] legOffset = new double[LEGS];
    public float pitch;
    public float roll;
    public double bodyShift;

    private float lastTime = -1f;

    public void update(GriverEntity e, float partialTick) {
        float time = e.tickCount + partialTick;
        if (time == lastTime) return;
        lastTime = time;

        float targetPitch = 0f;
        float targetRoll = 0f;
        double targetShift = 0.0;
        double[] targetLeg = new double[LEGS];

        if (e.onGround() && e.getGroundAnimationOffset(partialTick) == 0.0F) {
            double yaw = Math.toRadians(Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot));
            double sin = Math.sin(yaw);
            double cos = Math.cos(yaw);
            double ox = Mth.lerp(partialTick, e.xOld, e.getX());
            double oy = Mth.lerp(partialTick, e.yOld, e.getY());
            double oz = Mth.lerp(partialTick, e.zOld, e.getZ());

            double[] g = new double[LEGS];
            double rm = 0, fm = 0, gm = 0;
            for (int i = 0; i < LEGS; i++) {
                double wx = ox - R[i] * cos - F[i] * sin;
                double wz = oz - R[i] * sin + F[i] * cos;
                Vec3 from = new Vec3(wx, oy + RAY_UP, wz);
                Vec3 to = new Vec3(wx, oy - RAY_DOWN, wz);
                BlockHitResult hit = e.level().clip(new ClipContext(from, to,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
                if (hit.getType() == HitResult.Type.MISS) g[i] = -RAY_DOWN;
                else if (hit.isInside()) g[i] = 0.0;
                else g[i] = hit.getLocation().y - oy;
                rm += R[i];
                fm += F[i];
                gm += g[i];
            }
            rm /= LEGS;
            fm /= LEGS;
            gm /= LEGS;

            double srr = 0, sff = 0, srf = 0, srg = 0, sfg = 0;
            for (int i = 0; i < LEGS; i++) {
                double dr = R[i] - rm;
                double df = F[i] - fm;
                double dg = g[i] - gm;
                srr += dr * dr;
                sff += df * df;
                srf += dr * df;
                srg += dr * dg;
                sfg += df * dg;
            }
            double det = srr * sff - srf * srf;
            if (Math.abs(det) > 1.0E-6) {
                double a = (srg * sff - srf * sfg) / det;
                double b = (srr * sfg - srf * srg) / det;
                targetRoll = Mth.clamp((float) Math.atan(a), -MAX_TILT, MAX_TILT);
                targetPitch = Mth.clamp((float) Math.atan(b), -MAX_TILT, MAX_TILT);
            }

            double ta = Math.tan(targetRoll);
            double tb = Math.tan(targetPitch);
            double shift = 0.0;
            for (int i = 0; i < LEGS; i++) shift += g[i] - ta * R[i] - tb * F[i];
            targetShift = Mth.clamp(shift / LEGS, -MAX_SHIFT, MAX_SHIFT);
            for (int i = 0; i < LEGS; i++) {
                targetLeg[i] = Mth.clamp(g[i] - ta * R[i] - tb * F[i] - targetShift, -MAX_LEG, MAX_LEG);
            }
        }

        pitch = Mth.lerp(SMOOTH, pitch, targetPitch);
        roll = Mth.lerp(SMOOTH, roll, targetRoll);
        bodyShift = Mth.lerp(SMOOTH, bodyShift, targetShift);
        for (int i = 0; i < LEGS; i++) {
            legOffset[i] = Mth.lerp(SMOOTH, legOffset[i], targetLeg[i]);
        }
    }
}