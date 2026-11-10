package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.robotics.BufferedGyroscope;
import io.github.jabrena.juno.robotics.DirectionFinder;
import io.github.jabrena.juno.robotics.Gyroscope;
import org.jspecify.annotations.Nullable;

/**
 * A {@link DirectionFinder} over a {@link Gyroscope}: a virtual thread of its own reads the gyroscope's angular velocity
 * every {@link #SAMPLE_MILLIS} and integrates it, so the direction keeps counting while the program waits on something
 * else, such as an infrared round trip to an RCX. The direction is Cartesian, counter-clockwise positive like a drive's
 * turns, while the gyroscope's rate is clockwise positive; it does
 * not wrap at a full turn. Read it from any thread.
 *
 * <p>An uncorrected bias of one degree per second drifts the direction a degree every second, and a robot that holds a
 * heading turns itself by the drift. So calibrate while the robot stands still, after {@link #start()}: between
 * {@link #startCalibration()} and {@link #stopCalibration()}, or for a fixed time with {@link #calibrate(int)}, the
 * thread averages the rate instead of integrating it, and later reads subtract the average.
 *
 * <p>A {@link BufferedGyroscope}, such as the BMI088 with its FIFO, keeps sampling while the thread waits, and each read
 * adds every sample buffered since the previous one, so a turn that ends while the program is busy loses nothing. Any
 * other gyroscope is read once a visit, its rate times the time since the previous read: a read that comes late still
 * counts the whole interval, but at the rate it reads then. If a buffered gyroscope delivers nothing for
 * {@link #FALLBACK_PERIODS} sample periods, its buffer is taken as not working and it is read the same way.
 *
 * <p>Juno's threads take turns at loop backedges and method entries, so the thread can take the processor in the middle
 * of time-critical work on another thread. When a read must not interrupt such work, give the heading a gyroscope whose
 * reads wait for it, for instance by synchronizing on the object the work holds. Create it once at startup; reading it
 * allocates nothing.
 */
public final class GyroHeading implements DirectionFinder {
    /** How often the thread reads the gyroscope, also while it calibrates. */
    public static final int SAMPLE_MILLIS = 10;
    /** Sample periods without a buffered sample after which a buffered gyroscope is read directly instead. */
    public static final int FALLBACK_PERIODS = 3;
    private static final float MILLIDEGREES_PER_DEGREE = 1000f;

    private final Gyroscope gyroscope;
    private final @Nullable BufferedGyroscope buffered;
    private final Clock clock;
    /** The integrated rotation, clockwise as the gyroscope counts it, in thousandths of a degree. */
    private volatile int clockwiseMillidegrees;
    private volatile int zeroMillidegrees;
    private volatile int samples;
    private volatile boolean running;
    private volatile boolean calibrating;
    /** The rotation the calibration measured, in thousandths of a degree, and the milliseconds it covers. */
    private volatile int calibrationRotation;
    private volatile int calibrationSpan;
    private volatile float biasDegreesPerSecond;
    private int lastMillis;

    /** Creates the direction over {@code gyroscope}, read once a visit; nothing is read until {@link #start()}. */
    public GyroHeading(Gyroscope gyroscope, Clock clock) {
        this(gyroscope, null, clock);
    }

    /** Creates the direction over {@code gyroscope}, read from its buffer; nothing is read until {@link #start()}. */
    public GyroHeading(BufferedGyroscope gyroscope, Clock clock) {
        this(gyroscope, gyroscope, clock);
    }

    private GyroHeading(Gyroscope gyroscope, @Nullable BufferedGyroscope buffered, Clock clock) {
        this.gyroscope = gyroscope;
        this.buffered = buffered;
        this.clock = clock;
    }

    /**
     * Starts the virtual thread that follows the gyroscope, from a direction of zero.
     *
     * @throws IllegalStateException if it already runs
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("the heading is already being followed");
        }
        clockwiseMillidegrees = 0;
        zeroMillidegrees = 0;
        samples = 0;
        BufferedGyroscope fifo = buffered;
        if (fifo != null) {
            fifo.startBuffering();
        }
        lastMillis = clock.millis();
        running = true;
        Thread.startVirtualThread(this::follow);
    }

    /** Stops the thread after its current read; the direction keeps its last value. */
    public void stop() {
        running = false;
    }

    /** Whether the thread follows the gyroscope. */
    public boolean isRunning() {
        return running;
    }

    /** The direction since {@link #resetCartesianZero()}, counter-clockwise positive, not wrapped at a full turn. */
    @Override
    public float getDegreesCartesian() {
        return (zeroMillidegrees - clockwiseMillidegrees) / MILLIDEGREES_PER_DEGREE;
    }

    /** Makes the current direction zero. */
    @Override
    public void resetCartesianZero() {
        zeroMillidegrees = clockwiseMillidegrees;
    }

    /**
     * Starts averaging the gyroscope's rate as its bias, instead of integrating it; the robot must stand still.
     *
     * @throws IllegalStateException if the thread does not run
     */
    @Override
    public void startCalibration() {
        if (!running) {
            throw new IllegalStateException("start the heading before calibrating it");
        }
        beginCalibration();
    }

    /** Starts averaging from nothing; the reads that feed it come from the thread, or from a test. */
    void beginCalibration() {
        calibrationRotation = 0;
        calibrationSpan = 0;
        calibrating = true;
    }

    /** Ends the calibration and subtracts the averaged bias from later reads; without a sample, the bias stays. */
    @Override
    public void stopCalibration() {
        calibrating = false;
        int span = calibrationSpan;
        if (span > 0) {
            // Thousandths of a degree per millisecond are degrees per second.
            biasDegreesPerSecond = calibrationRotation / (float) span;
        }
    }

    /**
     * Calibrates for {@code millis}, waiting on the calling thread, and returns the bias in thousandths of a degree per
     * second, clockwise as the gyroscope counts it, for a log.
     *
     * @throws IllegalStateException if the thread does not run
     */
    public int calibrate(int millis) throws InterruptedException {
        startCalibration();
        Thread.sleep(millis);
        stopCalibration();
        return (int) (biasDegreesPerSecond * MILLIDEGREES_PER_DEGREE);
    }

    /** How many times the thread has read the gyroscope since {@link #start()}: a check that it gets its turns. */
    public int samples() {
        return samples;
    }

    private void follow() {
        while (running) {
            sample();
            try {
                Thread.sleep(SAMPLE_MILLIS);
            } catch (InterruptedException interrupted) {
                running = false;
            }
        }
    }

    /**
     * One visit: every buffered sample since the previous visit, or, without a working buffer, the rate times the time
     * since the previous read; added to the calibration while it runs, otherwise integrated less the bias.
     */
    void sample() {
        int now = clock.millis();
        int elapsed = now - lastMillis;
        BufferedGyroscope fifo = buffered;
        if (fifo != null) {
            int rotation = fifo.readBufferedRotation();
            int count = fifo.bufferedSamples();
            if (count > 0) {
                lastMillis = now;
                add(rotation, count * fifo.samplePeriodMillis());
                return;
            }
            if (elapsed < FALLBACK_PERIODS * fifo.samplePeriodMillis()) {
                return;
            }
        }
        lastMillis = now;
        // Degrees per second times milliseconds is thousandths of a degree.
        add(rounded(gyroscope.getAngularVelocity() * elapsed), elapsed);
    }

    /** Rounds to the nearest whole number, halves away from zero: truncating lost up to one per read, 3% of a bias. */
    private static int rounded(float value) {
        return (int) (value >= 0f ? value + 0.5f : value - 0.5f);
    }

    /** Adds {@code rotation} thousandths of a degree, clockwise, measured over {@code span} milliseconds. */
    private void add(int rotation, int span) {
        if (calibrating) {
            calibrationRotation = calibrationRotation + rotation;
            calibrationSpan = calibrationSpan + span;
        } else {
            clockwiseMillidegrees = clockwiseMillidegrees + rotation - rounded(biasDegreesPerSecond * span);
        }
        samples = samples + 1;
    }
}
