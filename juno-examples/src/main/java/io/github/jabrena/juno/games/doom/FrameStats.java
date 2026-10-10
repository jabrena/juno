package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * What a frame costs on the board, for {@code juno:monitor}: every {@link #PERIOD} frames it prints the average
 * microseconds spent on the game logic, on building the display list and on presenting it to the TFT, with the
 * address windows and pixels {@link DisplayList} sent per frame and the lines it dropped for want of room.
 */
final class FrameStats {
    /** Whether to print; the counting itself is a few additions per frame. */
    private static final boolean ENABLED = true;
    private static final int PERIOD = 64;
    private static final int FPS_PERIOD = 8;

    private static int frames;
    private static int logic;
    private static int build;
    private static int present;
    private static int currentFps;
    private static int fpsFrames;
    private static int fpsMicros;
    private static int lastFinished;

    private FrameStats() {
    }

    /** Records one frame from the {@code Clock.micros()} taken as each phase started and as the last one ended. */
    static void record(int started, int building, int presenting, int finished) {
        recordFps(finished);
        logic = logic + building - started;
        build = build + presenting - building;
        present = present + finished - presenting;
        frames = frames + 1;
        if (frames == PERIOD) {
            if (ENABLED) {
                report();
            }
            frames = 0;
            logic = 0;
            build = 0;
            present = 0;
            DisplayList.windows = 0;
            DisplayList.pixels = 0;
            DisplayList.dropped = 0;
        }
    }

    /** Starts timing a fresh map, excluding time spent on menus and intermissions. */
    static void reset() {
        frames = 0;
        logic = 0;
        build = 0;
        present = 0;
        currentFps = 0;
        fpsFrames = 0;
        fpsMicros = 0;
        lastFinished = 0;
    }

    /** Recent on-screen frame rate, averaged over eight complete frame intervals. */
    static int fps() {
        return currentFps;
    }

    private static void recordFps(int finished) {
        if (lastFinished != 0) {
            fpsMicros = fpsMicros + finished - lastFinished;
            fpsFrames = fpsFrames + 1;
            if (fpsFrames == FPS_PERIOD) {
                currentFps = fpsMicros > 0 ? FPS_PERIOD * 1_000_000 / fpsMicros : 0;
                fpsFrames = 0;
                fpsMicros = 0;
            }
        }
        lastFinished = finished;
    }

    private static void report() {
        Serial.print("DOOM: us logic=");
        Serial.print(logic / PERIOD);
        Serial.print(" build=");
        Serial.print(build / PERIOD);
        Serial.print(" present=");
        Serial.print(present / PERIOD);
        Serial.print(" windows=");
        Serial.print(DisplayList.windows / PERIOD);
        Serial.print(" pixels=");
        Serial.print(DisplayList.pixels / PERIOD);
        Serial.print(" dropped=");
        Serial.print(DisplayList.dropped);
        // Where the marine is, the waypoint the CPU walks to, health, and how many routes were planned so far.
        Serial.print(" cpu=");
        Serial.print((int) Player.x);
        Serial.print(",");
        Serial.print((int) Player.y);
        Serial.print(" t=");
        Serial.print(Autopilot.target);
        Serial.print("/");
        Serial.print(World.routeLength);
        Serial.print(" hp=");
        Serial.print(Player.health);
        Serial.print(" plans=");
        Serial.println(RoutePlanner.plans);
    }
}
