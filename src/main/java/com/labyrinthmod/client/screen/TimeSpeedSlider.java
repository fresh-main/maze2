package com.labyrinthmod.client.screen;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class TimeSpeedSlider extends AbstractSliderButton {

    private double currentValue;
    private final double minValue;
    private final double maxValue;
    private final OnValueChange callback;

    public interface OnValueChange {
        void onChange(double value);
    }

    public TimeSpeedSlider(int x, int y, int width, int height,
                           double minValue, double maxValue, double defaultValue,
                           OnValueChange callback) {
        super(x, y, width, height, Component.empty(), defaultValue);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.currentValue = defaultValue;
        this.callback = callback;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal(String.format("Скорость: %.1f x", currentValue)));
    }

    @Override
    protected void applyValue() {
        currentValue = Mth.lerp(this.value, minValue, maxValue);
        if (callback != null) {
            callback.onChange(currentValue);
        }
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(double value) {
        this.value = Mth.inverseLerp(value, minValue, maxValue);
        this.currentValue = value;
        updateMessage();
    }
}