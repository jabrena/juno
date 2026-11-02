package io.github.jabrena.juno.usecases.miner.cover;

import io.github.jabrena.juno.games.blockchain.GameScreen;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.miner.commons.Miner2Header;
import io.github.jabrena.juno.usecases.miner.commons.Miner2Views;
import io.github.jabrena.juno.usecases.miner.services.NodeService;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/**
 * Miner 2's cover: the MINER title with a PLAY button that opens the blocks board and starts the node. The Wi-Fi and
 * SD icons sit where the other views' header shows them, so the node can be seen joining Wi-Fi. The startup intro is a
 * separate view, shown once.
 */
public final class CoverView implements View {

    private final TftTouchShield tft;
    private static final int WIDTH = 240;
    private static final int TITLE_SIZE = 6;
    private static final int TITLE_Y = 80;
    private static final int PLAY_X = 50;
    private static final int PLAY_Y = 200;
    private static final int PLAY_WIDTH = 140;
    private static final int PLAY_HEIGHT = 50;

    private final Navigator navigator;
    private final NodeService node;
    private final Miner2Header header;

    public CoverView(TftTouchShield tft, Navigator navigator, NodeService node, Miner2Header header) {
        this.tft = tft;
        this.navigator = navigator;
        this.node = node;
        this.header = header;
    }

    @Override
    public void enter() {
        drawTitle(tft);
        header.redrawIcons(tft);
    }

    @Override
    public void render() {
        header.render(tft);
    }

    @Override
    public void touch(int x, int y) {
        if (isPlay(x, y)) {
            node.begin();
            Serial.println("Miner2: opening the blocks");
            navigator.show(Miner2Views.BLOCKS);
        }
    }

    /** Whether ({@code x}, {@code y}) is on the PLAY button. */
    static boolean isPlay(int x, int y) {
        return x >= PLAY_X && x < PLAY_X + PLAY_WIDTH && y >= PLAY_Y && y < PLAY_Y + PLAY_HEIGHT;
    }

    private static void drawTitle(TftTouchShield tft) {
        tft.fillScreen(GameScreen.BACKGROUND);
        tft.setTextSize(TITLE_SIZE);
        tft.setTextColor(TftTouchShield.GREEN, GameScreen.BACKGROUND);
        tft.setCursor((WIDTH - 5 * 6 * TITLE_SIZE) / 2, TITLE_Y);
        tft.print("MINER");
        tft.fillRect(PLAY_X, PLAY_Y, PLAY_WIDTH, PLAY_HEIGHT, GameScreen.PANEL);
        tft.drawRect(PLAY_X, PLAY_Y, PLAY_WIDTH, PLAY_HEIGHT, TftTouchShield.GREEN);
        tft.setTextSize(3);
        tft.setTextColor(TftTouchShield.WHITE, GameScreen.PANEL);
        tft.setCursor(PLAY_X + (PLAY_WIDTH - 4 * 18) / 2, PLAY_Y + (PLAY_HEIGHT - 21) / 2);
        tft.print("PLAY");
    }
}
