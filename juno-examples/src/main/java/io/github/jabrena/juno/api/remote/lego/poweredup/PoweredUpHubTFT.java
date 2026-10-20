package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpHubRemote;

import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.BOTTOM_HEIGHT;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.BOTTOM_Y;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.CONNECT_MILLIS;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.CONTENT_HEIGHT;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.CONTENT_Y;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.HEADER_HEIGHT;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.MARGIN;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.PORTS;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.REFRESH_MILLIS;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.TAB_HEIGHT;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.TAB_WIDTH;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.TAB_Y;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.TOUCH_LOCKOUT_MILLIS;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.VIEW_INFO;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.VIEW_LED;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.VIEW_MOTORS;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.VIEW_PAIR;
import static io.github.jabrena.juno.api.remote.lego.poweredup.PoweredUpState.WIDTH;

/**
 * A touch screen remote for a LEGO Powered Up hub (Technic Hub, Move Hub or City Hub), drawn on the
 * ELEGOO 2.8" TFT touch shield. The button along the bottom connects to / disconnects from the hub
 * (switch the hub on first; connecting blocks the screen for up to {@code CONNECT_MILLIS} while the
 * Arduino scans). Four tabs along the top choose the view:
 * <ul>
 *   <li><b>INFO</b>: hub type, battery level, signal strength (RSSI), firmware and hardware versions,
 *       and on a Technic Hub its tilt angles;</li>
 *   <li><b>LED</b>: a grid of nine colors, a touch sets the hub LED;</li>
 *   <li><b>MOTORS</b>: all four ports A..D, each with the device the hub reports on it (its automatic
 *       detection of what is plugged in: a motor type, a sensor or nothing). A port holding a motor has
 *       a {@code -} and a {@code +} button that change its power in steps of {@code POWER_STEP} percent
 *       and shows the tacho (position) value under it; a port with a sensor shows the sensor's name; an
 *       empty port stays blank. Below are a STOP button that brakes every motor and a ZERO button that
 *       sets the tacho values to 0 (an offset kept on the Arduino; the hub's own position is not
 *       changed).</li>
 *   <li><b>PAIR</b>: the first two motors the hub detected, driven as a pair. SYNC links them into one
 *       virtual port, so a single {@code -} / {@code +} sets both at once with one command; with SYNC off
 *       each motor has its own. Three stop levels can be chosen for the STOP button: COAST (power 0, the
 *       motors run free), BRAKE (they are slowed actively) and HOLD (they resist being turned). ZERO sets
 *       the two motors' tacho values to 0.</li>
 * </ul>
 * Pick the board with {@code -Djuno.board}.
 */
public class PoweredUpHubTFT {
    private static final PoweredUpHubRemote HUB = new PoweredUpHubRemote();
    private static final Clock CLOCK = MCU.getClock();

    private final PoweredUpState s = new PoweredUpState();

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        PoweredUpHubTFT app = new PoweredUpHubTFT();
        app.drawScreen();

        int lastTouch = CLOCK.millis();
        int lastRefresh = CLOCK.millis();
        while (true) {
            if (TftTouchShield.readTouch() && CLOCK.millis() - lastTouch > TOUCH_LOCKOUT_MILLIS) {
                lastTouch = CLOCK.millis();
                app.onTouch(TftTouchShield.touchX(), TftTouchShield.touchY());
            }
            if (CLOCK.millis() - lastRefresh > REFRESH_MILLIS) {
                lastRefresh = CLOCK.millis();
                app.checkLink();
                app.refresh();
            }
        }
    }

    private void onTouch(int x, int y) {
        if (PoweredUpUi.inBox(x, y, MARGIN, BOTTOM_Y, WIDTH - 2 * MARGIN, BOTTOM_HEIGHT)) {
            toggleConnection();
        } else if (y >= TAB_Y && y < TAB_Y + TAB_HEIGHT) {
            selectView(x / TAB_WIDTH);
        } else if (HUB.isConnected()) {
            if (s.view == VIEW_LED) {
                PoweredUpLed.onTouch(s, x, y);
            } else if (s.view == VIEW_MOTORS) {
                PoweredUpMotors.onTouch(s, x, y);
            } else if (s.view == VIEW_PAIR) {
                PoweredUpPair.onTouch(s, x, y);
            }
        }
    }

    private void selectView(int tab) {
        if (tab < 0 || tab > VIEW_PAIR || tab == s.view) {
            return;
        }
        s.view = tab;
        PoweredUpUi.drawTabs(s);
        drawContent();
    }

    private void toggleConnection() {
        if (HUB.isConnected()) {
            HUB.disconnect();
            Serial.println("Disconnected");
            linkLost();
        } else {
            PoweredUpUi.drawStatus("Scanning...", TftTouchShield.YELLOW);
            if (HUB.connect(CONNECT_MILLIS)) {
                onConnected();
            } else {
                Serial.println("No hub found");
                linkLost();
            }
        }
    }

    private void onConnected() {
        Serial.println("Connected");
        s.hubType = HUB.hubType();
        resetPorts();
        if (s.hubType == PoweredUpHubRemote.TYPE_TECHNIC_HUB) {
            HUB.enableSensor(PoweredUpHubRemote.PORT_TECHNIC_TILT, PoweredUpHubRemote.MODE_IMU_VALUES);
        }
        HUB.setLedColor(PoweredUpLed.LED_COLORS[s.ledIndex]);
        drawScreen();
    }

    /** If the hub went away on its own, show it the same way as a disconnect. */
    private void checkLink() {
        if (s.hubType != 0 && !HUB.isConnected()) {
            Serial.println("Link lost");
            linkLost();
        }
    }

    private void linkLost() {
        s.hubType = 0;
        resetPorts();
        drawScreen();
    }

    private void resetPorts() {
        for (int port = 0; port < PORTS; port++) {
            s.power[port] = 0;
            s.zero[port] = 0;
            s.device[port] = 0;
        }
        // The hub forgets its virtual ports with the connection.
        s.pairSync = false;
        s.link = -1;
        s.pairA = -1;
        s.pairB = -1;
    }

    /**
     * Asks the hub what it detected on each port. A motor found for the first time (or newly plugged in)
     * is subscribed to report its position; an unplugged one loses its power setting. Returns whether
     * anything changed, so the view can be redrawn from scratch.
     */
    private boolean detectDevices() {
        boolean changed = false;
        for (int port = 0; port < PORTS; port++) {
            int type = HUB.portDevice(port);
            if (type != s.device[port]) {
                s.device[port] = type;
                s.power[port] = 0;
                s.zero[port] = 0;
                if (PoweredUpUi.isMotor(type)) {
                    HUB.enableSensor(port, PoweredUpHubRemote.MODE_MOTOR_POSITION);
                }
                changed = true;
            }
        }
        if (changed) {
            PoweredUpPair.update(s);
        }
        return changed;
    }

    private void drawScreen() {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.fillRect(0, 0, WIDTH, HEADER_HEIGHT, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(MARGIN, 4);
        TftTouchShield.print("Powered Up");
        boolean connected = HUB.isConnected();
        PoweredUpUi.drawStatus(connected ? "Connected" : "Not connected",
                connected ? TftTouchShield.GREEN : TftTouchShield.RED);
        PoweredUpUi.drawTabs(s);
        PoweredUpUi.drawConnectButton(connected);
        drawContent();
    }

    /** Clears the area between the tabs and the connect button and draws the selected view. */
    private void drawContent() {
        TftTouchShield.fillRect(0, CONTENT_Y, WIDTH, CONTENT_HEIGHT, TftTouchShield.BLACK);
        if (!HUB.isConnected()) {
            TftTouchShield.setTextSize(2);
            TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
            TftTouchShield.setCursor(MARGIN, CONTENT_Y + 8);
            TftTouchShield.print("No hub connected");
        } else if (s.view == VIEW_INFO) {
            PoweredUpInfo.draw(s);
        } else if (s.view == VIEW_LED) {
            PoweredUpLed.draw(s);
        } else if (s.view == VIEW_MOTORS) {
            PoweredUpMotors.drawButtons(s);
            PoweredUpMotors.drawRows(s);
        } else {
            PoweredUpPair.draw(s);
        }
    }

    /** Redraws only what changes by itself: the info values or the tacho values. */
    private void refresh() {
        if (!HUB.isConnected()) {
            return;
        }
        boolean changed = detectDevices();
        if (s.view == VIEW_INFO) {
            PoweredUpInfo.draw(s);
        } else if ((s.view == VIEW_MOTORS || s.view == VIEW_PAIR) && changed) {
            drawContent();
        } else if (s.view == VIEW_MOTORS) {
            PoweredUpMotors.drawRows(s);
        } else if (s.view == VIEW_PAIR && s.pairB >= 0) {
            PoweredUpPair.drawRows(s);
        }
    }
}
