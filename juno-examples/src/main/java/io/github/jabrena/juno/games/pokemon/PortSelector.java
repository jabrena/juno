package io.github.jabrena.juno.games.pokemon;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.net.Udp;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The "CHOOSE PORT" screen of {@link PokemonBattle}: pick the UDP port both boards use, then listen on it. */
final class PortSelector {
    static final int FIRST_PORT = 5077;
    private static final int PORT_COUNT = 20;

    private PortSelector() {
    }

    /** Lets the player pick the UDP port, then listens on it; a refused port returns to the selector. */
    static int choose(int port) {
        int offset = port - FIRST_PORT;
        drawPortSelection(FIRST_PORT + offset);
        while (true) {
            if (!TftTouchShield.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int x = TftTouchShield.touchX();
            int y = TftTouchShield.touchY();
            PokemonBattle.waitForRelease();
            if (y >= 100 && y < 170 && x < 120) {
                offset = (offset + PORT_COUNT - 1) % PORT_COUNT;
                drawPortSelection(FIRST_PORT + offset);
            } else if (y >= 100 && y < 170) {
                offset = (offset + 1) % PORT_COUNT;
                drawPortSelection(FIRST_PORT + offset);
            } else if (y >= 235 && y < 290) {
                if (Udp.listen(FIRST_PORT + offset)) {
                    return FIRST_PORT + offset;
                }
                PokemonBattle.showMessage("PORT IN USE", TftTouchShield.RED);
                Delay.millis(1500);
                drawPortSelection(FIRST_PORT + offset);
            }
        }
    }

    private static void drawPortSelection(int port) {
        TftTouchShield.fillScreen(PokemonBattle.BACKGROUND);
        PokemonBattle.title("CHOOSE PORT");
        TftTouchShield.fillRect(PokemonBattle.BUTTON_X, 100, 60, 70, PokemonBattle.PANEL);
        TftTouchShield.fillRect(160, 100, 60, 70, PokemonBattle.PANEL);
        TftTouchShield.setTextSize(4);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, PokemonBattle.PANEL);
        TftTouchShield.setCursor(37, 117);
        TftTouchShield.print("-");
        TftTouchShield.setCursor(177, 117);
        TftTouchShield.print("+");
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, PokemonBattle.BACKGROUND);
        TftTouchShield.setCursor(84, 123);
        TftTouchShield.print(port);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, PokemonBattle.BACKGROUND);
        TftTouchShield.setCursor(20, 190);
        TftTouchShield.print("Both boards must use the same port");
        TftTouchShield.fillRect(PokemonBattle.BUTTON_X, 235, PokemonBattle.BUTTON_WIDTH, 55, TftTouchShield.CYAN);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, TftTouchShield.CYAN);
        TftTouchShield.setCursor(62, 254);
        TftTouchShield.print("CONNECT");
    }
}
