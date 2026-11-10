package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.robotics.BufferedGyroscope;
import io.github.jabrena.juno.robotics.DirectionFinder;
import io.github.jabrena.juno.robotics.Gyroscope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Gyroscope direction finder on a virtual thread")
class GyroHeadingTest {

    /** A gyroscope turning at a rate the test sets, clockwise positive. */
    static final class FakeGyroscope implements Gyroscope {
        volatile float degreesPerSecond;

        @Override
        public int getAngle() {
            return 0;
        }

        @Override
        public float getAngularVelocity() {
            return degreesPerSecond;
        }

        @Override
        public void recalibrateOffset() {
        }

        @Override
        public void reset() {
        }
    }

    /** A gyroscope with a buffer: the test queues what the next read finds, clockwise thousandths and samples. */
    static final class FakeBufferedGyroscope implements BufferedGyroscope {
        float degreesPerSecond;
        int nextRotation;
        int nextSamples;
        int lastSamples;
        boolean buffering;

        @Override
        public void startBuffering() {
            buffering = true;
        }

        @Override
        public int samplePeriodMillis() {
            return 10;
        }

        @Override
        public int readBufferedRotation() {
            lastSamples = nextSamples;
            int rotation = nextRotation;
            nextSamples = 0;
            nextRotation = 0;
            return rotation;
        }

        @Override
        public int bufferedSamples() {
            return lastSamples;
        }

        @Override
        public int getAngle() {
            return 0;
        }

        @Override
        public float getAngularVelocity() {
            return degreesPerSecond;
        }

        @Override
        public void recalibrateOffset() {
        }

        @Override
        public void reset() {
        }
    }

    /** A clock the test moves by hand. */
    static final class FakeClock implements Clock {
        int millis;

        @Override
        public int millis() {
            return millis;
        }

        @Override
        public int micros() {
            return millis * 1000;
        }
    }

    /** The board's clock, from the JVM's. */
    private static Clock realClock() {
        long origin = System.nanoTime();
        return new Clock() {
            @Override
            public int millis() {
                return (int) ((System.nanoTime() - origin) / 1_000_000L);
            }

            @Override
            public int micros() {
                return (int) ((System.nanoTime() - origin) / 1_000L);
            }
        };
    }

    @Test
    @DisplayName("Each read adds the rate over the time since the previous read, a clockwise rate as negative Cartesian degrees")
    void shouldIntegrateTheRateOverTheTimeBetweenReads() {
        // Given: 90 degrees a second clockwise
        FakeGyroscope gyroscope = new FakeGyroscope();
        FakeClock clock = new FakeClock();
        GyroHeading heading = new GyroHeading(gyroscope, clock);
        gyroscope.degreesPerSecond = 90f;

        // When: a 10 ms read, then one 150 ms late, as after an infrared round trip
        clock.millis = 10;
        heading.sample();
        clock.millis = 160;
        heading.sample();

        // Then: the late read still counts its whole 150 ms; 14.4 degrees clockwise is -14.4 Cartesian
        assertThat(heading.getDegreesCartesian()).isCloseTo(-14.4f, within(0.001f));
        assertThat(heading.samples()).isEqualTo(2);
    }

    @Test
    @DisplayName("A counter-clockwise rotation is positive, as a pilot's turns are")
    void shouldCountCounterClockwisePositive() {
        FakeGyroscope gyroscope = new FakeGyroscope();
        FakeClock clock = new FakeClock();
        DirectionFinder heading = new GyroHeading(gyroscope, clock);
        gyroscope.degreesPerSecond = -45f;

        clock.millis = 1_990;
        ((GyroHeading) heading).sample();

        assertThat(heading.getDegreesCartesian()).isCloseTo(89.55f, within(0.001f));
    }

    @Test
    @DisplayName("Resetting the Cartesian zero makes the current direction zero, and later rotation counts from it")
    void shouldCountFromTheResetZero() {
        FakeGyroscope gyroscope = new FakeGyroscope();
        FakeClock clock = new FakeClock();
        GyroHeading heading = new GyroHeading(gyroscope, clock);
        gyroscope.degreesPerSecond = -30f;
        clock.millis = 1000;
        heading.sample();

        heading.resetCartesianZero();
        clock.millis = 2000;
        heading.sample();

        assertThat(heading.getDegreesCartesian()).isCloseTo(30f, within(0.001f));
    }

    @Test
    @DisplayName("The virtual thread follows the gyroscope until stopped")
    void shouldFollowTheGyroscopeOnAVirtualThread() throws InterruptedException {
        // Given: 100 degrees a second clockwise
        FakeGyroscope gyroscope = new FakeGyroscope();
        GyroHeading heading = new GyroHeading(gyroscope, realClock());
        gyroscope.degreesPerSecond = 100f;

        // When
        heading.start();
        Thread.sleep(300);
        heading.stop();
        Thread.sleep(3 * GyroHeading.SAMPLE_MILLIS);

        // Then: about 30 degrees clockwise, from many reads, and nothing more once stopped
        assertThat(heading.getDegreesCartesian()).isBetween(-40f, -20f);
        assertThat(heading.samples()).isGreaterThan(5);
        assertThat(heading.isRunning()).isFalse();
        float stopped = heading.getDegreesCartesian();
        Thread.sleep(3 * GyroHeading.SAMPLE_MILLIS);
        assertThat(heading.getDegreesCartesian()).isEqualTo(stopped);
    }

    @Test
    @DisplayName("Calibration averages the rate at rest on the thread, and later reads subtract it")
    void shouldSubtractTheCalibratedBias() throws InterruptedException {
        // Given: a gyroscope that reads 1.2 degrees per second while still, as the BMI088 did on the floor
        FakeGyroscope gyroscope = new FakeGyroscope();
        GyroHeading heading = new GyroHeading(gyroscope, realClock());
        gyroscope.degreesPerSecond = 1.2f;
        heading.start();

        try {
            // When: calibrated, then left still
            int bias = heading.calibrate(100);
            heading.resetCartesianZero();
            Thread.sleep(300);
            float still = heading.getDegreesCartesian();
            // and turned at 90 degrees per second clockwise, the bias on top
            gyroscope.degreesPerSecond = 91.2f;
            Thread.sleep(300);
            gyroscope.degreesPerSecond = 1.2f;
            float turned = heading.getDegreesCartesian();

            // Then: no drift while still; the turn is counted without the bias
            // Read on the JVM's clock, so a millisecond of jitter may shift it slightly
            assertThat(bias).isBetween(1180, 1220);
            assertThat(still).isCloseTo(0f, within(0.05f));
            assertThat(turned).isBetween(-35f, -20f);
        } finally {
            heading.stop();
        }
    }

    @Test
    @DisplayName("Calibration needs the thread, and a second start is refused")
    void shouldRefuseCalibrationWithoutTheThreadAndASecondStart() {
        GyroHeading heading = new GyroHeading(new FakeGyroscope(), new FakeClock());
        assertThatThrownBy(heading::startCalibration).isInstanceOf(IllegalStateException.class);
        heading.start();
        try {
            assertThatThrownBy(heading::start).isInstanceOf(IllegalStateException.class);
        } finally {
            heading.stop();
        }
    }

    @Test
    @DisplayName("A buffered gyroscope's samples are all added, however late the visit, and its buffer is started")
    void shouldAddEveryBufferedSampleOfALateVisit() {
        // Given: a visit 150 ms late finds 15 samples that turned 20 degrees clockwise, the end of a fast turn
        FakeBufferedGyroscope gyroscope = new FakeBufferedGyroscope();
        FakeClock clock = new FakeClock();
        GyroHeading heading = new GyroHeading(gyroscope, clock);
        gyroscope.degreesPerSecond = 0f;
        gyroscope.nextRotation = 20_000;
        gyroscope.nextSamples = 15;

        // When: the rate now reads zero, as after the turn ended
        clock.millis = 150;
        heading.sample();

        // Then: the whole 20 degrees, where a single read of the rate would have counted none
        assertThat(heading.getDegreesCartesian()).isCloseTo(-20f, within(0.001f));
    }

    @Test
    @DisplayName("An empty buffer within three periods waits, and one empty for longer falls back to reading the rate")
    void shouldFallBackWhenTheBufferStaysEmpty() {
        FakeBufferedGyroscope gyroscope = new FakeBufferedGyroscope();
        FakeClock clock = new FakeClock();
        GyroHeading heading = new GyroHeading(gyroscope, clock);
        gyroscope.degreesPerSecond = 100f;

        // When: an empty buffer after 20 ms, then after 40 ms
        clock.millis = 20;
        heading.sample();
        float waited = heading.getDegreesCartesian();
        clock.millis = 40;
        heading.sample();

        // Then: nothing at 20 ms; at 40 ms the rate over the whole 40 ms, 4 degrees clockwise
        assertThat(waited).isZero();
        assertThat(heading.getDegreesCartesian()).isCloseTo(-4f, within(0.001f));
    }

    @Test
    @DisplayName("The bias calibrated from buffered samples is subtracted per sample period")
    void shouldCalibrateFromBufferedSamples() throws InterruptedException {
        // Given: a buffer that gives 1.2 degrees per second at rest: 10 samples, 12 thousandths each 100 ms
        FakeBufferedGyroscope gyroscope = new FakeBufferedGyroscope();
        FakeClock clock = new FakeClock();
        GyroHeading heading = new GyroHeading(gyroscope, clock);
        heading.start();
        heading.stop();
        Thread.sleep(3 * GyroHeading.SAMPLE_MILLIS);
        assertThat(gyroscope.buffering).isTrue();

        // When: a calibration over two visits, then a visit at rest and one with a 90 degree turn
        heading.beginCalibration();
        gyroscope.nextRotation = 120;
        gyroscope.nextSamples = 10;
        clock.millis = 100;
        heading.sample();
        gyroscope.nextRotation = 120;
        gyroscope.nextSamples = 10;
        clock.millis = 200;
        heading.sample();
        heading.stopCalibration();
        gyroscope.nextRotation = 1_200;
        gyroscope.nextSamples = 100;
        clock.millis = 1200;
        heading.sample();
        float still = heading.getDegreesCartesian();
        gyroscope.nextRotation = 91_200;
        gyroscope.nextSamples = 100;
        clock.millis = 2200;
        heading.sample();

        // Then
        assertThat(still).isCloseTo(0f, within(0.001f));
        assertThat(heading.getDegreesCartesian()).isCloseTo(-90f, within(0.001f));
    }
}
