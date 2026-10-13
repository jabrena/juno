package io.github.jabrena.juno.usecases.dashboard.commons;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The title bar every screen shows at the top: its title and a cyan rule under it. */
public final class TftHeaderWidget {
    private final String title;

    public TftHeaderWidget(String title) {
        this.title = title;
    }

    /** Draws the title bar; called after the screen was cleared to navy. */
    public void draw() {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(12, 12);
        TftTouchShield.print(title);
        TftTouchShield.drawHorizontalLine(12, 34, 296, TftTouchShield.CYAN);
    }
}
