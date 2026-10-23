package io.github.jabrena.juno.usecases.zelda.common.ux;

import io.github.jabrena.juno.hardware.display.Display;

/** Shared Zelda screen footer. */
public final class Footer {
    private static final int TOP = 107;
    private static final int HEIGHT = 28;

    private final Display display;

    public Footer(Display display) {
        this.display = display;
    }

    public void draw(String legend) {
        display.setTextSize(1);
        display.fillRect(0, TOP, 240, HEIGHT, Display.BLUE);
        display.fillRect(0, TOP, 240, 2, Display.YELLOW);
        display.setTextColor(Display.WHITE, Display.BLUE);
        display.setCursor(4, 117);
        display.println(legend);
    }
}
