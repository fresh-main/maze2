package com.infection.client.sound;

import com.infection.client.ClientInfectionCache;
import com.infection.settings.ClientSettings;
import com.infection.settings.InfectionSettings;
import com.infection.sound.InfectionModSounds;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Зацикленный эмбиент заражения. Громкость считается каждый тик линейно от уровня
 * заражения: 0.0 на ambienceStartLevel, 1.0 на ambienceFullLevel и выше.
 * Сам себя останавливает, когда:
 *   — игрок мёртв/удалён
 *   — уровень заражения упал ниже ambienceStartLevel
 */
public class InfectionAmbienceSound extends AbstractTickableSoundInstance {

    private final LocalPlayer player;

    public InfectionAmbienceSound(LocalPlayer player) {
        super(InfectionModSounds.INFESTATION_AMBIENCE.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f; // стартуем тихо, в первом tick подтянем актуальную громкость
        this.pitch = 1.0f;
        this.relative = true;           // «в ушах» игрока, без затухания по расстоянию
        this.attenuation = Attenuation.NONE;
    }

    @Override
    public void tick() {
        if (player == null || player.isRemoved() || !player.isAlive()) {
            stop();
            return;
        }
        InfectionSettings s = ClientSettings.get();
        int level = ClientInfectionCache.get(player.getUUID());
        int start = s.ambienceStartLevel;
        int full = Math.max(start + 1, s.ambienceFullLevel);
        if (level < start) {
            stop();
            return;
        }
        // Линейная интерполяция громкости 0.0 → 1.0 на отрезке [start, full]; на full+ держим 1.0.
        float t = Mth.clamp((level - start) / (float) (full - start), 0f, 1f);
        this.volume = Mth.clamp(t, 0.001f, 1.0f);

        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }
}
