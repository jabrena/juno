package io.github.jabrena.juno.api.thirdparty.tft;

import io.github.jabrena.juno.api.hardware.io.gpio.Gpio;

/**
 * Points the TFT driver, touch panel and bus included, at one {@link Gpio}, so a test in any package can drive the
 * shield's control lines and touch panel through a mocked {@code Gpio}. The driver's pin field is package-private
 * on purpose.
 */
public final class TftPins {
    private TftPins() {
    }

    public static void use(Gpio gpio) {
        TftTouchShield.gpio = gpio;
    }
}
