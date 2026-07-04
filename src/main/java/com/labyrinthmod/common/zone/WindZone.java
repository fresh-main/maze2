package com.labyrinthmod.common.zone;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class WindZone {

    public final UUID id;
    public final String name;
    public final BlockPos pos1;
    public final BlockPos pos2;
    public final int minX, minY, minZ, maxX, maxY, maxZ;
    public final int windStrength;
    public final int pushForce;

    private boolean active = true;
    private int cooldown = 0;
    private final Set<Long> triggeredBlocks = new HashSet<>();
    private final Set<Long> currentBlocksScratch = new HashSet<>();

    private final Map<Long, DustCloud> activeDustClouds = new HashMap<>();

    private static class DustCloud {
        int lifetime;
        float size;
        float alpha;
        Vec3 center;

        DustCloud(Vec3 center, int lifetime, float size) {
            this.center = center;
            this.lifetime = lifetime;
            this.size = size;
            this.alpha = 1.0f;
        }
    }

    public WindZone(String name, BlockPos pos1, BlockPos pos2, int windStrength, int pushForce) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.minX = Math.min(pos1.getX(), pos2.getX());
        this.minY = Math.min(pos1.getY(), pos2.getY());
        this.minZ = Math.min(pos1.getZ(), pos2.getZ());
        this.maxX = Math.max(pos1.getX(), pos2.getX());
        this.maxY = Math.max(pos1.getY(), pos2.getY());
        this.maxZ = Math.max(pos1.getZ(), pos2.getZ());
        this.windStrength = Math.max(1, Math.min(10, windStrength));
        this.pushForce = Math.max(1, Math.min(5, pushForce));
    }

    public boolean isInside(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX &&
                pos.getY() >= minY && pos.getY() <= maxY &&
                pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public boolean isInside(Entity entity) {
        return entity.getX() >= minX && entity.getX() <= maxX &&
                entity.getY() >= minY && entity.getY() <= maxY &&
                entity.getZ() >= minZ && entity.getZ() <= maxZ;
    }

    private static long toLong(BlockPos pos) {
        return ((long) pos.getX() & 0x3FFFFFFL) << 38 |
                ((long) pos.getY() & 0xFFFL) << 26 |
                ((long) pos.getZ() & 0x3FFFFFFL);
    }

    private static BlockPos fromLong(long key) {
        int x = (int) (key >> 38);
        int y = (int) ((key >> 26) & 0xFFFL);
        int z = (int) (key & 0x3FFFFFFL);
        if (x >= 0x2000000) x -= 0x4000000;
        if (z >= 0x2000000) z -= 0x4000000;
        if (y >= 0x800) y -= 0x1000;
        return new BlockPos(x, y, z);
    }

    public void tick(Level level) {
        if (!active) return;
        if (level.isClientSide) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        updateDustClouds(serverLevel);

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        currentBlocksScratch.clear();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = serverLevel.getBlockState(new BlockPos(x, y, z));

                    if (!state.isAir()) {
                        long key = toLong(new BlockPos(x, y, z));
                        currentBlocksScratch.add(key);

                        if (!triggeredBlocks.contains(key)) {
                            triggerDustEffect(serverLevel, new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }

        triggeredBlocks.clear();
        triggeredBlocks.addAll(currentBlocksScratch);
        cooldown = 1;
    }

    private void updateDustClouds(ServerLevel level) {
        Iterator<Map.Entry<Long, DustCloud>> it = activeDustClouds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, DustCloud> entry = it.next();
            DustCloud cloud = entry.getValue();
            cloud.lifetime--;

            if (cloud.lifetime <= 0) {
                it.remove();
            } else {
                float progress = 1.0f - (float) cloud.lifetime / 60f;
                cloud.size = 1.0f + progress * 2.5f;
                cloud.alpha = 1.0f - progress * 0.7f;
                addCloudParticles(level, entry.getKey(), cloud);
            }
        }
    }

    private void addCloudParticles(ServerLevel level, long posKey, DustCloud cloud) {
        BlockPos pos = fromLong(posKey);
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        int particleCount = (int) (10 * cloud.alpha) + 3;

        for (int i = 0; i < particleCount; i++) {
            double angle = rand.nextDouble() * 2 * Math.PI;
            double radius = rand.nextDouble() * cloud.size;
            double dx = Math.cos(angle) * radius;
            double dz = Math.sin(angle) * radius;
            double dy = rand.nextDouble() * 1.5 - 0.5;

            // Тяжёлая оседающая пыль
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    pos.getX() + 0.5 + dx,
                    pos.getY() + 0.5 + dy,
                    pos.getZ() + 0.5 + dz,
                    1,
                    rand.nextDouble() * 0.1 - 0.05,
                    -0.02,
                    rand.nextDouble() * 0.1 - 0.05,
                    0.01);
        }
    }

    private void triggerDustEffect(ServerLevel level, BlockPos blockPos) {
        Vec3 center = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        // 1. Основной выброс пыли (без звука)
        int mainBurstCount = 60 + (windStrength * 8);
        for (int i = 0; i < mainBurstCount; i++) {
            double angle = rand.nextDouble() * 2 * Math.PI;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double dy = (rand.nextDouble() - 0.5) * 0.8;

            double dist = rand.nextDouble() * 2.5;
            double px = center.x + dx * dist;
            double pz = center.z + dz * dist;
            double py = center.y + 0.5 + dy * dist;

            // Основные частицы - густая пыль
            level.sendParticles(ParticleTypes.CLOUD, px, py, pz, 1,
                    dx * 0.3, dy * 0.1, dz * 0.3, 0.03);
        }

        // 2. Медленно поднимающаяся пыль (атмосферная)
        int risingDust = 25 + windStrength * 3;
        for (int i = 0; i < risingDust; i++) {
            double angle = rand.nextDouble() * 2 * Math.PI;
            double radius = rand.nextDouble() * 1.5;
            double dx = Math.cos(angle) * radius;
            double dz = Math.sin(angle) * radius;

            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    center.x + dx,
                    center.y + 0.2,
                    center.z + dz,
                    1,
                    dx * 0.08,
                    0.08 + rand.nextDouble() * 0.05,
                    dz * 0.08,
                    0.02);
        }

        // 3. Тяжёлая пыль, оседающая вниз
        int heavyDust = 30 + windStrength * 4;
        for (int i = 0; i < heavyDust; i++) {
            double angle = rand.nextDouble() * 2 * Math.PI;
            double radius = rand.nextDouble() * 2;
            double dx = Math.cos(angle) * radius;
            double dz = Math.sin(angle) * radius;

            level.sendParticles(ParticleTypes.POOF,
                    center.x + dx,
                    center.y + 1.0,
                    center.z + dz,
                    1,
                    dx * 0.1,
                    -0.03,
                    dz * 0.1,
                    0.01);
        }

        // 4. Направленный выброс (если есть дверь)
        BlockPos doorPos = findDoorDirection(level, blockPos);
        if (doorPos != null) {
            Vec3 doorCenter = new Vec3(doorPos.getX() + 0.5, doorPos.getY() + 0.5, doorPos.getZ() + 0.5);
            Vec3 direction = doorCenter.subtract(center).normalize();

            int directionalParticles = 25 + windStrength * 5;
            for (int i = 0; i < directionalParticles; i++) {
                double spread = 0.5;
                double dx = direction.x + (rand.nextDouble() - 0.5) * spread;
                double dz = direction.z + (rand.nextDouble() - 0.5) * spread;
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len > 0) {
                    dx /= len;
                    dz /= len;
                }

                double dist = 0.5 + rand.nextDouble() * 1.5;
                double px = center.x + dx * dist;
                double pz = center.z + dz * dist;
                double py = center.y + 0.5 + (rand.nextDouble() - 0.5);

                level.sendParticles(ParticleTypes.POOF, px, py, pz, 1,
                        dx * 0.4, 0.05, dz * 0.4, 0.02);
            }
        }

        // 5. Персистентное облако пыли (висит 3-4 секунды)
        long key = toLong(blockPos);
        int cloudLifetime = 50 + windStrength * 4;
        float cloudSize = 1.2f + windStrength * 0.2f;
        activeDustClouds.put(key, new DustCloud(center, cloudLifetime, cloudSize));

        // 6. Лёгкое отбрасывание (без ударной волны)
        gentlePush(level, blockPos, center);

        LabyrinthMod.LOGGER.debug("[WindZone] Dust burst at {} (strength: {})", blockPos, windStrength);
    }

    private void gentlePush(ServerLevel level, BlockPos blockPos, Vec3 center) {
        double pushRadius = 2.0 + windStrength * 0.3;
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(blockPos).inflate(pushRadius)).forEach(entity -> {

            Vec3 toEntity = entity.position().subtract(center);
            double distance = Math.max(0.1, toEntity.length());
            Vec3 pushDirection = toEntity.normalize();

            // Мягкое отталкивание, не резкое
            double force = (1.0 - Math.min(1.0, distance / pushRadius)) * pushForce * 0.8;

            Vec3 currentVel = entity.getDeltaMovement();
            entity.setDeltaMovement(
                    currentVel.x + pushDirection.x * force,
                    currentVel.y + 0.1,
                    currentVel.z + pushDirection.z * force
            );

            entity.hurtMarked = true;

            // Небольшое облачко пыли на сущности
            for (int i = 0; i < 4; i++) {
                level.sendParticles(ParticleTypes.POOF,
                        entity.getX(), entity.getY() + 0.5, entity.getZ(),
                        1,
                        (rand.nextDouble() - 0.5) * 0.3,
                        0.05,
                        (rand.nextDouble() - 0.5) * 0.3,
                        0.01);
            }
        });
    }

    private BlockPos findDoorDirection(ServerLevel level, BlockPos blockPos) {
        String[] doorKeywords = {
                "door", "gate", "hatch", "shutter", "trapdoor",
                "mechanical_piston", "gantry", "rolling_door"
        };

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = blockPos.relative(dir);
            BlockState state = level.getBlockState(neighbor);
            String blockName = state.getBlock().getDescriptionId().toLowerCase();

            for (String keyword : doorKeywords) {
                if (blockName.contains(keyword)) {
                    return neighbor;
                }
            }
        }
        return null;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            activeDustClouds.clear();
            triggeredBlocks.clear();
        }
    }

    public boolean isActive() {
        return active;
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putInt("pos1_x", pos1.getX());
        tag.putInt("pos1_y", pos1.getY());
        tag.putInt("pos1_z", pos1.getZ());
        tag.putInt("pos2_x", pos2.getX());
        tag.putInt("pos2_y", pos2.getY());
        tag.putInt("pos2_z", pos2.getZ());
        tag.putInt("windStrength", windStrength);
        tag.putInt("pushForce", pushForce);
        tag.putBoolean("active", active);
        return tag;
    }

    public static WindZone deserialize(CompoundTag tag) {
        String name = tag.getString("name");
        BlockPos pos1 = new BlockPos(tag.getInt("pos1_x"), tag.getInt("pos1_y"), tag.getInt("pos1_z"));
        BlockPos pos2 = new BlockPos(tag.getInt("pos2_x"), tag.getInt("pos2_y"), tag.getInt("pos2_z"));
        int windStrength = tag.getInt("windStrength");
        int pushForce = tag.getInt("pushForce");

        WindZone zone = new WindZone(name, pos1, pos2, windStrength, pushForce);
        zone.setActive(tag.getBoolean("active"));
        return zone;
    }
}