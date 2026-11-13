package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.robotics.RangeFinder;

/**
 * An {@link ObstacleDetector} over a forward-facing {@link RangeFinder}, such as a Grove Ultrasonic Ranger: blocked once
 * {@code confirmReadings} readings in a row are nearer than {@code stopMillimetres}, and clear again only once a reading
 * is beyond {@code clearMillimetres} or finds no echo. The confirmation ignores a single stray echo, and the gap between
 * the two distances keeps a reading near the limit from switching it on and off. A range finder's {@code NaN}, no echo,
 * counts as a clear way.
 *
 * <p>leJOS's {@code RangeFeatureDetector} notifies its listeners on every reading while something is in range; this one
 * only answers when asked, so a pilot reads it at its own pace. Create it once at startup.
 */
public final class RangeObstacleDetector implements ObstacleDetector {
    private static final float MILLIMETRES_PER_METRE = 1000f;
    /** A reading that found nothing. */
    private static final int NO_ECHO = -1;

    private final RangeFinder rangeFinder;
    private final int stopMillimetres;
    private final int clearMillimetres;
    private final int confirmReadings;
    private int nearReadings;
    private boolean blocked;
    private int rangeMillimetres = NO_ECHO;

    /**
     * Creates the detector.
     *
     * @throws IllegalArgumentException unless {@code 0 < stopMillimetres <= clearMillimetres} and
     *         {@code confirmReadings > 0}
     */
    public RangeObstacleDetector(RangeFinder rangeFinder, int stopMillimetres, int clearMillimetres, int confirmReadings) {
        if (stopMillimetres <= 0 || clearMillimetres < stopMillimetres || confirmReadings <= 0) {
            throw new IllegalArgumentException("need 0 < stop <= clear and at least one confirming reading");
        }
        this.rangeFinder = rangeFinder;
        this.stopMillimetres = stopMillimetres;
        this.clearMillimetres = clearMillimetres;
        this.confirmReadings = confirmReadings;
    }

    @Override
    public boolean blocked() {
        int range = reading();
        if (range != NO_ECHO && range < stopMillimetres) {
            nearReadings++;
            if (nearReadings >= confirmReadings) {
                blocked = true;
                rangeMillimetres = range;
            }
        } else {
            nearReadings = 0;
            if (range == NO_ECHO || range > clearMillimetres) {
                blocked = false;
            }
        }
        return blocked;
    }

    @Override
    public int rangeMillimetres() {
        return rangeMillimetres;
    }

    @Override
    public void reset() {
        nearReadings = 0;
        blocked = false;
    }

    /** The range finder's reading in millimetres, or {@link #NO_ECHO}. */
    private int reading() {
        float metres = rangeFinder.getRange();
        if (!(metres >= 0f)) {
            return NO_ECHO;
        }
        return (int) (metres * MILLIMETRES_PER_METRE + 0.5f);
    }
}
