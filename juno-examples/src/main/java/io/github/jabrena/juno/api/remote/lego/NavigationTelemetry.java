package io.github.jabrena.juno.api.remote.lego;

import io.github.jabrena.juno.hardware.net.udp.Syslog;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.robotics.RangeFinder;
import io.github.jabrena.juno.robotics.navigation.GyroHeading;
import io.github.jabrena.juno.robotics.navigation.Pose;

/**
 * IMU and ranger telemetry for a navigating robot, sent to syslog while it drives, to tell a drifting gyroscope from a
 * steering loop that weaves, and a ranger that starves the gyroscope's thread. Each {@code imu} line carries:
 *
 * <ul>
 *   <li>{@code t}: the milliseconds since {@link #start()};</li>
 *   <li>{@code gyroMdeg}: the gyroscope's direction in thousandths of a degree, counter-clockwise positive;</li>
 *   <li>{@code reads}: how many times its thread read the gyroscope since the previous line, about ten in 100 ms, fewer
 *       when the thread is starved;</li>
 *   <li>{@code lineX} and {@code lineY}, when the pilot measures them: the travel's progress along its line and its
 *       offset to the left of it, which shows a weave;</li>
 *   <li>the ranger's window since the previous line: {@code rng}, its last distance in millimetres ({@code -1} for no
 *       echo); {@code rngReads}; {@code busyUs} and {@code maxUs}, the microseconds their echo waits held the processor
 *       in all and at most; and {@code timeouts}, the reads that heard no echo.</li>
 * </ul>
 *
 * <p>On the Powered Up robot it showed a heading swinging 20 degrees either way while the line stayed within 20 mm, and
 * a ranger that never heard an echo holding the processor 30 ms a read.
 *
 * <p>Give the obstacle detector {@link #ranger()} instead of the ranger itself, so its reads are timed: {@code pulseIn}
 * holds the processor while it waits for the echo, so no other thread runs meanwhile. Each line is built under the
 * syslog's lock, which the program's own sends from other threads must take too. A program with a link that must not be
 * interrupted mid-exchange, such as the RCX's infrared link, holds that link's lock around {@link #sample()}. Create it
 * once at startup; sampling allocates nothing.
 */
public final class NavigationTelemetry {
    private final Syslog syslog;
    private final GyroHeading heading;
    private final Clock clock;
    private final TimedRangeFinder ranger;
    /** A pose for the lines without one: never sent. */
    private final Pose noLine = new Pose();
    private int started;
    private int reads;

    /** Telemetry for {@code heading} and {@code ranger}, sent with {@code syslog}. */
    public NavigationTelemetry(Syslog syslog, GyroHeading heading, Clock clock, RangeFinder ranger) {
        this.syslog = syslog;
        this.heading = heading;
        this.clock = clock;
        this.ranger = new TimedRangeFinder(ranger, clock);
    }

    /** The ranger, timed: give this to the obstacle detector. */
    public RangeFinder ranger() {
        return ranger;
    }

    /** Starts the clock and the gyroscope's read count from now, as the navigator sets off. */
    public void start() {
        started = clock.millis();
        reads = heading.samples();
    }

    /** Sends one line, for a pilot that does not measure its line. */
    public void sample() {
        send(noLine, false);
    }

    /** Sends one line with {@code line}, the pilot's pose along the line of its travel. */
    public void sample(Pose line) {
        send(line, true);
    }

    private void send(Pose line, boolean withLine) {
        int readsNow = heading.samples();
        synchronized (syslog) {
            syslog.begin(Syslog.INFO).text("imu t=").number(clock.millis() - started).text(" gyroMdeg=")
                    .number((int) (heading.getDegreesCartesian() * 1000f)).text(" reads=").number(readsNow - reads);
            if (withLine) {
                syslog.text(" lineX=").number(line.xMillimetres()).text(" lineY=").number(line.yMillimetres());
            }
            ranger.appendTo(syslog);
            syslog.send();
        }
        reads = readsNow;
    }

    /**
     * The ranger, timed on the thread that reads it, the pilot's. Only that thread writes the counters; only the
     * sampling thread reads them and keeps its copies.
     */
    private static final class TimedRangeFinder implements RangeFinder {
        private final RangeFinder ranger;
        private final Clock clock;
        private volatile int reads;
        private volatile int busyMicros;
        private volatile int maxMicros;
        private volatile int timeouts;
        private volatile int lastMillimetres = -1;
        private int reportedReads;
        private int reportedBusyMicros;
        private int reportedTimeouts;

        TimedRangeFinder(RangeFinder ranger, Clock clock) {
            this.ranger = ranger;
            this.clock = clock;
        }

        @Override
        public float getRange() {
            int start = clock.micros();
            float metres = ranger.getRange();
            int spent = clock.micros() - start;
            reads = reads + 1;
            busyMicros = busyMicros + spent;
            if (spent > maxMicros) {
                maxMicros = spent;
            }
            // No echo reads as NaN, which fails every comparison.
            if (metres >= 0f) {
                lastMillimetres = (int) (metres * 1000f);
            } else {
                timeouts = timeouts + 1;
                lastMillimetres = -1;
            }
            return metres;
        }

        /**
         * Appends the window since the previous line and starts the next. The longest read is reset here, so one the
         * pilot's thread finishes meanwhile may be missed: enough for a log.
         */
        void appendTo(Syslog syslog) {
            int readsNow = reads;
            int busyNow = busyMicros;
            int timeoutsNow = timeouts;
            syslog.text(" rng=").number(lastMillimetres).text(" rngReads=").number(readsNow - reportedReads)
                    .text(" busyUs=").number(busyNow - reportedBusyMicros).text(" maxUs=").number(maxMicros)
                    .text(" timeouts=").number(timeoutsNow - reportedTimeouts);
            reportedReads = readsNow;
            reportedBusyMicros = busyNow;
            reportedTimeouts = timeoutsNow;
            maxMicros = 0;
        }
    }
}
