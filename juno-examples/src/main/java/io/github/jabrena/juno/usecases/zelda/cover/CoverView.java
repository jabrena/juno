package io.github.jabrena.juno.usecases.zelda.cover;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.usecases.zelda.KeyboardView;
import io.github.jabrena.juno.util.mvc.Navigator;

/** Zelda fan cover screen with a slowly twinkling star field and a centered Triforce. */
public final class CoverView implements KeyboardView {
    private static final int SELECTOR_VIEW = 1;
    private static final int STAR_FRAME_PERIOD = 18;

    private final Navigator navigator;
    private final Display display;
    private int starFrameCounter;
    private int starPhase;

    public CoverView(Navigator navigator, Display display) {
        this.navigator = navigator;
        this.display = display;
    }

    @Override
    public void enter() {
        display.fillScreen(Display.BLACK);
        starFrameCounter = 0;
        starPhase = 1;
        drawStars(starPhase);
        drawTriforce(120, 12, 36);
        display.setTextSize(1);
        display.setTextColor(Display.WHITE, Display.BLACK);
        display.setCursor(60, 106);
        display.println("PRESS ENTER TO BEGIN");
    }

    @Override
    public void render() {
        starFrameCounter = starFrameCounter + 1;
        if (starFrameCounter < STAR_FRAME_PERIOD) {
            return;
        }
        starFrameCounter = 0;
        starPhase = (starPhase + 1) & 3;
        drawStars(starPhase);
    }

    @Override
    public void key(int key, boolean fnPressed) {
        if (key == Keyboard.ENTER && !fnPressed) {
            navigator.show(SELECTOR_VIEW);
        }
    }

    @Override
    public void touch(int x, int y) {
    }

    private void drawStars(int phase) {
        display.drawPixel(18, 18, starColor(0, phase));
        display.drawPixel(42, 31, starColor(1, phase));
        display.drawPixel(78, 12, starColor(2, phase));
        display.drawPixel(171, 28, starColor(3, phase));
        display.drawPixel(225, 43, starColor(4, phase));
        display.drawPixel(20, 88, starColor(5, phase));
        display.drawPixel(218, 92, starColor(6, phase));
    }

    private int starColor(int index, int phase) {
        int twinkle = (index + phase) & 3;
        if (twinkle == 0) {
            return Display.BLACK;
        }
        if (twinkle == 2) {
            return Display.WHITE;
        }
        return Display.CYAN;
    }

    private void drawTriforce(int x, int y, int size) {
        drawTriangle(x, y, size, Display.YELLOW);
        drawTriangle(x - size, y + size, size, Display.YELLOW);
        drawTriangle(x + size, y + size, size, Display.YELLOW);
    }

    private void drawTriangle(int centerX, int topY, int height, int color) {
        for (int row = 0; row < height; row++) {
            display.fillRect(centerX - row, topY + row, row * 2 + 1, 1, color);
        }
    }

}
