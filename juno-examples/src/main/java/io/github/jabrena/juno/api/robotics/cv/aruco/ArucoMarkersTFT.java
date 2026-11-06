package io.github.jabrena.juno.api.robotics.cv.aruco;

import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.robotics.cv.aruco.ArucoMarker;
import io.github.jabrena.juno.util.Delay;

/**
 * Cycles through every marker of the {@code DICT_4X4_50} dictionary (ids 0 to 49) on the ELEGOO 2.8" TFT touch
 * screen: each marker is drawn in the center with its id printed below it, and stays for 5 seconds. Point a
 * camera at the screen to detect it.
 *
 * <p>The marker is 6 cells of 24 pixels with a one-cell white quiet zone, centered on the 240x320 portrait screen.
 */
@Board(ArduinoUnoR3WiFiRev2Board.class)
public final class ArucoMarkersTFT {
    private static final int MARKERS = ArucoMarker.MARKERS;
    private static final int HOLD_MS = 5000;
    private static final int CELL = 24;
    // Top-left corner of the black border, centering the 144 px marker: (240 - 144) / 2 and (320 - 144) / 2.
    private static final int MARKER_X = 48;
    private static final int MARKER_Y = 88;
    private static final int CAPTION_SIZE = 2;
    private static final int CAPTION_X = 60;
    private static final int CAPTION_Y = 260;

    private ArucoMarkersTFT() {}

    /**
     * Starts the screen in portrait, then loops forever: for each id from 0 to 49, clears the screen, draws the
     * marker with its caption and waits 5 seconds.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        TftTouchShield tft = new TftTouchShield();
        tft.setRotation(TftTouchShield.PORTRAIT);
        tft.setTextSize(CAPTION_SIZE);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);

        while (true) {
            int id = 0;
            while (id < MARKERS) {
                tft.fillScreen(TftTouchShield.BLACK);
                ArucoMarker.draw(tft, id, MARKER_X, MARKER_Y, CELL);
                tft.setCursor(CAPTION_X, CAPTION_Y);
                tft.print("ArUco ");
                tft.print(id);
                Delay.millis(HOLD_MS);
                id = id + 1;
            }
        }
    }
}
