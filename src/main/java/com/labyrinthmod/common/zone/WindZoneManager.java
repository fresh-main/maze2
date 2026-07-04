package com.labyrinthmod.common.zone;

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
import java.util.List;
import java.util.UUID;

public class WindZoneManager extends SavedData {
    private static final String DATA_NAME = "labyrinth_wind_zones";

    private final List<WindZone> windZones = new ArrayList<>();
    private boolean enabled = true;

    public static WindZoneManager get(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        DimensionDataStorage storage = serverLevel.getDataStorage();
        return storage.computeIfAbsent(WindZoneManager::load, WindZoneManager::new, DATA_NAME);
    }

    public void tick(Level level) {
        if (!enabled) return;
        for (WindZone zone : windZones) {
            zone.tick(level);
        }
    }

    public void addWindZone(WindZone zone) {
        windZones.add(zone);
        setDirty();
    }

    public void removeWindZone(String name) {
        windZones.removeIf(zone -> zone.name.equals(name));
        setDirty();
    }

    public void removeWindZone(UUID id) {
        windZones.removeIf(zone -> zone.id.equals(id));
        setDirty();
    }

    public WindZone getWindZone(String name) {
        for (WindZone zone : windZones) {
            if (zone.name.equals(name)) return zone;
        }
        return null;
    }

    public WindZone getWindZoneAt(BlockPos pos) {
        for (WindZone zone : windZones) {
            if (zone.isInside(pos)) return zone;
        }
        return null;
    }

    public List<WindZone> getAllWindZones() {
        return new ArrayList<>(windZones);
    }

    public void clearWindZones() {
        windZones.clear();
        setDirty();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        for (WindZone zone : windZones) {
            zone.setActive(enabled);
        }
        setDirty();
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag) {
        tag.putBoolean("enabled", enabled);
        ListTag zonesList = new ListTag();
        for (WindZone zone : windZones) {
            zonesList.add(zone.serialize());
        }
        tag.put("windZones", zonesList);
        return tag;
    }

    public static WindZoneManager load(CompoundTag tag) {
        WindZoneManager manager = new WindZoneManager();
        manager.enabled = tag.getBoolean("enabled");
        ListTag zonesList = tag.getList("windZones", Tag.TAG_COMPOUND);
        for (int i = 0; i < zonesList.size(); i++) {
            manager.windZones.add(WindZone.deserialize(zonesList.getCompound(i)));
        }
        return manager;
    }
}