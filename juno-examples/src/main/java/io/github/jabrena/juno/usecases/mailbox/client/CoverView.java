package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.mvc.KeyboardView;
import io.github.jabrena.juno.util.mvc.Navigator;

/**
 * The opening screen: a letter flying across a night sky, with twinkling stars and a blinking "press any key" prompt.
 * Wi-Fi joins in the background meanwhile; any key opens the settings, where the mailbox is scanned for or typed.
 */
public final class CoverView implements KeyboardView {
    private static final int SETTINGS_VIEW = 0;
    private static final int WIDTH = 240;
    private static final int HEIGHT = 135;

    private static final int CREAM = 0xFFBA;
    private static final int FLAP = 0xF716;
    private static final int FOLD = 0xCDB1;
    private static final int SEAL = 0xD8A3;
    private static final int SPEED_LINE = 0x9E7F;
    private static final int STAR = 0xFFFF;
    private static final int DIM_STAR = 0x632C;

    private static final int ENVELOPE_LEFT = 78;
    private static final int ENVELOPE_TOP = 14;
    private static final int ENVELOPE_WIDTH = 84;
    private static final int ENVELOPE_HEIGHT = 52;
    private static final int FLAP_DEPTH = 32;
    private static final int SEAL_RADIUS = 6;

    private static final String TITLE = "JUNO MAILBOX";
    private static final int TITLE_Y = 78;
    private static final String SUBTITLE = "Java on the Cardputer";
    private static final int SUBTITLE_Y = 99;
    private static final String PROMPT = "PRESS ANY KEY TO CONTINUE";
    private static final int PROMPT_Y = 118;
    private static final int CHAR_WIDTH = 6;
    private static final int CHAR_HEIGHT = 8;

    private static final int BLINK_MILLIS = 500;
    private static final int TWINKLE_MILLIS = 150;
    private static final int[] STAR_X = {12, 34, 57, 190, 214, 228, 26, 200, 172, 8, 232, 30};
    private static final int[] STAR_Y = {8, 30, 12, 10, 34, 70, 64, 52, 22, 96, 110, 88};

    private final Navigator navigator;
    private final Display display;
    private final Clock clock;
    private boolean promptShown;
    private int nextBlink;
    private int nextTwinkle;
    private int twinkle;

    public CoverView(Navigator navigator, Display display) {
        this(navigator, display, ArduinoUno.getClock());
    }

    CoverView(Navigator navigator, Display display, Clock clock) {
        this.navigator = navigator;
        this.display = display;
        this.clock = clock;
    }

    @Override
    public void enter() {
        for (int y = 0; y < HEIGHT; y++) {
            display.fillRect(0, y, WIDTH, 1, sky(y));
        }
        for (int index = 0; index < STAR_X.length; index++) {
            display.drawPixel(STAR_X[index], STAR_Y[index], index % 3 == 0 ? STAR : DIM_STAR);
        }
        drawSpeedLines();
        drawEnvelope();
        drawCentered(TITLE, 2, TITLE_Y, Display.YELLOW);
        drawCentered(SUBTITLE, 1, SUBTITLE_Y, Display.CYAN);
        promptShown = false;
        nextBlink = clock.millis();
        nextTwinkle = nextBlink;
    }

    @Override
    public void render() {
        int now = clock.millis();
        if (now - nextBlink >= 0) {
            nextBlink = now + BLINK_MILLIS;
            promptShown = !promptShown;
            if (promptShown) {
                drawCentered(PROMPT, 1, PROMPT_Y, Display.WHITE);
            } else {
                for (int y = PROMPT_Y; y < PROMPT_Y + CHAR_HEIGHT; y++) {
                    display.fillRect(0, y, WIDTH, 1, sky(y));
                }
            }
        }
        if (now - nextTwinkle >= 0) {
            nextTwinkle = now + TWINKLE_MILLIS;
            // Each step brightens one star and dims the one before it, so the sparkle walks across the sky.
            int previous = twinkle;
            twinkle = (twinkle + 1) % STAR_X.length;
            display.drawPixel(STAR_X[previous], STAR_Y[previous], DIM_STAR);
            display.drawPixel(STAR_X[twinkle], STAR_Y[twinkle], STAR);
        }
    }

    @Override
    public void key(int key, boolean fnPressed) {
        navigator.show(SETTINGS_VIEW);
    }

    @Override
    public void touch(int x, int y) {
    }

    /** A vertical gradient from deep navy at the top to violet at the bottom, as an RGB565 color. */
    private int sky(int y) {
        int red = 1 + y * 9 / (HEIGHT - 1);
        int green = 2 + y * 2 / (HEIGHT - 1);
        int blue = 8 + y * 8 / (HEIGHT - 1);
        return (red << 11) | (green << 5) | blue;
    }

    private void drawSpeedLines() {
        display.fillRect(46, 26, 24, 2, SPEED_LINE);
        display.fillRect(36, 38, 34, 2, SPEED_LINE);
        display.fillRect(50, 50, 20, 2, SPEED_LINE);
    }

    private void drawEnvelope() {
        int right = ENVELOPE_LEFT + ENVELOPE_WIDTH - 1;
        int bottom = ENVELOPE_TOP + ENVELOPE_HEIGHT - 1;
        int centerX = ENVELOPE_LEFT + ENVELOPE_WIDTH / 2;
        display.fillRect(ENVELOPE_LEFT, ENVELOPE_TOP, ENVELOPE_WIDTH, ENVELOPE_HEIGHT, CREAM);
        drawLine(ENVELOPE_LEFT, bottom, centerX - 6, ENVELOPE_TOP + 30, FOLD);
        drawLine(right, bottom, centerX + 6, ENVELOPE_TOP + 30, FOLD);
        int halfWidth = ENVELOPE_WIDTH / 2;
        for (int row = 0; row <= FLAP_DEPTH; row++) {
            int half = halfWidth - row * halfWidth / FLAP_DEPTH;
            display.fillRect(centerX - half, ENVELOPE_TOP + row, 2 * half, 1, FLAP);
        }
        drawLine(ENVELOPE_LEFT, ENVELOPE_TOP, centerX, ENVELOPE_TOP + FLAP_DEPTH, FOLD);
        drawLine(right, ENVELOPE_TOP, centerX, ENVELOPE_TOP + FLAP_DEPTH, FOLD);
        int sealY = ENVELOPE_TOP + FLAP_DEPTH;
        for (int dy = -SEAL_RADIUS; dy <= SEAL_RADIUS; dy++) {
            int half = 0;
            while ((half + 1) * (half + 1) + dy * dy <= SEAL_RADIUS * SEAL_RADIUS) {
                half = half + 1;
            }
            display.fillRect(centerX - half, sealY + dy, 2 * half + 1, 1, SEAL);
        }
    }

    private void drawLine(int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int step = 0; step <= steps; step++) {
            display.drawPixel(x0 + (x1 - x0) * step / steps, y0 + (y1 - y0) * step / steps, color);
        }
    }

    /** Prints {@code text} centered at text size {@code size}, over the sky color of its middle row. */
    private void drawCentered(String text, int size, int y, int color) {
        display.setTextSize(size);
        display.setTextColor(color, sky(y + size * CHAR_HEIGHT / 2));
        display.setCursor((WIDTH - text.length() * size * CHAR_WIDTH) / 2, y);
        display.print(text);
    }
}
