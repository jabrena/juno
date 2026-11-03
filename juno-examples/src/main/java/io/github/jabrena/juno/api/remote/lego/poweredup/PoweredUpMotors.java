package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;

import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.BUTTON_HEIGHT_MOTOR;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.MARGIN;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.PORTS;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.POWER_STEP;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.ROW_FIRST;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.ROW_PITCH;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.ROW_STOP;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.SMALL_BUTTON_WIDTH;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.WIDE_BUTTON_WIDTH;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.WIDTH;

/** The MOTORS view of {@link PoweredUpHubTFT}. */
final class PoweredUpMotors {
    private static final RemotePoweredUpHub HUB = new RemotePoweredUpHub();
    private PoweredUpMotors() {
    }

    static void onTouch(TftTouchShield tft, PoweredUpState s, int x, int y) {
        if (PoweredUpUi.inBox(x, y, WIDTH - MARGIN - WIDE_BUTTON_WIDTH, ROW_STOP, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            for (int port = 0; port < PORTS; port++) {
                s.zero[port] = HUB.readSensor(PoweredUpState.HUB_PORTS[port]);
            }
        } else if (PoweredUpUi.inBox(x, y, MARGIN, ROW_STOP, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            for (int port = 0; port < PORTS; port++) {
                if (PoweredUpUi.isMotor(s.device[port])) {
                    s.power[port] = 0;
                    HUB.getMotor(PoweredUpState.HUB_PORTS[port]).stop();
                }
            }
        } else {
            for (int port = 0; port < PORTS; port++) {
                int delta = PoweredUpUi.isMotor(s.device[port]) ? motorDelta(x, y, rowOf(port)) : 0;
                if (delta != 0) {
                    s.power[port] = PoweredUpUi.clamp(s.power[port] + delta);
                    HUB.getMotor(PoweredUpState.HUB_PORTS[port]).drive(s.power[port]);
                }
            }
        }
        drawRows(tft, s);
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
    static void drawButtons(TftTouchShield tft, PoweredUpState s) {
        PoweredUpUi.drawButton(tft, MARGIN, ROW_STOP, WIDE_BUTTON_WIDTH, TftTouchShield.RED, "STOP", MARGIN + 28);
        PoweredUpUi.drawButton(tft, WIDTH - MARGIN - WIDE_BUTTON_WIDTH, ROW_STOP, WIDE_BUTTON_WIDTH, TftTouchShield.GRAY,
                "ZERO", WIDTH - MARGIN - WIDE_BUTTON_WIDTH + 28);
        for (int port = 0; port < PORTS; port++) {
            if (PoweredUpUi.isMotor(s.device[port])) {
                PoweredUpUi.drawButton(tft, MARGIN, rowOf(port), SMALL_BUTTON_WIDTH, TftTouchShield.BLUE, "-", MARGIN + 20);
                PoweredUpUi.drawButton(tft, WIDTH - MARGIN - SMALL_BUTTON_WIDTH, rowOf(port), SMALL_BUTTON_WIDTH,
                        TftTouchShield.BLUE, "+", WIDTH - MARGIN - SMALL_BUTTON_WIDTH + 20);
            }
        }
    }

    static void drawRows(TftTouchShield tft, PoweredUpState s) {
        for (int port = 0; port < PORTS; port++) {
            drawPortRow(tft, s, port, rowOf(port));
        }
    }

    /**
     * One port: its letter and the detected device, then for a motor its power and tacho value (position
     * minus the zero offset) between the - and + buttons.
     */
    static void drawPortRow(TftTouchShield tft, PoweredUpState s, int port, int row) {
        int x = MARGIN + SMALL_BUTTON_WIDTH + 8;
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        if (!PoweredUpUi.isMotor(s.device[port])) {
            tft.setCursor(MARGIN, row + 8);
            tft.print("Port ");
            tft.print(PoweredUpUi.portLetter(PoweredUpState.HUB_PORTS[port]));
            tft.print("  ");
            PoweredUpUi.printDevice(tft, s.device[port]);
            return;
        }
        tft.setCursor(x, row);
        tft.print(PoweredUpUi.portLetter(PoweredUpState.HUB_PORTS[port]));
        tft.print(": ");
        PoweredUpUi.printDevice(tft, s.device[port]);
        tft.setTextSize(2);
        tft.setCursor(x, row + 10);
        tft.print(s.power[port]);
        tft.print("%   ");
        tft.setTextSize(1);
        tft.setCursor(x, row + 26);
        tft.print("tacho ");
        tft.print(HUB.readSensor(PoweredUpState.HUB_PORTS[port]) - s.zero[port]);
        tft.print("     ");
    }
}
