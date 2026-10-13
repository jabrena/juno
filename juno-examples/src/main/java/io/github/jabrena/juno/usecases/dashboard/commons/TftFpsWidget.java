package io.github.jabrena.juno.usecases.dashboard.commons;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * The frame-rate label every screen shows in its bottom-left corner. One instance is shared by all the screens: the
 * active screen calls {@link #update()} once per frame, so the count carries on across screen changes. Once a second
 * the rate is recomputed and printed over serial; the label is redrawn only when the number changes.
 */
public final class TftFpsWidget {
    private static final int WINDOW_MS = 1000;
    private static final int UNSHOWN = -1;

    private int windowStart = Clock.millis();
    private int framesInWindow;
    private int framesPerSecond;
    private int shownFramesPerSecond = UNSHOWN;

    /** Forgets what was drawn, so the next update redraws the label; called when a screen clears the display. */
    public void reset() {
        shownFramesPerSecond = UNSHOWN;
    }

    /** Counts this frame, and redraws the label if the rate changed. */
    public void update() {
        framesInWindow = framesInWindow + 1;
        int now = Clock.millis();
        if (now - windowStart >= WINDOW_MS) {
            framesPerSecond = framesInWindow;
            framesInWindow = 0;
            windowStart = now;
            Serial.print("FPS ");
            Serial.println(framesPerSecond);
        }
        if (framesPerSecond != shownFramesPerSecond) {
            shownFramesPerSecond = framesPerSecond;
            draw();
        }
    }

    private void draw() {
        TftTouchShield.fillRect(12, 212, 150, 20, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(12, 216);
        TftTouchShield.print("FPS ");
        TftTouchShield.print(framesPerSecond);
    }
}
