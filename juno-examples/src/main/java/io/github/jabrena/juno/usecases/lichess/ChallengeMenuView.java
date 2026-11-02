package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** The Lichess picker: daily puzzle, another public puzzle, or a live TV game. */
final class ChallengeMenuView implements View {

    private final TftTouchShield tft;
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

    ChallengeMenuView(TftTouchShield tft, Navigator navigator, LichessApi api, LichessModel model, TvModel tvModel) {
        this.tft = tft;
        this.navigator = navigator;
        this.api = api;
        this.model = model;
        this.tvModel = tvModel;
    }

    @Override
    public void enter() {
        error = false;
        drawMenu(tft);
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
            load(tft, true);
        } else if (y >= FRESH_Y && y < FRESH_Y + BUTTON_HEIGHT) {
            load(tft, false);
        } else if (y >= TV_Y && y < TV_Y + BUTTON_HEIGHT) {
            loadTv(tft);
        } else if (isBackTouch(x, y)) {
            navigator.show(LichessViews.COVER);
        }
    }

    private void load(TftTouchShield tft, boolean daily) {
        drawLoading(tft, daily ? "DOWNLOADING DAILY PUZZLE" : "DOWNLOADING FRESH PUZZLE");
        boolean loaded = daily ? api.loadDaily(model) : api.loadFresh(model);
        if (loaded) {
            navigator.show(LichessViews.CHALLENGE);
        } else {
            error = true;
            drawError(tft);
        }
    }

    private void loadTv(TftTouchShield tft) {
        drawLoading(tft, "CONNECTING TO LICHESS TV");
        if (api.loadTv(tvModel)) {
            navigator.show(LichessViews.TV);
        } else {
            error = true;
            drawError(tft);
        }
    }

    private void drawLoading(TftTouchShield tft, String detail) {
        tft.fillScreen(TftTouchShield.BLACK);
        for (int dot = 0; dot < DOT_X.length; dot++) {
            drawDot(tft, dot, DIM_DOT);
        }
        drawSpinner(tft);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.CYAN, TftTouchShield.BLACK);
        String title = "ASKING LICHESS...";
        tft.setCursor(centeredX(title, 2), 132);
        tft.print(title);
        tft.setTextSize(1);
        tft.setCursor(centeredX(detail, 1), 158);
        tft.print(detail);
    }

    private void drawSpinner(TftTouchShield tft) {
        for (int trail = TAIL_COLORS.length; trail >= 0; trail--) {
            int dot = (-trail + DOT_X.length) % DOT_X.length;
            drawDot(tft, dot, trail == TAIL_COLORS.length ? DIM_DOT : TAIL_COLORS[trail]);
        }
    }

    private void drawDot(TftTouchShield tft, int dot, int color) {
        tft.fillRect(SPINNER_X + DOT_X[dot] - DOT_SIZE / 2, SPINNER_Y + DOT_Y[dot] - DOT_SIZE / 2,
                DOT_SIZE, DOT_SIZE, color);
    }

    private void drawMenu(TftTouchShield tft) {
        tft.fillScreen(TftTouchShield.BLACK);
        tft.fillRect(0, 0, 240, 48, TftTouchShield.NAVY);
        tft.setTextSize(3);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        tft.setCursor(centeredX("CHALLENGES", 3), 13);
        tft.print("CHALLENGES");
        drawButton(tft, DAILY_Y, "DAILY PUZZLE", "THE featured move", TftTouchShield.ORANGE);
        drawButton(tft, FRESH_Y, "FRESH PUZZLE", "A new public tactic", TftTouchShield.ORANGE);
        drawButton(tft, TV_Y, "LIVE TV", "WATCH A LICHESS GAME", TftTouchShield.ORANGE);
        drawBackButton(tft);
    }

    private void drawButton(TftTouchShield tft, int y, String title, String subtitle, int background) {
        tft.fillRect(BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT, background);
        tft.drawRect(BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT, TftTouchShield.WHITE);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, background);
        tft.setCursor(BUTTON_X + 14, y + 10);
        tft.print(title);
        tft.setTextSize(1);
        tft.setCursor(BUTTON_X + 14, y + 36);
        tft.print(subtitle);
    }

    private void drawBackButton(TftTouchShield tft) {
        drawButton(tft, BACK_Y, "BACK TO COVER", "", TftTouchShield.GRAY);
    }

    private boolean isBackTouch(int x, int y) {
        return x >= BUTTON_X && x < BUTTON_X + BUTTON_WIDTH && y >= BACK_Y && y < BACK_Y + BUTTON_HEIGHT;
    }

    private static int centeredX(String text, int size) {
        return (WIDTH - text.length() * TftTouchShield.CHAR_WIDTH * size) / 2;
    }

    private void drawError(TftTouchShield tft) {
        tft.fillScreen(TftTouchShield.BLACK);
        tft.setTextSize(3);
        tft.setTextColor(TftTouchShield.RED, TftTouchShield.BLACK);
        tft.setCursor(66, 84);
        tft.print("OFFLINE");
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        tft.setCursor(24, 140);
        tft.print(model.loadError() == LichessModel.LOAD_NETWORK_ERROR ? "CHECK WIFI" : "BAD PUZZLE DATA");
        tft.setCursor(54, 256);
        tft.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        tft.print("TAP FOR COVER");
    }
}
