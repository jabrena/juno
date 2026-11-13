package io.github.jabrena.juno.usecases.lichess.win;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.lichess.commons.LichessViews;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** Celebrates a solved daily puzzle before returning to the challenge menu. */
public final class WinView implements View {
    private static final int WIDTH = 240;
    private static final int WIN_MILLIS = 2000;
    private static final int HEADER_BACKGROUND = 0x2945;
    private static final int PANEL_X = 18;
    private static final int PANEL_Y = 72;
    private static final int PANEL_WIDTH = 204;
    private static final int PANEL_HEIGHT = 128;

    private final Navigator navigator;
    private final Clock clock;
    private int enteredAt;

    private final TftTouchShield tft;

    public WinView(TftTouchShield tft, Navigator navigator) {
        this(tft, navigator, MCU.getClock());
    }

    public WinView(TftTouchShield tft, Navigator navigator, Clock clock) {
        this.tft = tft;
        this.navigator = navigator;
        this.clock = clock;
    }

    @Override
    public void enter() {
        enteredAt = clock.millis();
        draw(tft);
    }

    @Override
    public void render() {
        if (clock.millis() - enteredAt >= WIN_MILLIS) {
            navigator.show(LichessViews.MENU);
        }
    }

    @Override
    public void touch(int x, int y) {
        // Keep the celebration visible for the full two seconds.
    }

    private void draw(TftTouchShield tft) {
        tft.fillScreen(TftTouchShield.BLACK);
        tft.fillRect(0, 0, WIDTH, 36, HEADER_BACKGROUND);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        tft.setCursor(8, 2);
        tft.print("LICHESS");
        tft.setTextSize(2);
        tft.setCursor(8, 17);
        tft.print("DAILY PUZZLE");

        tft.fillRect(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, TftTouchShield.NAVY);
        tft.drawRect(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, TftTouchShield.WHITE);
        tft.setTextSize(3);
        tft.setTextColor(TftTouchShield.GREEN, TftTouchShield.NAVY);
        tft.setCursor(centeredX("YOU WIN!", 3), 94);
        tft.print("YOU WIN!");
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        tft.setCursor(centeredX("PUZZLE SOLVED", 2), 140);
        tft.print("PUZZLE SOLVED");
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.GRAY, TftTouchShield.NAVY);
        tft.setCursor(centeredX("RETURNING TO MENU", 1), 180);
        tft.print("RETURNING TO MENU");
    }

    private static int centeredX(String text, int size) {
        return (WIDTH - text.length() * TftTouchShield.CHAR_WIDTH * size) / 2;
    }
}
