package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** Celebrates a solved daily puzzle before returning to the challenge menu. */
final class WinView implements View {
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

    WinView(Navigator navigator) {
        this(navigator, ArduinoUno.getClock());
    }

    WinView(Navigator navigator, Clock clock) {
        this.navigator = navigator;
        this.clock = clock;
    }

    @Override
    public void enter() {
        enteredAt = clock.millis();
        draw();
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

    private void draw() {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.fillRect(0, 0, WIDTH, 36, HEADER_BACKGROUND);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, HEADER_BACKGROUND);
        TftTouchShield.setCursor(8, 2);
        TftTouchShield.print("LICHESS");
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(8, 17);
        TftTouchShield.print("DAILY PUZZLE");

        TftTouchShield.fillRect(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, TftTouchShield.NAVY);
        TftTouchShield.drawRect(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.GREEN, TftTouchShield.NAVY);
        TftTouchShield.setCursor(centeredX("YOU WIN!", 3), 94);
        TftTouchShield.print("YOU WIN!");
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(centeredX("PUZZLE SOLVED", 2), 140);
        TftTouchShield.print("PUZZLE SOLVED");
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.NAVY);
        TftTouchShield.setCursor(centeredX("RETURNING TO MENU", 1), 180);
        TftTouchShield.print("RETURNING TO MENU");
    }

    private static int centeredX(String text, int size) {
        return (WIDTH - text.length() * TftTouchShield.CHAR_WIDTH * size) / 2;
    }
}
