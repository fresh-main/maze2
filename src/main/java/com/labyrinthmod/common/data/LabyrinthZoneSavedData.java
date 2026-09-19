package com.labyrinthmod.common.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Управляет сохранением и загрузкой всех зон сдвига лабиринта
 * для конкретного измерения (мира).
 */
public class LabyrinthZoneSavedData extends SavedData {

    private static final String DATA_NAME = "labyrinth_shift_zones";

    private final Map<UUID, LabyrinthShiftZone> zones = new HashMap<>();
    private boolean generatedFromMaze = false;

    public LabyrinthZoneSavedData() {
        super();
    }

    public static LabyrinthZoneSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                LabyrinthZoneSavedData::load,
                LabyrinthZoneSavedData::new,
                DATA_NAME
        );
    }

    public static LabyrinthZoneSavedData load(CompoundTag nbt) {
        LabyrinthZoneSavedData data = new LabyrinthZoneSavedData();
        data.generatedFromMaze = nbt.getBoolean("GeneratedFromMaze");
        ListTag zonesList = nbt.getList("Zones", 10);

        for (int i = 0; i < zonesList.size(); i++) {
            CompoundTag zoneNbt = zonesList.getCompound(i);
            LabyrinthShiftZone zone = LabyrinthShiftZone.load(zoneNbt);
            data.zones.put(zone.id, zone);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag zonesList = new ListTag();
        for (LabyrinthShiftZone zone : zones.values()) {
            zonesList.add(zone.save(new CompoundTag()));
        }
        nbt.put("Zones", zonesList);
        nbt.putBoolean("GeneratedFromMaze", generatedFromMaze);
        return nbt;
    }

    public void addZone(LabyrinthShiftZone zone) {
        this.zones.put(zone.id, zone);
        this.setDirty();
    }

    public void removeZone(UUID id) {
        this.zones.remove(id);
        this.setDirty();
    }

    public LabyrinthShiftZone getZone(UUID id) {
        return this.zones.get(id);
    }

    public Map<UUID, LabyrinthShiftZone> getAllZones() {
        return this.zones;
    }

    public boolean hasGeneratedFromMaze() {
        return generatedFromMaze;
    }

    public void markGeneratedFromMaze() {
        this.generatedFromMaze = true;
        this.setDirty();
    }

    public void toggleZoneState(UUID id) {
        LabyrinthShiftZone zone = this.zones.get(id);
        if (zone != null) {
            zone.toggleState();
            this.setDirty();
        }
    }

    /**
     * Переключает ВСЕ зоны и помечает данные как изменённые.
     */
    public void toggleAllZones() {
        for (LabyrinthShiftZone zone : zones.values()) {
            zone.toggleState();
        }
        this.setDirty();
    }

    /**
     * Возвращает количество зон в варианте B.
     */
    public int countVariantB() {
        int count = 0;
        for (LabyrinthShiftZone zone : zones.values()) {
            if (zone.isVariantB) count++;
        }
        return count;
    }
}