package io.github.jabrena.juno.api.lego;

import static io.github.jabrena.juno.api.lego.PoweredUpState.*;

import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Small drawing and lookup helpers shared by the views of {@link PoweredUpHubTFT}. */
final class PoweredUpUi {
    private PoweredUpUi() {
    }

    static boolean isMotor(int type) {
        return type == PoweredUpHubRemote.DEVICE_MEDIUM_LINEAR_MOTOR
                || type == PoweredUpHubRemote.DEVICE_MOVE_HUB_MEDIUM_LINEAR_MOTOR
                || type == PoweredUpHubRemote.DEVICE_MEDIUM_ANGULAR_MOTOR
                || type == PoweredUpHubRemote.DEVICE_LARGE_ANGULAR_MOTOR
                || type == PoweredUpHubRemote.DEVICE_TECHNIC_LARGE_MOTOR
                || type == PoweredUpHubRemote.DEVICE_TECHNIC_XL_MOTOR
                || type == PoweredUpHubRemote.DEVICE_TECHNIC_MEDIUM_ANGULAR_MOTOR
                || type == PoweredUpHubRemote.DEVICE_TECHNIC_LARGE_ANGULAR_MOTOR;
    }

    static int clamp(int power) {
        if (power > 100) {
            return 100;
        }
        if (power < -100) {
            return -100;
        }
        return power;
    }

    static boolean inBox(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    static void drawStatus(String text, int color) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(color, TftTouchShield.NAVY);
        TftTouchShield.setCursor(WIDTH - 90, 8);
        TftTouchShield.print("             ");
        TftTouchShield.setCursor(WIDTH - 90, 8);
        TftTouchShield.print(text);
    }

    static void drawTabs(PoweredUpState s) {
        drawTab(s, VIEW_INFO, "INFO");
        drawTab(s, VIEW_LED, "LED");
        drawTab(s, VIEW_MOTORS, "MOTORS");
        drawTab(s, VIEW_PAIR, "PAIR");
    }

    private static void drawTab(PoweredUpState s, int tab, String label) {
        int color = tab == s.view ? TftTouchShield.BLUE : TftTouchShield.GRAY;
        TftTouchShield.fillRect(tab * TAB_WIDTH, TAB_Y, TAB_WIDTH, TAB_HEIGHT, color);
        TftTouchShield.drawRect(tab * TAB_WIDTH, TAB_Y, TAB_WIDTH, TAB_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(tab * TAB_WIDTH + 6, TAB_Y + 12);
        TftTouchShield.print(label);
    }

    static void drawConnectButton(boolean connected) {
        int color = connected ? TftTouchShield.RED : TftTouchShield.GREEN;
        TftTouchShield.fillRect(MARGIN, BOTTOM_Y, WIDTH - 2 * MARGIN, BOTTOM_HEIGHT, color);
        TftTouchShield.drawRect(MARGIN, BOTTOM_Y, WIDTH - 2 * MARGIN, BOTTOM_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(WIDTH / 2 - (connected ? 60 : 42), BOTTOM_Y + 8);
        TftTouchShield.print(connected ? "DISCONNECT" : "CONNECT");
    }

    static void drawButton(int x, int y, int width, int color, String label, int textX) {
        TftTouchShield.fillRect(x, y, width, BUTTON_HEIGHT_MOTOR, color);
        TftTouchShield.drawRect(x, y, width, BUTTON_HEIGHT_MOTOR, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(textX, y + 9);
        TftTouchShield.print(label);
    }

    static String portLetter(int port) {
        if (port == PoweredUpHubRemote.PORT_A) {
            return "A";
        }
        if (port == PoweredUpHubRemote.PORT_B) {
            return "B";
        }
        if (port == PoweredUpHubRemote.PORT_C) {
            return "C";
        }
        return "D";
    }

    /**
     * The device's name, short enough (at most 18 characters) to end before the + button of a motor row.
     * It is not padded: a changed device redraws the whole view first, so no older text is left behind.
     */
    static void printDevice(int type) {
        if (type == PoweredUpHubRemote.DEVICE_NONE) {
            TftTouchShield.print("-- empty --");
        } else if (isMotor(type)) {
            printMotorName(type);
        } else if (type == PoweredUpHubRemote.DEVICE_COLOR_DISTANCE_SENSOR) {
            TftTouchShield.print("Color/dist. sensor");
        } else if (type == PoweredUpHubRemote.DEVICE_MOTION_SENSOR) {
            TftTouchShield.print("Motion sensor");
        } else {
            TftTouchShield.print("Device 0x");
            TftTouchShield.print(type);
        }
    }

    private static void printMotorName(int type) {
        if (type == PoweredUpHubRemote.DEVICE_TECHNIC_XL_MOTOR) {
            TftTouchShield.print("Technic XL motor");
        } else if (type == PoweredUpHubRemote.DEVICE_TECHNIC_LARGE_MOTOR
                || type == PoweredUpHubRemote.DEVICE_LARGE_ANGULAR_MOTOR
                || type == PoweredUpHubRemote.DEVICE_TECHNIC_LARGE_ANGULAR_MOTOR) {
            TftTouchShield.print("Large motor");
        } else if (type == PoweredUpHubRemote.DEVICE_MEDIUM_ANGULAR_MOTOR
                || type == PoweredUpHubRemote.DEVICE_TECHNIC_MEDIUM_ANGULAR_MOTOR) {
            TftTouchShield.print("Medium ang. motor");
        } else {
            TftTouchShield.print("Medium motor");
        }
    }
}
