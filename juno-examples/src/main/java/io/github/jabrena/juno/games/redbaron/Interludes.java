package io.github.jabrena.juno.games.redbaron;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The propeller start, take-off and letter-by-letter title scenes. */
final class Interludes {
    private static final int SPIN_FRAMES = 60;
    private static final int PROP_LENGTH = 78;
    private static final int TAKEOFF_FRAMES = 56;
    private static final int RUNWAY_HALF_WIDTH = 60;
    private static final int EYE_HEIGHT = 30;
    private static final int PROPELLER = 0xC618;
    private static final int RUNWAY = 0x8410;
    private static final String TITLE = "RED BARON";

    private Interludes() {
    }

    static void opening(short[] lines) {
        TftTouchShield.fillScreen(DisplayList.SKY);
        DisplayList.shown = 0;
        Camera.bankCos = 1024;
        Camera.bankSin = 0;
        Hud.showCentered("CONTACT!", 28, 2, TftTouchShield.YELLOW);
        int angle = 0;
        for (int f = 0; f < SPIN_FRAMES; f++) {
            int speed = 2 + f * f / 45;
            angle = (angle + speed) % 360;
            DisplayList.begin();
            for (int k = 0; k < 8; k++) {
                DisplayList.addLine(lines, Camera.CENTER_X + SceneRenderer.sightX(k) * 2,
                        Camera.CENTER_Y + SceneRenderer.sightY(k) * 2,
                        Camera.CENTER_X + SceneRenderer.sightX(k + 1) * 2,
                        Camera.CENTER_Y + SceneRenderer.sightY(k + 1) * 2, SceneRenderer.COCKPIT);
            }
            if (speed > 40) {
                for (int k = 0; k < 16; k++) {
                    float a0 = (float) Math.toRadians(k * 22.5f);
                    float a1 = (float) Math.toRadians(k * 22.5f + 22.5f);
                    DisplayList.addLine(lines,
                            Camera.CENTER_X + Math.round((float) Math.cos(a0) * PROP_LENGTH),
                            Camera.CENTER_Y + Math.round((float) Math.sin(a0) * PROP_LENGTH),
                            Camera.CENTER_X + Math.round((float) Math.cos(a1) * PROP_LENGTH),
                            Camera.CENTER_Y + Math.round((float) Math.sin(a1) * PROP_LENGTH),
                            SceneRenderer.HORIZON);
                }
            }
            if (speed <= 60) {
                float radians = (float) Math.toRadians(angle);
                int dx = Math.round((float) Math.cos(radians) * PROP_LENGTH);
                int dy = Math.round((float) Math.sin(radians) * PROP_LENGTH);
                DisplayList.addLine(lines, Camera.CENTER_X - dx, Camera.CENTER_Y - dy,
                        Camera.CENTER_X + dx, Camera.CENTER_Y + dy, PROPELLER);
            }
            DisplayList.present(lines);
            Delay.millis(30);
        }
        Delay.millis(200);
        takeOff(lines);
        TftTouchShield.fillScreen(DisplayList.SKY);
        DisplayList.shown = 0;
        Delay.millis(250);
    }

    private static void takeOff(short[] lines) {
        TftTouchShield.fillScreen(DisplayList.SKY);
        DisplayList.shown = 0;
        int pitch = 0;
        int travel = 0;
        for (int f = 0; f < TAKEOFF_FRAMES; f++) {
            DisplayList.begin();
            travel = travel + 8 + 2 * f;
            if (f > TAKEOFF_FRAMES - 22) {
                pitch = pitch + 7;
            }
            int horizon = Camera.CENTER_Y + pitch;
            DisplayList.addLine(lines, 0, horizon, DisplayList.WIDTH - 1, horizon, SceneRenderer.HORIZON);
            for (int side = -1; side <= 1; side = side + 2) {
                DisplayList.addLine(lines,
                        Camera.CENTER_X + side * RUNWAY_HALF_WIDTH * Camera.FOCAL / Camera.NEAR,
                        horizon + EYE_HEIGHT * Camera.FOCAL / Camera.NEAR,
                        Camera.CENTER_X + side * RUNWAY_HALF_WIDTH * Camera.FOCAL / Camera.FAR,
                        horizon + EYE_HEIGHT * Camera.FOCAL / Camera.FAR, RUNWAY);
            }
            for (int k = 0; k < 12; k++) {
                int z = Camera.NEAR + k * 120 + (120 - travel % 120);
                DisplayList.addLine(lines, Camera.CENTER_X, horizon + EYE_HEIGHT * Camera.FOCAL / z,
                        Camera.CENTER_X, horizon + EYE_HEIGHT * Camera.FOCAL / (z + 50), SceneRenderer.LINE);
            }
            SceneRenderer.drawCockpit(lines);
            DisplayList.present(lines);
            Delay.millis(30);
        }
        Delay.millis(200);
    }

    static void drawTitle(short[] lines, byte[] letter) {
        DisplayList.begin();
        Camera.altitude = 300;
        Camera.heading = 30;
        Camera.bankCos = 1024;
        Camera.bankSin = 0;
        SceneRenderer.drawLandscape(lines);
        SceneRenderer.drawBiplane(lines, -120, 330, 520, 150, TftTouchShield.WHITE);
        SceneRenderer.drawBiplane(lines, 260, 420, 1400, 200, TftTouchShield.WHITE);
        DisplayList.present(lines);
        int left = (DisplayList.WIDTH - TITLE.length() * 24) / 2;
        TftTouchShield.setTextSize(4);
        for (int i = 0; i < TITLE.length(); i++) {
            letter[0] = (byte) TITLE.charAt(i);
            TftTouchShield.setTextColor(TftTouchShield.WHITE, DisplayList.SKY);
            TftTouchShield.setCursor(left + i * 24, 34);
            TftTouchShield.print(letter, 1);
            Delay.millis(50);
            TftTouchShield.setTextColor(TftTouchShield.RED, DisplayList.SKY);
            TftTouchShield.setCursor(left + i * 24, 34);
            TftTouchShield.print(letter, 1);
            Delay.millis(70);
        }
        Delay.millis(200);
        Hud.showCentered("Drag to fly, tap to fire", 198, 1, TftTouchShield.WHITE);
        Hud.showCentered("Tap to start", 214, 1, TftTouchShield.CYAN);
    }
}
