package com.labyrinthmod.common.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Управляет сохранением и загрузкой всех зон сдвига лабиринта для конкретного измерения (мира).
 */
public class LabyrinthZoneSavedData extends SavedData {

    private static final String DATA_NAME = "labyrinth_shift_zones";

    // Хранилище всех зон по их UUID
    private final Map<UUID, LabyrinthShiftZone> zones = new HashMap<>();
    private boolean generatedFromMaze = false;

    public LabyrinthZoneSavedData() {
        super();
    }

    /**
     * Получает экземпляр данных для текущего мира.
     * Если его нет, создает новый.
     * В 1.20.1 используется 3 аргумента: loader, factory, key
     */
    public static LabyrinthZoneSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                LabyrinthZoneSavedData::load,   // 1. Функция загрузки из NBT
                LabyrinthZoneSavedData::new,    // 2. Фабрика для создания нового экземпляра
                DATA_NAME                       // 3. Имя файла сохранения
        );
    }

    /**
     * Загрузка из NBT
     */
    public static LabyrinthZoneSavedData load(CompoundTag nbt) {
        LabyrinthZoneSavedData data = new LabyrinthZoneSavedData();
        data.generatedFromMaze = nbt.getBoolean("GeneratedFromMaze");
        ListTag zonesList = nbt.getList("Zones", 10); // 10 = TAG_COMPOUND

        for (int i = 0; i < zonesList.size(); i++) {
            CompoundTag zoneNbt = zonesList.getCompound(i);
            LabyrinthShiftZone zone = LabyrinthShiftZone.load(zoneNbt);
            data.zones.put(zone.id, zone);
        }

        return data;
    }

    /**
     * Сохранение в NBT
     */
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

    // --- Методы управления зонами ---

    public void addZone(LabyrinthShiftZone zone) {
        this.zones.put(zone.id, zone);
        this.setDirty(); // Обязательно: сообщает Minecraft, что данные изменились и их нужно сохранить
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

    /**
     * Переключает состояние конкретной зоны и помечает данные как измененные.
     */
    public void toggleZoneState(UUID id) {
        LabyrinthShiftZone zone = this.zones.get(id);
        if (zone != null) {
            zone.toggleState();
            this.setDirty();
        }
    }
}
