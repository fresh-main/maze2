package com.infection.settings;

import com.infection.network.Network;
import com.infection.network.packet.S2CSettingsSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/** Сервер-сайд persistent-хранилище настроек заражения. Лежит в overworld data storage. */
public class InfectionSavedData extends SavedData {

    private static final String FILE = "infection_settings";

    private InfectionSettings settings = new InfectionSettings();

    public static InfectionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                InfectionSavedData::load,
                InfectionSavedData::new,
                FILE
        );
    }

    public InfectionSettings settings() {
        return settings;
    }

    /** Обновить настройки (копия), пометить dirty и разослать всем клиентам. */
    public void replace(InfectionSettings fresh, MinecraftServer server) {
        this.settings = fresh.copy();
        this.settings.clampAll();
        setDirty();
        Network.CHANNEL.send(PacketDistributor.ALL.noArg(), new S2CSettingsSyncPacket(this.settings));
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.put("settings", settings.save());
        return tag;
    }

    public static InfectionSavedData load(CompoundTag tag) {
        InfectionSavedData d = new InfectionSavedData();
        if (tag.contains("settings")) d.settings.load(tag.getCompound("settings"));
        return d;
    }
}
