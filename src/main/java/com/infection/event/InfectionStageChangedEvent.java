package com.infection.event;

import com.infection.capability.InfectionStage;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

/**
 * Публикуется на серверном Forge EVENT_BUS, когда у игрока пересекается граница стадии заражения.
 * Это публичный API для других модов: они могут подписаться, не зная нашей capability.
 */
public class InfectionStageChangedEvent extends Event {

    private final Player player;
    private final InfectionStage oldStage;
    private final InfectionStage newStage;
    private final int newLevel;

    public InfectionStageChangedEvent(Player player, InfectionStage oldStage, InfectionStage newStage, int newLevel) {
        this.player = player;
        this.oldStage = oldStage;
        this.newStage = newStage;
        this.newLevel = newLevel;
    }

    public Player getPlayer() {
        return player;
    }

    public InfectionStage getOldStage() {
        return oldStage;
    }

    public InfectionStage getNewStage() {
        return newStage;
    }

    public int getNewLevel() {
        return newLevel;
    }
}
