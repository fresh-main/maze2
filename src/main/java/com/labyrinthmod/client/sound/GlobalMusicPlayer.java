package com.labyrinthmod.client.sound;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.generation.LabyrinthConfig;
import com.labyrinthmod.common.init.ModSounds;
import com.otbor.client.OtborCreditsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** Управляет одной авторской музыкальной дорожкой во всех состояниях клиента. */
public final class GlobalMusicPlayer {
    private static MusicInstance current;
    private static Track currentTrack;
    private static boolean vanillaMusicStopped;
    private static int inactiveTicks;

    private GlobalMusicPlayer() {}

    public static void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!vanillaMusicStopped) {
            minecraft.getMusicManager().stopPlaying();
            vanillaMusicStopped = true;
        }

        Track wanted = selectTrack(minecraft);

        if (current != null && current.isStopped()) {
            current = null;
            currentTrack = null;
            inactiveTicks = 0;
        }

        if (current != null && !minecraft.getSoundManager().isActive(current)) {
            inactiveTicks++;
            // Потоковый OGG может несколько тиков находиться в очереди. Если спустя
            // пять секунд он так и не стал активным, создаём его заново.
            if (inactiveTicks > 100) {
                LabyrinthMod.LOGGER.warn("[music] {} did not become active; restarting", currentTrack);
                minecraft.getSoundManager().stop(current);
                current = null;
                currentTrack = null;
                inactiveTicks = 0;
            }
        } else if (current != null) {
            inactiveTicks = 0;
        }

        if (current == null) {
            if (wanted != Track.NONE) start(minecraft, wanted);
            return;
        }

        if (wanted == currentTrack) {
            current.fadeIn();
        } else {
            // Новая композиция запускается только после полного затухания старой,
            // поэтому музыкальные дорожки не накладываются друг на друга.
            current.fadeOut();
        }
    }

    private static Track selectTrack(Minecraft minecraft) {
        if (minecraft.screen instanceof OtborCreditsScreen) return Track.NONE;
        if (minecraft.player == null) return Track.MENU;

        int radius = Math.max(1, LabyrinthConfig.getInstance().gleydRadius);
        int x = minecraft.player.blockPosition().getX();
        int z = minecraft.player.blockPosition().getZ();
        return Math.max(Math.abs(x), Math.abs(z)) <= radius ? Track.GLADE : Track.MAZE;
    }

    private static void start(Minecraft minecraft, Track track) {
        currentTrack = track;
        current = new MusicInstance(track.event(), track.volume());
        minecraft.getSoundManager().play(current);
        LabyrinthMod.LOGGER.info("[music] started {} ({})", track, track.event().getLocation());
    }

    private enum Track {
        NONE(null, 0.0F),
        MENU(ModSounds.MENU_EPIC.get(), 0.16F),
        GLADE(ModSounds.GLADE_AMBIENT.get(), 0.13F),
        MAZE(ModSounds.MAZE_AMBIENT.get(), 0.15F);

        private final SoundEvent event;
        private final float volume;

        Track(SoundEvent event, float volume) {
            this.event = event;
            this.volume = volume;
        }

        private SoundEvent event() { return event; }
        private float volume() { return volume; }
    }

    private static final class MusicInstance extends AbstractTickableSoundInstance {
        private static final float VOLUME_STEP = 0.004F;
        private final float normalVolume;
        private boolean fadingOut;

        private MusicInstance(SoundEvent event, float normalVolume) {
            super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.normalVolume = normalVolume;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.04F;
            this.pitch = 1.0F;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        private void fadeIn() {
            fadingOut = false;
        }

        private void fadeOut() {
            fadingOut = true;
        }

        @Override
        public void tick() {
            float target = fadingOut ? 0.0F : normalVolume;
            this.volume = Mth.clamp(
                    this.volume + Mth.clamp(target - this.volume, -VOLUME_STEP, VOLUME_STEP),
                    0.0F, normalVolume);
            if (fadingOut && this.volume <= 0.0F) this.stop();
        }
    }
}
