package com.labyrinthmod.common;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Базовый прокси для серверной и клиентской сторон.
 * Используется для безопасного вызова side-специфичного кода.
 */
public class Proxy {

    private static Proxy instance;

    public static Proxy getInstance() {
        if (instance == null) {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                try {
                    Class<?> clazz = Class.forName("com.labyrinthmod.client.ClientProxy");
                    instance = (Proxy) clazz.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create client proxy", e);
                }
            } else {
                instance = new Proxy();
            }
        }
        return instance;
    }

    /** Открыть админ-экран (только на клиенте) */
    public void openAdminScreen(Object packet) {
        // На сервере ничего не делаем
    }

    /** Зарегистрировать клиентские обработчики (только на клиенте) */
    public void registerClientListeners() {
        // На сервере ничего не делаем
    }

    /** Проверка, является ли текущая сторона клиентом */
    public boolean isClient() {
        return false;
    }

    /** Проверка, является ли текущая сторона выделенным сервером */
    public boolean isDedicatedServer() {
        return true;
    }
}