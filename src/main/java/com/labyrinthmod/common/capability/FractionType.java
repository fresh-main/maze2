package com.labyrinthmod.common.capability;

import java.util.Random;

public enum FractionType {
        NONE("Нет фракции", 0xFFFFFF, -1, null),
        FARMER("Фермер", 0xFFFF55, 0, null),      // Жёлтый - §e
        BUTCHER("Мясник", 0xFFAA00, 1, null),     // Оранжевый - §6
        RUNNER("Бегун", 0x55FFFF, 2, null),       // Голубой - §b
        COOK("Повар", 0xFFFFFF, 3, null),         // Белый - §f
        MEDIC("Медик", 0xFF55FF, 4, null),        // Пурпурный - §d
        OPERATOR("Оператор", 0xAAAAAA, 5, null),  // Серый - §7
        IMPOSTER("Предатель", 0xFF0000, 6, null); // Красный - §c



    public final String displayName;
    public final int color;
    public final int id;
    private String maskFraction;

    FractionType(String displayName, int color, int id, String maskFraction) {
        this.displayName = displayName;
        this.color = color;
        this.id = id;
        this.maskFraction = maskFraction;
    }

    public String getMaskFraction() {
        return maskFraction;
    }

    public void setMaskFraction(String maskFraction) {
        this.maskFraction = maskFraction;
    }

    public boolean hasMaskFraction() {
        return maskFraction != null && !maskFraction.isEmpty();
    }

    public static String getRandomMask() {
        String[] masks = {"FARMER", "BUTCHER", "RUNNER", "COOK", "MEDIC"};
        Random random = new Random();
        return masks[random.nextInt(masks.length)];
    }

    public static FractionType fromId(int id) {
        for (FractionType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return NONE;
    }

    public static FractionType fromName(String name) {
        for (FractionType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return NONE;
    }

    public boolean hasFraction() {
        return this != NONE;
    }
}