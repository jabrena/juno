package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.remote.lego.poweredup.HubColor;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;

import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.MARGIN;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.SWATCH_GAP;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.SWATCH_HEIGHT;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.SWATCH_TOP;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.SWATCH_WIDTH;

/** The LED view of {@link PoweredUpHubTFT}: a 3x3 grid of color swatches. */
final class PoweredUpLed {
    private static final RemotePoweredUpHub HUB = new RemotePoweredUpHub();
    static final HubColor[] LED_COLORS = {
        HubColor.RED,
        HubColor.ORANGE,
        HubColor.YELLOW,
        HubColor.GREEN,
        HubColor.CYAN,
        HubColor.BLUE,
        HubColor.PURPLE,
        HubColor.PINK,
        HubColor.WHITE
    };

    private PoweredUpLed() {
    }

    static void onTouch(TftTouchShield tft, PoweredUpState s, int x, int y) {
        for (int i = 0; i < LED_COLORS.length; i++) {
            if (PoweredUpUi.inBox(x, y, swatchX(i), swatchY(i), SWATCH_WIDTH, SWATCH_HEIGHT)) {
                s.ledIndex = i;
                HUB.setLedColor(LED_COLORS[i]);
                draw(tft, s);
            }
        }
    }

    /** A 3x3 grid of swatches in roughly the colors the hub LED shows; the chosen one has a white frame. */
    static void draw(TftTouchShield tft, PoweredUpState s) {
        for (int i = 0; i < LED_COLORS.length; i++) {
            int x = swatchX(i);
            int y = swatchY(i);
            tft.fillRect(x, y, SWATCH_WIDTH, SWATCH_HEIGHT, swatchColor(LED_COLORS[i]));
            int frame = i == s.ledIndex ? TftTouchShield.WHITE : TftTouchShield.BLACK;
            tft.drawRect(x, y, SWATCH_WIDTH, SWATCH_HEIGHT, frame);
            tft.drawRect(x + 1, y + 1, SWATCH_WIDTH - 2, SWATCH_HEIGHT - 2, frame);
        }
    }

    private static int swatchX(int index) {
        return MARGIN + (index % 3) * (SWATCH_WIDTH + SWATCH_GAP);
    }

    private static int swatchY(int index) {
        return SWATCH_TOP + (index / 3) * (SWATCH_HEIGHT + SWATCH_GAP);
    }

    private static int swatchColor(HubColor hubColor) {
        if (hubColor == HubColor.RED) {
            return TftTouchShield.RED;
        }
        if (hubColor == HubColor.ORANGE) {
            return TftTouchShield.ORANGE;
        }
        if (hubColor == HubColor.YELLOW) {
            return TftTouchShield.YELLOW;
        }
        if (hubColor == HubColor.GREEN) {
            return TftTouchShield.GREEN;
        }
        if (hubColor == HubColor.CYAN) {
            return TftTouchShield.CYAN;
        }
        if (hubColor == HubColor.BLUE) {
            return TftTouchShield.BLUE;
        }
        if (hubColor == HubColor.PURPLE) {
            return TftTouchShield.MAGENTA;
        }
        if (hubColor == HubColor.PINK) {
            return TftTouchShield.color(255, 128, 192);
        }
        return TftTouchShield.WHITE;
    }
}
