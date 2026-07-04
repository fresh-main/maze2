package com.infection.settings;

/** Клиентский кеш настроек — обновляется S2CSettingsSyncPacket на логине и при правке в GUI. */
public final class ClientSettings {

    private static volatile InfectionSettings current = new InfectionSettings();

    private ClientSettings() {}

    public static InfectionSettings get() {
        return current;
    }

    public static void set(InfectionSettings s) {
        current = s;
    }
}
