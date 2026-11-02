package io.github.jabrena.juno.usecases.dashboard.commons.ux;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** The title bar every screen shows at the top: its title and a cyan rule under it. */
public final class TftHeaderWidget {
    private final String title;

    public TftHeaderWidget(String title) {
        this.title = title;
    }

    /** Draws the title bar; called after the screen was cleared to navy. */
    public void draw(TftTouchShield tft) {
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        tft.setCursor(12, 12);
        tft.print(title);
        tft.drawHorizontalLine(12, 34, 296, TftTouchShield.CYAN);
    }
}
