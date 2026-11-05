package io.github.jabrena.juno.robotics;

/** A device that measures temperature, in degrees Celsius. */
public interface Thermometer {

    /**
     * Returns the temperature in degrees Celsius, or {@link Float#NaN} when the sensor could not give a reading.
     */
    float getTemperatureCelsius();
}
