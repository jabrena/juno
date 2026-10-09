package io.github.jabrena.juno.api.lego;

import static io.github.jabrena.juno.api.lego.PoweredUpState.*;

import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** The PAIR view of {@link PoweredUpHubTFT}: the first two motors driven together. */
final class PoweredUpPair {
    private PoweredUpPair() {
    }

    static void onTouch(PoweredUpState s, int x, int y) {
        if (s.pairB < 0) {
            return;
        }
        if (PoweredUpUi.inBox(x, y, WIDTH - MARGIN - WIDE_BUTTON_WIDTH, PAIR_ROW_SYNC, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            toggleSync(s);
        } else if (PoweredUpUi.inBox(x, y, MARGIN, ROW_STOP, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            stop(s);
        } else if (PoweredUpUi.inBox(x, y, WIDTH - MARGIN - WIDE_BUTTON_WIDTH, ROW_STOP, WIDE_BUTTON_WIDTH, BUTTON_HEIGHT_MOTOR)) {
            s.zero[s.pairA] = PoweredUpHubRemote.readSensor(s.pairA);
            s.zero[s.pairB] = PoweredUpHubRemote.readSensor(s.pairB);
        } else if (y >= ROW_LEVELS && y < ROW_LEVELS + BUTTON_HEIGHT_MOTOR) {
            int level = (x - MARGIN) / (LEVEL_WIDTH + LEVEL_GAP);
            if (level >= STOP_COAST && level <= STOP_HOLD) {
                s.stopLevel = level;
            }
        } else {
            changePower(s, PoweredUpMotors.motorDelta(x, y, PAIR_ROW_A), PoweredUpMotors.motorDelta(x, y, PAIR_ROW_B));
        }
        draw(s);
    }

    /** With SYNC on, either row's button changes both motors with one linked command; otherwise each its own. */
    private static void changePower(PoweredUpState s, int deltaA, int deltaB) {
        if (s.pairSync && (deltaA != 0 || deltaB != 0)) {
            int shared = PoweredUpUi.clamp(s.power[s.pairA] + (deltaA != 0 ? deltaA : deltaB));
            s.power[s.pairA] = shared;
            s.power[s.pairB] = shared;
            PoweredUpHubRemote.setLinkedMotorPower(s.link, shared, shared);
            return;
        }
        if (deltaA != 0) {
            s.power[s.pairA] = PoweredUpUi.clamp(s.power[s.pairA] + deltaA);
            PoweredUpHubRemote.setMotorPower(s.pairA, s.power[s.pairA]);
        }
        if (deltaB != 0) {
            s.power[s.pairB] = PoweredUpUi.clamp(s.power[s.pairB] + deltaB);
            PoweredUpHubRemote.setMotorPower(s.pairB, s.power[s.pairB]);
        }
    }

    private static void toggleSync(PoweredUpState s) {
        if (s.pairSync) {
            dropSync(s);
            return;
        }
        s.link = PoweredUpHubRemote.linkMotors(s.pairA, s.pairB);
        s.pairSync = s.link >= 0;
        if (s.pairSync) {
            s.power[s.pairB] = s.power[s.pairA];
            PoweredUpHubRemote.setLinkedMotorPower(s.link, s.power[s.pairA], s.power[s.pairA]);
        } else {
            Serial.println("The hub did not link the motors");
        }
    }

    private static void dropSync(PoweredUpState s) {
        if (s.pairSync) {
            PoweredUpHubRemote.unlinkMotors(s.link);
        }
        s.pairSync = false;
        s.link = -1;
    }

    /** Stops both motors of the pair with the chosen level. */
    private static void stop(PoweredUpState s) {
        if (s.stopLevel == STOP_HOLD) {
            PoweredUpHubRemote.holdMotor(s.pairA);
            PoweredUpHubRemote.holdMotor(s.pairB);
        } else if (s.pairSync && s.stopLevel == STOP_BRAKE) {
            PoweredUpHubRemote.brakeLinkedMotors(s.link);
        } else if (s.stopLevel == STOP_BRAKE) {
            PoweredUpHubRemote.brakeMotor(s.pairA);
            PoweredUpHubRemote.brakeMotor(s.pairB);
        } else if (s.pairSync) {
            PoweredUpHubRemote.setLinkedMotorPower(s.link, 0, 0);
        } else {
            PoweredUpHubRemote.setMotorPower(s.pairA, 0);
            PoweredUpHubRemote.setMotorPower(s.pairB, 0);
        }
        s.power[s.pairA] = 0;
        s.power[s.pairB] = 0;
    }

    /** The pair is the two lowest ports with a motor; if it changes, a link on the old one is dropped. */
    static void update(PoweredUpState s) {
        int first = -1;
        int second = -1;
        for (int port = 0; port < PORTS; port++) {
            if (PoweredUpUi.isMotor(s.device[port])) {
                if (first < 0) {
                    first = port;
                } else if (second < 0) {
                    second = port;
                }
            }
        }
        if (first != s.pairA || second != s.pairB) {
            dropSync(s);
            s.pairA = first;
            s.pairB = second;
        }
    }

    /** The whole view: the SYNC switch, the two motors, the stop level choice and the STOP button. */
    static void draw(PoweredUpState s) {
        TftTouchShield.fillRect(0, CONTENT_Y, WIDTH, CONTENT_HEIGHT, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        if (s.pairB < 0) {
            TftTouchShield.setCursor(MARGIN, CONTENT_Y + 8);
            TftTouchShield.print("Plug in two motors");
            TftTouchShield.setCursor(MARGIN, CONTENT_Y + 34);
            TftTouchShield.print("to use a pair");
            return;
        }
        TftTouchShield.setCursor(MARGIN, PAIR_ROW_SYNC + 10);
        TftTouchShield.print(PoweredUpUi.portLetter(s.pairA));
        TftTouchShield.print(" + ");
        TftTouchShield.print(PoweredUpUi.portLetter(s.pairB));
        PoweredUpUi.drawButton(WIDTH - MARGIN - WIDE_BUTTON_WIDTH, PAIR_ROW_SYNC, WIDE_BUTTON_WIDTH,
                s.pairSync ? TftTouchShield.GREEN : TftTouchShield.GRAY, s.pairSync ? "SYNC ON" : "SYNC OFF",
                WIDTH - MARGIN - WIDE_BUTTON_WIDTH + (s.pairSync ? 14 : 8));
        drawPlusMinus(PAIR_ROW_A);
        drawPlusMinus(PAIR_ROW_B);
        drawLevel(s, STOP_COAST, "COAST");
        drawLevel(s, STOP_BRAKE, "BRAKE");
        drawLevel(s, STOP_HOLD, "HOLD");
        PoweredUpUi.drawButton(MARGIN, ROW_STOP, WIDE_BUTTON_WIDTH, TftTouchShield.RED, "STOP", MARGIN + 28);
        PoweredUpUi.drawButton(WIDTH - MARGIN - WIDE_BUTTON_WIDTH, ROW_STOP, WIDE_BUTTON_WIDTH, TftTouchShield.GRAY,
                "ZERO", WIDTH - MARGIN - WIDE_BUTTON_WIDTH + 28);
        drawRows(s);
    }

    private static void drawPlusMinus(int row) {
        PoweredUpUi.drawButton(MARGIN, row, SMALL_BUTTON_WIDTH, TftTouchShield.BLUE, "-", MARGIN + 20);
        PoweredUpUi.drawButton(WIDTH - MARGIN - SMALL_BUTTON_WIDTH, row, SMALL_BUTTON_WIDTH, TftTouchShield.BLUE, "+",
                WIDTH - MARGIN - SMALL_BUTTON_WIDTH + 20);
    }

    private static void drawLevel(PoweredUpState s, int level, String label) {
        PoweredUpUi.drawButton(MARGIN + level * (LEVEL_WIDTH + LEVEL_GAP), ROW_LEVELS, LEVEL_WIDTH,
                level == s.stopLevel ? TftTouchShield.BLUE : TftTouchShield.GRAY, label,
                MARGIN + level * (LEVEL_WIDTH + LEVEL_GAP) + (level == STOP_HOLD ? 12 : 6));
    }

    static void drawRows(PoweredUpState s) {
        PoweredUpMotors.drawPortRow(s, s.pairA, PAIR_ROW_A);
        PoweredUpMotors.drawPortRow(s, s.pairB, PAIR_ROW_B);
    }
}
