package io.github.jabrena.juno.usecases.supabase;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SensorClockTest {

    @Test
    void countsDaysSinceTheUnixEpoch() {
        assertThat(SensorClock.daysFromCivil(1970, 1, 1)).isZero();
        assertThat(SensorClock.daysFromCivil(1970, 1, 2)).isEqualTo(1);
        assertThat(SensorClock.daysFromCivil(2000, 3, 1) - SensorClock.daysFromCivil(2000, 2, 28)).isEqualTo(2);
    }

    @Test
    void advancesWithinTheDay() {
        SensorClock clock = new SensorClock();
        clock.start(2026, 10, 16, 12, 0, 0, 5_000);

        clock.update(5_000 + 61_500);

        assertThat(format(clock)).isEqualTo("2026-10-16T12:01:01Z");
    }

    @Test
    void carriesIntoTheNextMonth() {
        SensorClock clock = new SensorClock();
        clock.start(2026, 10, 31, 23, 59, 30, 0);

        clock.update(60_000);

        assertThat(format(clock)).isEqualTo("2026-11-01T00:00:30Z");
    }

    @Test
    void carriesIntoALeapDay() {
        SensorClock clock = new SensorClock();
        clock.start(2028, 2, 28, 23, 59, 59, 0);

        clock.update(1_000);

        assertThat(format(clock)).isEqualTo("2028-02-29T00:00:00Z");
    }

    @Test
    void skipsNoLeapDayInACenturyYear() {
        SensorClock clock = new SensorClock();
        clock.start(2100, 2, 28, 23, 59, 59, 0);

        clock.update(1_000);

        assertThat(format(clock)).isEqualTo("2100-03-01T00:00:00Z");
    }

    @Test
    void elapsedTimeSurvivesTheMillisecondCounterWrapping() {
        int origin = Integer.MAX_VALUE - 500;
        SensorClock clock = new SensorClock();
        clock.start(2026, 10, 16, 12, 0, 0, origin);

        clock.update(origin + 2_500);

        assertThat(format(clock)).isEqualTo("2026-10-16T12:00:02Z");
    }

    @Test
    void writesTheUtcCutoffOneHourEarlierAcrossAMonthBoundary() {
        SensorClock clock = new SensorClock();
        byte[] cutoff = new byte[20];
        clock.start(2026, 11, 1, 0, 30, 0, 0);

        int length = clock.writeUtcMinusOneHour(cutoff, cutoff.length);

        assertThat(length).isEqualTo(20);
        assertThat(new String(cutoff, 0, length, StandardCharsets.US_ASCII))
                .isEqualTo("2026-10-31T23:30:00Z");
    }

    private static String format(SensorClock clock) {
        return "%04d-%02d-%02dT%02d:%02d:%02dZ".formatted(clock.year(), clock.month(), clock.day(),
                clock.hour(), clock.minute(), clock.second());
    }
}
