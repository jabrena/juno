package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.remote.lego.poweredup.HubPort;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;

import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.BOTTOM_HEIGHT;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.BOTTOM_Y;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.BUTTON_HEIGHT_MOTOR;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.MARGIN;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.TAB_HEIGHT;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.TAB_WIDTH;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.TAB_Y;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.VIEW_INFO;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.VIEW_LED;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.VIEW_MOTORS;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.VIEW_PAIR;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.WIDTH;

/** Small drawing and lookup helpers shared by the views of {@link PoweredUpHubTFT}. */
final class PoweredUpUi {
    private PoweredUpUi() {
    }

    static boolean isMotor(int type) {
        return type == RemotePoweredUpHub.DEVICE_MEDIUM_LINEAR_MOTOR
                || type == RemotePoweredUpHub.DEVICE_MOVE_HUB_MEDIUM_LINEAR_MOTOR
                || type == RemotePoweredUpHub.DEVICE_MEDIUM_ANGULAR_MOTOR
                || type == RemotePoweredUpHub.DEVICE_LARGE_ANGULAR_MOTOR
                || type == RemotePoweredUpHub.DEVICE_TECHNIC_LARGE_MOTOR
                || type == RemotePoweredUpHub.DEVICE_TECHNIC_XL_MOTOR
                || type == RemotePoweredUpHub.DEVICE_TECHNIC_MEDIUM_ANGULAR_MOTOR
                || type == RemotePoweredUpHub.DEVICE_TECHNIC_LARGE_ANGULAR_MOTOR;
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

    static void drawStatus(TftTouchShield tft, String text, int color) {
        tft.setTextSize(1);
        tft.setTextColor(color, TftTouchShield.NAVY);
        tft.setCursor(WIDTH - 90, 8);
        tft.print("             ");
        tft.setCursor(WIDTH - 90, 8);
        tft.print(text);
    }

    static void drawTabs(TftTouchShield tft, PoweredUpState s) {
        drawTab(tft, s, VIEW_INFO, "INFO");
        drawTab(tft, s, VIEW_LED, "LED");
        drawTab(tft, s, VIEW_MOTORS, "MOTORS");
        drawTab(tft, s, VIEW_PAIR, "PAIR");
    }

    private static void drawTab(TftTouchShield tft, PoweredUpState s, int tab, String label) {
        int color = tab == s.view ? TftTouchShield.BLUE : TftTouchShield.GRAY;
        tft.fillRect(tab * TAB_WIDTH, TAB_Y, TAB_WIDTH, TAB_HEIGHT, color);
        tft.drawRect(tab * TAB_WIDTH, TAB_Y, TAB_WIDTH, TAB_HEIGHT, TftTouchShield.WHITE);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, color);
        tft.setCursor(tab * TAB_WIDTH + 6, TAB_Y + 12);
        tft.print(label);
    }

    static void drawConnectButton(TftTouchShield tft, boolean connected) {
        int color = connected ? TftTouchShield.RED : TftTouchShield.GREEN;
        tft.fillRect(MARGIN, BOTTOM_Y, WIDTH - 2 * MARGIN, BOTTOM_HEIGHT, color);
        tft.drawRect(MARGIN, BOTTOM_Y, WIDTH - 2 * MARGIN, BOTTOM_HEIGHT, TftTouchShield.WHITE);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, color);
        tft.setCursor(WIDTH / 2 - (connected ? 60 : 42), BOTTOM_Y + 8);
        tft.print(connected ? "DISCONNECT" : "CONNECT");
    }

    static void drawButton(TftTouchShield tft, int x, int y, int width, int color, String label, int textX) {
        tft.fillRect(x, y, width, BUTTON_HEIGHT_MOTOR, color);
        tft.drawRect(x, y, width, BUTTON_HEIGHT_MOTOR, TftTouchShield.WHITE);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, color);
        tft.setCursor(textX, y + 9);
        tft.print(label);
    }

    static String portLetter(HubPort port) {
        if (port == HubPort.A) {
            return "A";
        }
        if (port == HubPort.B) {
            return "B";
        }
        if (port == HubPort.C) {
            return "C";
        }
        return "D";
    }

    /**
     * The device's name, short enough (at most 18 characters) to end before the + button of a motor row.
     * It is not padded: a changed device redraws the whole view first, so no older text is left behind.
     */
    static void printDevice(TftTouchShield tft, int type) {
        if (type == RemotePoweredUpHub.DEVICE_NONE) {
            tft.print("-- empty --");
        } else if (isMotor(type)) {
            printMotorName(tft, type);
        } else if (type == RemotePoweredUpHub.DEVICE_COLOR_DISTANCE_SENSOR) {
            tft.print("Color/dist. sensor");
        } else if (type == RemotePoweredUpHub.DEVICE_MOTION_SENSOR) {
            tft.print("Motion sensor");
        } else {
            tft.print("Device 0x");
            tft.print(type);
        }
    }

    private static void printMotorName(TftTouchShield tft, int type) {
        if (type == RemotePoweredUpHub.DEVICE_TECHNIC_XL_MOTOR) {
            tft.print("Technic XL motor");
        } else if (type == RemotePoweredUpHub.DEVICE_TECHNIC_LARGE_MOTOR
                || type == RemotePoweredUpHub.DEVICE_LARGE_ANGULAR_MOTOR
                || type == RemotePoweredUpHub.DEVICE_TECHNIC_LARGE_ANGULAR_MOTOR) {
            tft.print("Large motor");
        } else if (type == RemotePoweredUpHub.DEVICE_MEDIUM_ANGULAR_MOTOR
                || type == RemotePoweredUpHub.DEVICE_TECHNIC_MEDIUM_ANGULAR_MOTOR) {
            tft.print("Medium ang. motor");
        } else {
            tft.print("Medium motor");
        }
    }
}
