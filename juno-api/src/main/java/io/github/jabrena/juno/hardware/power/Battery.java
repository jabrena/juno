package io.github.jabrena.juno.hardware.power;

/** Battery readings exposed by a board. Values are read directly from the hardware and allocate no Java objects. */
public interface Battery {
    /** The estimated battery charge level as a percentage in the range {@code 0..100}. */
    int levelPercent();

    /** The battery voltage in millivolts. */
    int voltageMillivolts();
}
