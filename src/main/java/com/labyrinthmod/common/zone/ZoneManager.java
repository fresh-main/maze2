package com.labyrinthmod.common.zone;

import com.labyrinthmod.common.capability.FractionType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ZoneManager extends SavedData {
    private static final String DATA_NAME = "labyrinth_zones";

    private final List<Zone> zones = new ArrayList<>();
    private boolean zonesEnabled = true;

    /**
     * Per-fraction белый список «может выходить из safe-зоны (т.е. в лабиринт)».
     * По умолчанию выходить могут: RUNNER, IMPOSTER, OPERATOR — остальные
     * заперты внутри зоны и выкидываются назад при попытке выйти. Эти дефолты
     * совпадают со старым хардкодом в FractionEvents.checkZoneRestriction.
     * Админ может перенастроить через GUI «Доступ фракций» в админ-панели.
     */
    private final Map<FractionType, Boolean> fractionCanLeave = new EnumMap<>(FractionType.class);

    public ZoneManager() {
        applyFractionDefaults();
    }

    private void applyFractionDefaults() {
        for (FractionType f : FractionType.values()) {
            // Default: можно выходить только бегунам, предателям и операторам.
            fractionCanLeave.putIfAbsent(f,
                    f == FractionType.RUNNER
                    || f == FractionType.IMPOSTER
                    || f == FractionType.OPERATOR);
        }
    }

    public boolean canFractionLeave(FractionType fraction) {
        if (fraction == null) return false;
        Boolean v = fractionCanLeave.get(fraction);
        return v != null && v;
    }

    public void setFractionCanLeave(FractionType fraction, boolean canLeave) {
        if (fraction == null) return;
        fractionCanLeave.put(fraction, canLeave);
        setDirty();
    }

    public Map<FractionType, Boolean> getFractionAccessSnapshot() {
        return new EnumMap<>(fractionCanLeave);
    }

    public static class Zone {
        public final String name;
        public final BlockPos pos1;
        public final BlockPos pos2;
        public final int minX, minY, minZ, maxX, maxY, maxZ;

        public Zone(String name, BlockPos pos1, BlockPos pos2) {
            this.name = name;
            this.pos1 = pos1;
            this.pos2 = pos2;
            this.minX = Math.min(pos1.getX(), pos2.getX());
            this.minY = Math.min(pos1.getY(), pos2.getY());
            this.minZ = Math.min(pos1.getZ(), pos2.getZ());
            this.maxX = Math.max(pos1.getX(), pos2.getX());
            this.maxY = Math.max(pos1.getY(), pos2.getY());
            this.maxZ = Math.max(pos1.getZ(), pos2.getZ());
        }

        public boolean isInside(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX &&
                    pos.getY() >= minY && pos.getY() <= maxY &&
                    pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        public CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", name);
            tag.putInt("pos1_x", pos1.getX());
            tag.putInt("pos1_y", pos1.getY());
            tag.putInt("pos1_z", pos1.getZ());
            tag.putInt("pos2_x", pos2.getX());
            tag.putInt("pos2_y", pos2.getY());
            tag.putInt("pos2_z", pos2.getZ());
            return tag;
        }

        public static Zone deserialize(CompoundTag tag) {
            String name = tag.getString("name");
            BlockPos pos1 = new BlockPos(tag.getInt("pos1_x"), tag.getInt("pos1_y"), tag.getInt("pos1_z"));
            BlockPos pos2 = new BlockPos(tag.getInt("pos2_x"), tag.getInt("pos2_y"), tag.getInt("pos2_z"));
            return new Zone(name, pos1, pos2);
        }
    }

    public static ZoneManager get(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        DimensionDataStorage storage = serverLevel.getDataStorage();
        return storage.computeIfAbsent(ZoneManager::load, ZoneManager::new, DATA_NAME);
    }

    public static ZoneManager load(CompoundTag tag) {
        ZoneManager manager = new ZoneManager();
        manager.zonesEnabled = tag.getBoolean("zonesEnabled");
        ListTag zonesList = tag.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < zonesList.size(); i++) {
            manager.zones.add(Zone.deserialize(zonesList.getCompound(i)));
        }
        // fractionAccess — карта по имени фракции. Отсутствующие ключи остаются с дефолтом из конструктора.
        if (tag.contains("fractionAccess")) {
            CompoundTag fa = tag.getCompound("fractionAccess");
            for (FractionType f : FractionType.values()) {
                if (fa.contains(f.name())) {
                    manager.fractionCanLeave.put(f, fa.getBoolean(f.name()));
                }
            }
        }
        return manager;
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag) {
        tag.putBoolean("zonesEnabled", zonesEnabled);
        ListTag zonesList = new ListTag();
        for (Zone zone : zones) {
            zonesList.add(zone.serialize());
        }
        tag.put("zones", zonesList);

        CompoundTag fa = new CompoundTag();
        for (Map.Entry<FractionType, Boolean> e : fractionCanLeave.entrySet()) {
            fa.putBoolean(e.getKey().name(), e.getValue());
        }
        tag.put("fractionAccess", fa);

        return tag;
    }

    public void addZone(Zone zone) {
        zones.add(zone);
        setDirty();
    }

    public void removeZone(String name) {
        zones.removeIf(zone -> zone.name.equals(name));
        setDirty();
    }

    public Zone getZone(String name) {
        return zones.stream().filter(zone -> zone.name.equals(name)).findFirst().orElse(null);
    }

    public List<Zone> getAllZones() {
        return new ArrayList<>(zones);
    }

    public boolean isInsideAnyZone(BlockPos pos) {
        if (!zonesEnabled) return false;
        for (Zone zone : zones) {
            if (zone.isInside(pos)) return true;
        }
        return false;
    }

    public Zone getZoneAt(BlockPos pos) {
        if (!zonesEnabled) return null;
        for (Zone zone : zones) {
            if (zone.isInside(pos)) return zone;
        }
        return null;
    }

    public void clearZones() {
        zones.clear();
        setDirty();
    }

    public boolean isZonesEnabled() {
        return zonesEnabled;
    }

    public void setZonesEnabled(boolean enabled) {
        this.zonesEnabled = enabled;
        setDirty();
    }

    public void toggleZones() {
        this.zonesEnabled = !this.zonesEnabled;
        setDirty();
    }
}