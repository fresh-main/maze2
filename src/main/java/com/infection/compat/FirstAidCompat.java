package com.infection.compat;

/**
 * Обёртка: проверяет наличие First Aid в рантайме и делегирует работу реальному
 * бекенду {@link FirstAidHealBlocker}, не трогая классы First Aid, если мод не загружен.
 */
public final class FirstAidCompat {

    private static final boolean LOADED = detect();

    private FirstAidCompat() {}

    private static boolean detect() {
        try {
            Class.forName("ichttt.mods.firstaid.common.util.CommonUtils");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    /** Если First Aid есть — откатывает любые увеличения хп всех body-part'ов,
     *  если у игрока заражение &ge; 100%. */
    public static void revertIfInfected(net.minecraft.server.level.ServerPlayer player, int infectionLevel) {
        if (!LOADED) return;
        FirstAidHealBlocker.revertIfInfected(player, infectionLevel);
    }

    /** Очистить snapshot HP-частей при дисконнекте. No-op если First Aid не загружен. */
    public static void forgetSnapshots(java.util.UUID playerId) {
        if (!LOADED) return;
        FirstAidHealBlocker.forget(playerId);
    }
}
