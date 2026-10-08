package io.github.jabrena.juno.games.doom;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * A wireframe walk through a DOOM map on the ELEGOO 2.8" TFT touch screen shield, after Eben Upton's
 * BBC Micro E1M1 renderer: full BSP traversal, perspective projection, occlusion and working doors.
 *
 * <p>The map lives in flash as {@code static final} tables ({@link Level}, {@link LevelVertices},
 * {@link LevelLines}, {@link LevelSegs}, {@link LevelNodes}); the committed ones describe a small original
 * test map, and {@code LevelGenerator} replaces them locally with a real map from a WAD you own. Like the
 * other 3D vector games it targets the UNO Q, whose Cortex-M33 has the speed its per-column
 * floating-point projection needs.
 *
 * <p>After the title, choose the pilot ({@link Controls}): HUMAN walks with the touch screen, CPU lets
 * {@link Autopilot} walk the map's demo route, and tapping the header switches between them.
 * {@link Player} walks and opens doors, {@link Renderer} builds each frame and {@link DisplayList} draws
 * only what changed.
 */
@Board(ArduinoUnoQ.class)
public final class Doom {
    private static final int FRAME_MILLIS = 40;

    private Doom() {
    }

    public static void main(String[] args) {
        short[] lines = new short[2 * DisplayList.LIST_SIZE];
        byte[] clips = new byte[2 * DisplayList.WIDTH];
        short[] stack = new short[64];
        short[] ceilings = new short[Level.SECTOR_CEILING.length];

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        title();
        Controls.choosePilot();
        Player.spawn(ceilings);
        Autopilot.restart();
        Hud.drawHeader();
        DisplayList.clearView();

        int next = Clock.millis();
        int frame = 0;
        while (true) {
            while (Clock.millis() - next < 0) {
                Delay.millis(1);
            }
            next = next + FRAME_MILLIS;
            if (Clock.millis() - next > 4 * FRAME_MILLIS) {
                next = Clock.millis();
            }
            frame = frame + 1;
            if (Controls.handle(ceilings)) {
                Hud.drawHeader();
            }
            if (Controls.autopilot) {
                Autopilot.step(ceilings);
            }
            Player.operateDoors(ceilings);
            Player.settle();
            Renderer.render(lines, clips, stack, ceilings);
            if ((frame & 3) == 0) {
                Hud.drawStatus();
            }
        }
    }

    private static void title() {
        TftTouchShield.fillScreen(DisplayList.BACKGROUND);
        Hud.showCentered("DOOM", 60, 6, TftTouchShield.RED);
        Hud.showCentered("WIREFRAME  " + Level.NAME, 128, 2, TftTouchShield.YELLOW);
        Hud.showCentered("JAVA ON ARDUINO UNO Q WITH JUNO", 168, 1, Renderer.WALL);
        Hud.showCentered("AFTER EBEN UPTON'S BBC MICRO PORT", 184, 1, Renderer.WALL_FAR);
        Hud.showCentered("TAP TO START", 214, 1, TftTouchShield.WHITE);
        Controls.waitForTap();
    }
}
