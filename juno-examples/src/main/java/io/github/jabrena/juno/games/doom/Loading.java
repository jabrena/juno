package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Times a map's loading and its route planning, and shows their progress. The work is known before it starts: the
 * WAD's directory gives every lump's size, so {@link WadLevel} knows how many records it will read (a line counting
 * three, for its two sidedef seeks), and planning grows with the map's subsectors. An estimate from per-unit rates
 * then drives a bar with the seconds left; each load and plan reports its real time over serial and recalibrates
 * its rate, so the next estimate is closer. Where nothing shows the bar (a re-plan mid-map), only the time is kept.
 */
final class Loading {
    /**
     * Initial rates, in microseconds per unit, measured on the UNO Q reading from the TFT shield's SD card: each record
     * costs a seek and a short read (E3M1: 1473 units in 10.2 s), while the planner settles a subsector in about a
     * millisecond. Each load and plan then refines them.
     */
    private static final int LOAD_MICROS_PER_UNIT = 7000;
    private static final int PLAN_MICROS_PER_SUBSECTOR = 1500;
    private static final int BAR_HEIGHT = 6;

    private static int loadRate = LOAD_MICROS_PER_UNIT;
    private static int planRate = PLAN_MICROS_PER_SUBSECTOR;

    private static boolean visible;
    private static int barX;
    private static int barY;
    private static int barWidth;
    private static int drawn;
    private static int secondsShown;

    private static boolean loading;
    private static boolean planning;
    private static int loadUnits;
    private static int loadDone;
    private static int planUnits;
    private static int planDone;
    private static int started;
    private static int estimateMillis;

    private Loading() {
    }

    /** Draws the next load's bar at ({@code x}, {@code y}), {@code width} pixels long, with the seconds left after it. */
    static void show(int x, int y, int width) {
        visible = true;
        barX = x;
        barY = y;
        barWidth = width;
        drawn = 0;
        secondsShown = -1;
        TftTouchShield.drawRect(x - 1, y - 1, width + 2, BAR_HEIGHT + 2, TftTouchShield.YELLOW);
    }

    /** A map is about to be read: {@code units} records, then a route over {@code subsectors}. */
    static void beginLoad(int units, int subsectors) {
        loading = true;
        loadUnits = Math.max(1, units);
        loadDone = 0;
        planUnits = Math.max(1, subsectors);
        planDone = 0;
        estimateMillis = (loadUnits * loadRate + planUnits * planRate) / 1000;
        started = Clock.millis();
        redraw();
    }

    /** {@code units} more records read. */
    static void advance(int units) {
        if (loading) {
            loadDone = loadDone + units;
            redraw();
        }
    }

    /** The map is read (or failed to): reports the time and recalibrates the reading rate. */
    static void endLoad() {
        if (!loading) {
            return;
        }
        loading = false;
        int elapsed = Clock.millis() - started;
        int rate = (int) ((long) elapsed * 1000 / loadUnits);
        loadRate = (loadRate + rate) / 2;
        report(" read ", loadUnits, " units in ", elapsed, (loadUnits * LOAD_MICROS_PER_UNIT) / 1000, rate);
        started = Clock.millis();
    }

    /** The route is about to be planned for the map just read. */
    static void beginPlan() {
        planning = true;
        planDone = 0;
        started = Clock.millis();
        loadDone = loadUnits;
        redraw();
    }

    /** One more subsector settled by the planner's search. */
    static void advancePlan() {
        if (planning) {
            planDone = Math.min(planUnits, planDone + 1);
            redraw();
        }
    }

    /** The route is planned: reports the time, recalibrates, fills the bar and stops drawing it. */
    static void endPlan() {
        if (!planning) {
            return;
        }
        planning = false;
        int elapsed = Clock.millis() - started;
        int rate = (int) ((long) elapsed * 1000 / planUnits);
        planRate = (planRate + rate) / 2;
        report(" planned over ", planUnits, " subsectors in ", elapsed,
                (planUnits * PLAN_MICROS_PER_SUBSECTOR) / 1000, rate);
        planDone = planUnits;
        redraw();
        visible = false;
    }

    private static void report(String what, int units, String unitName, int elapsed, int firstGuess, int rate) {
        Serial.print("DOOM: E");
        Serial.print(World.episode);
        Serial.print("M");
        Serial.print(World.map);
        Serial.print(what);
        Serial.print(units);
        Serial.print(unitName);
        Serial.print(elapsed);
        Serial.print(" ms (");
        Serial.print(rate);
        Serial.print(" us each; first guess ");
        Serial.print(firstGuess);
        Serial.println(" ms)");
    }

    /** Grows the bar to the estimated share of the work done and updates the seconds left. */
    private static void redraw() {
        if (!visible) {
            return;
        }
        int doneMicros = loadDone * loadRate + planDone * planRate;
        int totalMicros = Math.max(1, estimateMillis * 1000);
        int width = (int) Math.min(barWidth, (long) doneMicros * barWidth / totalMicros);
        if (width > drawn) {
            TftTouchShield.fillRect(barX + drawn, barY, width - drawn, BAR_HEIGHT, TftTouchShield.YELLOW);
            drawn = width;
        }
        int seconds = Math.max(0, (totalMicros - doneMicros + 999_999) / 1_000_000);
        if (seconds != secondsShown) {
            secondsShown = seconds;
            TftTouchShield.setTextSize(1);
            TftTouchShield.setTextColor(TftTouchShield.YELLOW, DisplayList.BACKGROUND);
            TftTouchShield.setCursor(barX + barWidth + 6, barY - 1);
            TftTouchShield.print("~");
            TftTouchShield.print(seconds);
            TftTouchShield.print("s ");
        }
    }
}
