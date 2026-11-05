package io.github.jabrena.juno.robotics;

/**
 * One way of reading a sensor: a {@link SampleProvider} with a name, such as a
 * temperature or a humidity mode of the same device. Implementations return a constant name, so asking for it
 * allocates nothing.
 */
public interface SensorMode extends SampleProvider {

    /** Returns the name of this mode. */
    String getName();
}
