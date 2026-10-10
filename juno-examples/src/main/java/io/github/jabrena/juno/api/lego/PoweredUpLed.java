package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.api.lego.PoweredUpState.*;

/** The LED view of {@link PoweredUpHubTFT}: a 3x3 grid of color swatches. */
final class PoweredUpLed {
    static final int[] LED_COLORS = {
        PoweredUpHubRemote.COLOR_RED,
        PoweredUpHubRemote.COLOR_ORANGE,
        PoweredUpHubRemote.COLOR_YELLOW,
        PoweredUpHubRemote.COLOR_GREEN,
        PoweredUpHubRemote.COLOR_CYAN,
        PoweredUpHubRemote.COLOR_BLUE,
        PoweredUpHubRemote.COLOR_PURPLE,
        PoweredUpHubRemote.COLOR_PINK,
        PoweredUpHubRemote.COLOR_WHITE
    };

    private PoweredUpLed() {
    }

    static void onTouch(PoweredUpState s, int x, int y) {
        for (int i = 0; i < LED_COLORS.length; i++) {
            if (PoweredUpUi.inBox(x, y, swatchX(i), swatchY(i), SWATCH_WIDTH, SWATCH_HEIGHT)) {
                s.ledIndex = i;
                PoweredUpHubRemote.setLedColor(LED_COLORS[i]);
                draw(s);
            }
        }
    }

    /** A 3x3 grid of swatches in roughly the colors the hub LED shows; the chosen one has a white frame. */
    static void draw(PoweredUpState s) {
        for (int i = 0; i < LED_COLORS.length; i++) {
            int x = swatchX(i);
            int y = swatchY(i);
            TftTouchShield.fillRect(x, y, SWATCH_WIDTH, SWATCH_HEIGHT, swatchColor(LED_COLORS[i]));
            int frame = i == s.ledIndex ? TftTouchShield.WHITE : TftTouchShield.BLACK;
            TftTouchShield.drawRect(x, y, SWATCH_WIDTH, SWATCH_HEIGHT, frame);
            TftTouchShield.drawRect(x + 1, y + 1, SWATCH_WIDTH - 2, SWATCH_HEIGHT - 2, frame);
        }
    }

    private static int swatchX(int index) {
        return MARGIN + (index % 3) * (SWATCH_WIDTH + SWATCH_GAP);
    }

    private static int swatchY(int index) {
        return SWATCH_TOP + (index / 3) * (SWATCH_HEIGHT + SWATCH_GAP);
    }

    private static int swatchColor(int hubColor) {
        if (hubColor == PoweredUpHubRemote.COLOR_RED) {
            return TftTouchShield.RED;
        }
        if (hubColor == PoweredUpHubRemote.COLOR_ORANGE) {
            return TftTouchShield.ORANGE;
        }
        if (hubColor == PoweredUpHubRemote.COLOR_YELLOW) {
            return TftTouchShield.YELLOW;
        }
        if (hubColor == PoweredUpHubRemote.COLOR_GREEN) {
            return TftTouchShield.GREEN;
        }
        if (hubColor == PoweredUpHubRemote.COLOR_CYAN) {
            return TftTouchShield.CYAN;
        }
        if (hubColor == PoweredUpHubRemote.COLOR_BLUE) {
            return TftTouchShield.BLUE;
        }
        if (hubColor == PoweredUpHubRemote.COLOR_PURPLE) {
            return TftTouchShield.MAGENTA;
        }
        if (hubColor == PoweredUpHubRemote.COLOR_PINK) {
            return TftTouchShield.color(255, 128, 192);
        }
        return TftTouchShield.WHITE;
    }
}
