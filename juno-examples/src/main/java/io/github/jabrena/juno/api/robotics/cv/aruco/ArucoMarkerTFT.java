package io.github.jabrena.juno.api.robotics.cv.aruco;

import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.robotics.cv.aruco.ArucoMarker;

/**
 * Draws ArUco marker 0 of the {@code DICT_4X4_50} dictionary in the center of the ELEGOO 2.8" TFT touch
 * screen, with its id printed below it. Point a camera at the screen to detect it.
 *
 * <p>The marker is 6 cells of 24 pixels with a one-cell white quiet zone, centered on the 240x320 portrait screen.
 */
@Board(ArduinoUnoR3WiFiRev2Board.class)
public final class ArucoMarkerTFT {
    private static final int MARKER_ID = 0;
    private static final int CELL = 24;
    // Top-left corner of the black border, centering the 144 px marker: (240 - 144) / 2 and (320 - 144) / 2.
    private static final int MARKER_X = 48;
    private static final int MARKER_Y = 88;
    private static final int CAPTION_SIZE = 2;
    private static final int CAPTION_X = 60;
    private static final int CAPTION_Y = 260;

    private ArucoMarkerTFT() {}

    /**
     * Starts the screen in portrait, paints the black background, draws the marker and its caption, then does nothing.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        TftTouchShield tft = new TftTouchShield();
        tft.setRotation(TftTouchShield.PORTRAIT);
        tft.fillScreen(TftTouchShield.BLACK);
        ArucoMarker.draw(tft, MARKER_ID, MARKER_X, MARKER_Y, CELL);
        tft.setTextSize(CAPTION_SIZE);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        tft.setCursor(CAPTION_X, CAPTION_Y);
        tft.print("ArUco 0");
    }
}
