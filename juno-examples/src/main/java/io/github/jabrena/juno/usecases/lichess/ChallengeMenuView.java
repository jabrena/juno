package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** The Lichess picker: daily puzzle, another public puzzle, or a live TV game. */
final class ChallengeMenuView implements View {
    private static final int WIDTH = 240;
    private static final int BUTTON_X = 18;
    private static final int BUTTON_WIDTH = 204;
    private static final int BUTTON_HEIGHT = 56;
    private static final int DAILY_Y = 56;
    private static final int FRESH_Y = 120;
    private static final int TV_Y = 184;
    private static final int BACK_Y = 248;
    private static final int SPINNER_X = 120;
    private static final int SPINNER_Y = 98;
    private static final int DOT_SIZE = 5;
    private static final int[] DOT_X = {0, 10, 14, 10, 0, -10, -14, -10};
    private static final int[] DOT_Y = {-14, -10, 0, 10, 14, 10, 0, -10};
    private static final int[] TAIL_COLORS = {TftTouchShield.WHITE, 0xAD55, 0x632C};
    private static final int DIM_DOT = 0x2104;
    private final Navigator navigator;
    private final LichessApi api;
    private final LichessModel model;
    private final TvModel tvModel;
    private boolean error;

    ChallengeMenuView(Navigator navigator, LichessApi api, LichessModel model, TvModel tvModel) {
        this.navigator = navigator;
        this.api = api;
        this.model = model;
        this.tvModel = tvModel;
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
        if (y >= DAILY_Y && y < DAILY_Y + BUTTON_HEIGHT) {
            load(true);
        } else if (y >= FRESH_Y && y < FRESH_Y + BUTTON_HEIGHT) {
            load(false);
        } else if (y >= TV_Y && y < TV_Y + BUTTON_HEIGHT) {
            loadTv();
        } else if (isBackTouch(x, y)) {
            navigator.show(LichessViews.COVER);
        }
    }

    private void load(boolean daily) {
        drawLoading(daily ? "DOWNLOADING DAILY PUZZLE" : "DOWNLOADING FRESH PUZZLE");
        boolean loaded = daily ? api.loadDaily(model) : api.loadFresh(model);
        if (loaded) {
            navigator.show(LichessViews.CHALLENGE);
        } else {
            error = true;
            drawError();
        }
    }

    private void loadTv() {
        drawLoading("CONNECTING TO LICHESS TV");
        if (api.loadTv(tvModel)) {
            navigator.show(LichessViews.TV);
        } else {
            error = true;
            drawError();
        }
    }

    private void drawLoading(String detail) {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        for (int dot = 0; dot < DOT_X.length; dot++) {
            drawDot(dot, DIM_DOT);
        }
        drawSpinner();
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, TftTouchShield.BLACK);
        String title = "ASKING LICHESS...";
        TftTouchShield.setCursor(centeredX(title, 2), 132);
        TftTouchShield.print(title);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setCursor(centeredX(detail, 1), 158);
        TftTouchShield.print(detail);
    }

    private void drawSpinner() {
        for (int trail = TAIL_COLORS.length; trail >= 0; trail--) {
            int dot = (-trail + DOT_X.length) % DOT_X.length;
            drawDot(dot, trail == TAIL_COLORS.length ? DIM_DOT : TAIL_COLORS[trail]);
        }
    }

    private void drawDot(int dot, int color) {
        TftTouchShield.fillRect(SPINNER_X + DOT_X[dot] - DOT_SIZE / 2, SPINNER_Y + DOT_Y[dot] - DOT_SIZE / 2,
                DOT_SIZE, DOT_SIZE, color);
    }

    private void drawMenu() {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.fillRect(0, 0, 240, 48, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(centeredX("CHALLENGES", 3), 13);
        TftTouchShield.print("CHALLENGES");
        drawButton(DAILY_Y, "DAILY PUZZLE", "THE featured move", TftTouchShield.ORANGE);
        drawButton(FRESH_Y, "FRESH PUZZLE", "A new public tactic", TftTouchShield.ORANGE);
        drawButton(TV_Y, "LIVE TV", "WATCH A LICHESS GAME", TftTouchShield.ORANGE);
        drawBackButton();
    }

    private void drawButton(int y, String title, String subtitle, int background) {
        TftTouchShield.fillRect(BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT, background);
        TftTouchShield.drawRect(BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, background);
        TftTouchShield.setCursor(BUTTON_X + 14, y + 10);
        TftTouchShield.print(title);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setCursor(BUTTON_X + 14, y + 36);
        TftTouchShield.print(subtitle);
    }

    private void drawBackButton() {
        drawButton(BACK_Y, "BACK TO COVER", "", TftTouchShield.GRAY);
    }

    private boolean isBackTouch(int x, int y) {
        return x >= BUTTON_X && x < BUTTON_X + BUTTON_WIDTH && y >= BACK_Y && y < BACK_Y + BUTTON_HEIGHT;
    }

    private static int centeredX(String text, int size) {
        return (WIDTH - text.length() * TftTouchShield.CHAR_WIDTH * size) / 2;
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
