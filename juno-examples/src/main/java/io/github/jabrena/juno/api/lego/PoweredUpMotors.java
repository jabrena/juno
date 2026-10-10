package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.tft.TftTouchShield;

import static io.github.jabrena.juno.api.lego.PoweredUpState.*;

/** The MOTORS view of {@link PoweredUpHubTFT}. */
final class PoweredUpMotors {
    private PoweredUpMotors() {
    }

    static void onTouch(PoweredUpState s, int x, int y) {
        if (PoweredUpUi.inBox(x, y, WIDTH - MARGIN - WIDE_BUTTON_WIDTH, ROW_STOP, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            for (int port = 0; port < PORTS; port++) {
                s.zero[port] = PoweredUpHubRemote.readSensor(port);
            }
        } else if (PoweredUpUi.inBox(x, y, MARGIN, ROW_STOP, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            for (int port = 0; port < PORTS; port++) {
                if (PoweredUpUi.isMotor(s.device[port])) {
                    s.power[port] = 0;
                    PoweredUpHubRemote.brakeMotor(port);
                }
            }
        } else {
            for (int port = 0; port < PORTS; port++) {
                int delta = PoweredUpUi.isMotor(s.device[port]) ? motorDelta(x, y, rowOf(port)) : 0;
                if (delta != 0) {
                    s.power[port] = PoweredUpUi.clamp(s.power[port] + delta);
                    PoweredUpHubRemote.setMotorPower(port, s.power[port]);
                }
            }
        }
        drawRows(s);
    }

    private static int rowOf(int port) {
        return ROW_FIRST + port * ROW_PITCH;
    }

    /** {@code -POWER_STEP}, {@code +POWER_STEP} or 0 depending on which button of the row was hit. */
    static int motorDelta(int x, int y, int row) {
        if (PoweredUpUi.inBox(x, y, MARGIN, row, SMALL_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            return -POWER_STEP;
        }
        if (PoweredUpUi.inBox(x, y, WIDTH - MARGIN - SMALL_BUTTON_WIDTH, row, SMALL_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            return POWER_STEP;
        }
        return 0;
    }

    /** The buttons that stay the same until a device changes: STOP, ZERO and the - / + of each motor. */
    static void drawButtons(PoweredUpState s) {
        PoweredUpUi.drawButton(MARGIN, ROW_STOP, WIDE_BUTTON_WIDTH, TftTouchShield.RED, "STOP", MARGIN + 28);
        PoweredUpUi.drawButton(WIDTH - MARGIN - WIDE_BUTTON_WIDTH, ROW_STOP, WIDE_BUTTON_WIDTH, TftTouchShield.GRAY,
                "ZERO", WIDTH - MARGIN - WIDE_BUTTON_WIDTH + 28);
        for (int port = 0; port < PORTS; port++) {
            if (PoweredUpUi.isMotor(s.device[port])) {
                PoweredUpUi.drawButton(MARGIN, rowOf(port), SMALL_BUTTON_WIDTH, TftTouchShield.BLUE, "-", MARGIN + 20);
                PoweredUpUi.drawButton(WIDTH - MARGIN - SMALL_BUTTON_WIDTH, rowOf(port), SMALL_BUTTON_WIDTH,
                        TftTouchShield.BLUE, "+", WIDTH - MARGIN - SMALL_BUTTON_WIDTH + 20);
            }
        }
    }

    static void drawRows(PoweredUpState s) {
        for (int port = 0; port < PORTS; port++) {
            drawPortRow(s, port, rowOf(port));
        }
    }

    /**
     * One port: its letter and the detected device, then for a motor its power and tacho value (position
     * minus the zero offset) between the - and + buttons.
     */
    static void drawPortRow(PoweredUpState s, int port, int row) {
        int x = MARGIN + SMALL_BUTTON_WIDTH + 8;
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        if (!PoweredUpUi.isMotor(s.device[port])) {
            TftTouchShield.setCursor(MARGIN, row + 8);
            TftTouchShield.print("Port ");
            TftTouchShield.print(PoweredUpUi.portLetter(port));
            TftTouchShield.print("  ");
            PoweredUpUi.printDevice(s.device[port]);
            return;
        }
        TftTouchShield.setCursor(x, row);
        TftTouchShield.print(PoweredUpUi.portLetter(port));
        TftTouchShield.print(": ");
        PoweredUpUi.printDevice(s.device[port]);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(x, row + 10);
        TftTouchShield.print(s.power[port]);
        TftTouchShield.print("%   ");
        TftTouchShield.setTextSize(1);
        TftTouchShield.setCursor(x, row + 26);
        TftTouchShield.print("tacho ");
        TftTouchShield.print(PoweredUpHubRemote.readSensor(port) - s.zero[port]);
        TftTouchShield.print("     ");
    }
}
