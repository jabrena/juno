package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** The challenge picker: daily puzzle or another public Lichess puzzle. */
final class ChallengeMenuView implements View {
    private final Navigator navigator;
    private final LichessApi api;
    private final LichessModel model;
    private boolean error;

    ChallengeMenuView(Navigator navigator, LichessApi api, LichessModel model) {
        this.navigator = navigator;
        this.api = api;
        this.model = model;
    }

    @Override
    public void enter() {
        error = false;
        drawMenu();
    }

    @Override
    public void render() {
    }

    @Override
    public void touch(int x, int y) {
        if (error) {
            if (y > 250) {
                navigator.show(LichessViews.COVER);
            }
            return;
        }
        if (y >= 84 && y < 148) {
            load(true);
        } else if (y >= 156 && y < 220) {
            load(false);
        } else if (y >= 244) {
            navigator.show(LichessViews.COVER);
        }
    }

    private void load(boolean daily) {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, TftTouchShield.BLACK);
        TftTouchShield.setCursor(48, 140);
        TftTouchShield.print("ASKING LICHESS...");
        boolean loaded = daily ? api.loadDaily(model) : api.loadFresh(model);
        if (loaded) {
            navigator.show(LichessViews.CHALLENGE);
        } else {
            error = true;
            drawError();
        }
    }

    private void drawMenu() {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.fillRect(0, 0, 240, 48, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(50, 13);
        TftTouchShield.print("CHALLENGES");
        drawButton(84, "DAILY PUZZLE", "THE featured move");
        drawButton(156, "FRESH PUZZLE", "A new public tactic");
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.setCursor(76, 260);
        TftTouchShield.print("BACK TO COVER");
    }

    private void drawButton(int y, String title, String subtitle) {
        TftTouchShield.fillRect(18, y, 204, 56, TftTouchShield.ORANGE);
        TftTouchShield.drawRect(18, y, 204, 56, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.ORANGE);
        TftTouchShield.setCursor(32, y + 10);
        TftTouchShield.print(title);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setCursor(32, y + 36);
        TftTouchShield.print(subtitle);
    }

    private void drawError() {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.RED, TftTouchShield.BLACK);
        TftTouchShield.setCursor(66, 84);
        TftTouchShield.print("OFFLINE");
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(24, 140);
        TftTouchShield.print(model.loadError() == LichessModel.LOAD_NETWORK_ERROR ? "CHECK WIFI" : "BAD PUZZLE DATA");
        TftTouchShield.setCursor(54, 256);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.print("TAP FOR COVER");
    }
}
