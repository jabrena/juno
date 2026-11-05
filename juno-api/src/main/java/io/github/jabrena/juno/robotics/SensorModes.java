package io.github.jabrena.juno.robotics;

/**
 * A sensor with several modes. The sensor itself fetches samples in its current
 * mode, and each mode is also available as its own {@link SensorMode}. Modes are numbered from 0 to
 * {@link #getModeCount()} - 1.
 *
 * <p>Enumerate the modes with {@link #getModeCount()} and {@link #getMode(int)}. Implementations create their mode
 * objects once, so {@code getMode} returns an existing instance rather than allocating one.
 */
public interface SensorModes extends SensorMode {

    /** Returns the number of modes this sensor supports. */
    int getModeCount();

    /**
     * Returns the sample provider for a mode.
     *
     * @param mode the mode number, from 0 to {@link #getModeCount()} - 1
     * @return the sample provider for that mode
     */
    SensorMode getMode(int mode);

    /**
     * Returns the sample provider for the mode with the given name.
     *
     * @param modeName the name that the mode's {@link SensorMode#getName()} returns
     * @return the sample provider for that mode
     */
    SensorMode getMode(String modeName);

    /**
     * Sets the mode that {@link #fetchSample(float[], int)} reads.
     *
     * @param mode the mode number, from 0 to {@link #getModeCount()} - 1
     */
    void setCurrentMode(int mode);

    /**
     * Sets the mode that {@link #fetchSample(float[], int)} reads.
     *
     * @param modeName the name that the mode's {@link SensorMode#getName()} returns
     */
    void setCurrentMode(String modeName);

    /** Returns the number of the current mode. */
    int getCurrentMode();
}
