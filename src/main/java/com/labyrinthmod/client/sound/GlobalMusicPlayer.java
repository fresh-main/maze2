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
import net.minecraft.world.level.biome.Biomes;

/** Управляет одной авторской музыкальной дорожкой во всех состояниях клиента. */
public final class GlobalMusicPlayer {
    private static MusicInstance current;
    private static MusicInstance outgoing;
    private static Track currentTrack = Track.NONE;
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
            currentTrack = Track.NONE;
            inactiveTicks = 0;
        }
        if (outgoing != null && outgoing.isStopped()) outgoing = null;

        if (current != null && !minecraft.getSoundManager().isActive(current)) {
            inactiveTicks++;
            // Потоковый OGG может несколько тиков находиться в очереди. Если спустя
            // пять секунд он так и не стал активным, создаём его заново.
            if (inactiveTicks > 100) {
                LabyrinthMod.LOGGER.warn("[music] {} did not become active; restarting", currentTrack);
                minecraft.getSoundManager().stop(current);
                current = null;
                currentTrack = Track.NONE;
                inactiveTicks = 0;
            }
        } else if (current != null) {
            inactiveTicks = 0;
        }

        if (wanted != currentTrack) {
            switchTo(minecraft, wanted);
            return;
        }

        if (current == null && wanted != Track.NONE) start(minecraft, wanted);
        if (current != null) current.fadeIn();
    }

    private static Track selectTrack(Minecraft minecraft) {
        if (minecraft.screen instanceof OtborCreditsScreen) return Track.NONE;
        if (minecraft.player == null) return Track.MENU;

        long timeOfDay = Math.floorMod(minecraft.level.getDayTime(), 24_000L);
        boolean night = timeOfDay >= 13_000L && timeOfDay < 23_000L;
        if (minecraft.level.getBiome(minecraft.player.blockPosition()).is(Biomes.DESERT)) {
            return night ? Track.DESERT_NIGHT : Track.DESERT_DAY;
        }

        int radius = Math.max(1, LabyrinthConfig.getInstance().gleydRadius);
        int x = minecraft.player.blockPosition().getX();
        int z = minecraft.player.blockPosition().getZ();
        if (Math.max(Math.abs(x), Math.abs(z)) <= radius) {
            return night ? Track.GLADE_NIGHT : Track.GLADE_DAY;
        }
        return night ? Track.MAZE_NIGHT : Track.MAZE_DAY;
    }

    private static void start(Minecraft minecraft, Track track) {
        currentTrack = track;
        current = new MusicInstance(track.event(), track.volume());
        minecraft.getSoundManager().play(current);
        LabyrinthMod.LOGGER.info("[music] started {} ({})", track, track.event().getLocation());
    }

    private static void switchTo(Minecraft minecraft, Track wanted) {
        if (outgoing != null) minecraft.getSoundManager().stop(outgoing);
        outgoing = current;
        if (outgoing != null) outgoing.fadeOut();

        current = null;
        currentTrack = Track.NONE;
        inactiveTicks = 0;
        if (wanted != Track.NONE) start(minecraft, wanted);
        LabyrinthMod.LOGGER.info("[music] switched to {} at dayTime={}", wanted,
                minecraft.level == null ? -1L : Math.floorMod(minecraft.level.getDayTime(), 24_000L));
    }

    private enum Track {
        NONE(null, 0.0F),
        MENU(ModSounds.MENU_EPIC.get(), 0.16F),
        GLADE_DAY(ModSounds.GLADE_AMBIENT.get(), 0.12F),
        GLADE_NIGHT(ModSounds.GLADE_NIGHT.get(), 0.10F),
        MAZE_DAY(ModSounds.MAZE_AMBIENT.get(), 0.13F),
        MAZE_NIGHT(ModSounds.MAZE_NIGHT.get(), 0.12F),
        DESERT_DAY(ModSounds.DESERT_DAY.get(), 0.11F),
        DESERT_NIGHT(ModSounds.DESERT_NIGHT.get(), 0.09F);

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
            // SoundEngine отбрасывает новый звук, если его громкость в момент play()
            // равна нулю. Начинаем почти беззвучно, затем обычный tick делает fade-in.
            this.volume = 0.01F;
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
