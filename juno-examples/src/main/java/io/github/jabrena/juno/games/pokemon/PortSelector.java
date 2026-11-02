package io.github.jabrena.juno.games.pokemon;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.net.udp.Udp;
import io.github.jabrena.juno.util.Delay;

/** The "CHOOSE PORT" screen of {@link PokemonBattle}: pick the UDP port both boards use, then listen on it. */
final class PortSelector {
    static final int FIRST_PORT = 5077;
    private static final int PORT_COUNT = 20;

    private PortSelector() {
    }

    /** Lets the player pick the UDP port, then listens on it; a refused port returns to the selector. */
    static int choose(TftTouchShield tft, int port) {
        int offset = port - FIRST_PORT;
        drawPortSelection(tft, FIRST_PORT + offset);
        while (true) {
            if (!tft.readTouch()) {
                Delay.millis(10);
                continue;
            }
            int x = tft.touchX();
            int y = tft.touchY();
            PokemonBattle.waitForRelease(tft);
            if (y >= 100 && y < 170 && x < 120) {
                offset = (offset + PORT_COUNT - 1) % PORT_COUNT;
                drawPortSelection(tft, FIRST_PORT + offset);
            } else if (y >= 100 && y < 170) {
                offset = (offset + 1) % PORT_COUNT;
                drawPortSelection(tft, FIRST_PORT + offset);
            } else if (y >= 235 && y < 290) {
                if (Udp.listen(FIRST_PORT + offset)) {
                    return FIRST_PORT + offset;
                }
                PokemonBattle.showMessage(tft, "PORT IN USE", TftTouchShield.RED);
                Delay.millis(1500);
                drawPortSelection(tft, FIRST_PORT + offset);
            }
        }
    }

    private static void drawPortSelection(TftTouchShield tft, int port) {
        tft.fillScreen(PokemonBattle.BACKGROUND);
        PokemonBattle.title(tft, "CHOOSE PORT");
        tft.fillRect(PokemonBattle.BUTTON_X, 100, 60, 70, PokemonBattle.PANEL);
        tft.fillRect(160, 100, 60, 70, PokemonBattle.PANEL);
        tft.setTextSize(4);
        tft.setTextColor(TftTouchShield.WHITE, PokemonBattle.PANEL);
        tft.setCursor(37, 117);
        tft.print("-");
        tft.setCursor(177, 117);
        tft.print("+");
        tft.setTextSize(3);
        tft.setTextColor(TftTouchShield.YELLOW, PokemonBattle.BACKGROUND);
        tft.setCursor(84, 123);
        tft.print(port);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, PokemonBattle.BACKGROUND);
        tft.setCursor(20, 190);
        tft.print("Both boards must use the same port");
        tft.fillRect(PokemonBattle.BUTTON_X, 235, PokemonBattle.BUTTON_WIDTH, 55, TftTouchShield.CYAN);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.BLACK, TftTouchShield.CYAN);
        tft.setCursor(62, 254);
        tft.print("CONNECT");
    }
}
