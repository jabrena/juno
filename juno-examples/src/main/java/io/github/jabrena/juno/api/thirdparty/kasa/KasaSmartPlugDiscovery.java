package io.github.jabrena.juno.api.thirdparty.kasa;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.api.time.Clock;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Finds legacy TP-Link Kasa HS100/HS110 plugs on the local network and controls them from an ELEGOO TFT shield.
 *
 * <p>Set {@code JUNO_WIFI_SSID} and {@code JUNO_WIFI_PASSWORD} in the build environment. The display stays live
 * while the board joins Wi-Fi or reopens discovery, and its three fixed buttons toggle the first three plugs found.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class KasaSmartPlugDiscovery {
    private static final int DISCOVERY_INTERVAL_MILLIS = 3000;
    private static final int WIFI_POLL_MILLIS = 500;
    private static final int LISTEN_RETRY_MILLIS = 1000;
    private static final int MAX_PLUGS = 3;
    private static final int FIRST_BUTTON_Y = 46;
    private static final int BUTTON_HEIGHT = 58;
    private static final int BUTTON_MARGIN = 8;

    private KasaSmartPlugDiscovery() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(500);
        Serial.println("Kasa discovery");
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE_FLIPPED);
        new Controller().run();
    }

    /** Fixed startup allocations and an allocation-free state machine, following Miner2's board-side service model. */
    private static final class Controller {
        private static final int JOINING = 0;
        private static final int LISTENING = 1;
        private static final int READY = 2;

        private final byte[] request = new byte[KasaSmartPlug.REQUEST_BUFFER_SIZE];
        private final byte[] response = new byte[KasaSmartPlug.DEFAULT_RESPONSE_BUFFER_SIZE];
        private final int[] endpoint = new int[Udp.ENDPOINT_SIZE];
        private final int[] plugEndpoints = new int[MAX_PLUGS * Udp.ENDPOINT_SIZE];
        private final int[] selectedEndpoint = new int[Udp.ENDPOINT_SIZE];
        private final int[] models = new int[MAX_PLUGS];
        private final int[] relayStates = new int[MAX_PLUGS];
        private int plugCount;
        private int state;
        private int nextPoll;
        private int nextDiscovery;

        void run() {
            startWifi(Clock.millis());
            while (true) {
                int now = Clock.millis();
                stepLink(now);
                if (state == READY) {
                    discover(now);
                    handleTouch();
                }
                Delay.millis(10);
            }
        }

        private void startWifi(int now) {
            Udp.stop();
            showStatus("Connecting WiFi...");
            Serial.println("Kasa: connecting WiFi");
            Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
            state = JOINING;
            nextPoll = now;
        }

        private void stepLink(int now) {
            if (now - nextPoll < 0) {
                return;
            }
            nextPoll = now + WIFI_POLL_MILLIS;
            if (state == JOINING) {
                if (Wifi.status() == Wifi.STATUS_CONNECTED) {
                    state = LISTENING;
                    nextPoll = now;
                    showStatus("Opening discovery...");
                    Serial.println("Kasa: WiFi connected");
                }
                return;
            }
            if (state == LISTENING) {
                if (KasaSmartPlug.openDiscovery()) {
                    state = READY;
                    nextDiscovery = now;
                    renderPlugs();
                    Serial.println("Kasa discovery ready");
                } else {
                    nextPoll = now + LISTEN_RETRY_MILLIS;
                    showStatus("Retrying discovery...");
                }
                return;
            }
            if (Wifi.status() != Wifi.STATUS_CONNECTED) {
                startWifi(now);
            }
        }

        private void discover(int now) {
            if (now - nextDiscovery >= 0) {
                KasaSmartPlug.broadcastDiscovery(request, request.length);
                nextDiscovery = now + DISCOVERY_INTERVAL_MILLIS;
            }
            int discoveryLength = KasaSmartPlug.receiveDiscovery(response, response.length, endpoint);
            if (discoveryLength <= 0) {
                return;
            }
            int model = KasaSmartPlug.model(response, discoveryLength);
            if (model == KasaSmartPlug.MODEL_UNKNOWN) {
                return;
            }
            int slot = indexOf(endpoint);
            int relayState = KasaSmartPlug.relayState(response, discoveryLength);
            if (slot < 0 && plugCount < MAX_PLUGS) {
                copyAddress(endpoint, plugEndpoints, plugCount);
                models[plugCount] = model;
                relayStates[plugCount] = relayState;
                plugCount++;
                renderPlugs();
            } else if (slot >= 0 && relayState != relayStates[slot]) {
                relayStates[slot] = relayState;
                drawButton(slot);
            }
        }

        private void handleTouch() {
            if (!TftTouchShield.readTouch()) {
                return;
            }
            int slot = buttonAt(TftTouchShield.touchX(), TftTouchShield.touchY());
            if (slot >= 0) {
                drawButtonBusy(slot);
                copyAddress(plugEndpoints, slot, selectedEndpoint);
                toggle(selectedEndpoint, slot);
                drawButton(slot);
            }
            while (TftTouchShield.readTouch()) {
                Delay.millis(25);
            }
        }

        private int indexOf(int[] candidate) {
            for (int slot = 0; slot < plugCount; slot++) {
                int offset = slot * Udp.ENDPOINT_SIZE;
                if (plugEndpoints[offset] == candidate[0] && plugEndpoints[offset + 1] == candidate[1]
                        && plugEndpoints[offset + 2] == candidate[2] && plugEndpoints[offset + 3] == candidate[3]) {
                    return slot;
                }
            }
            return -1;
        }

        private void renderPlugs() {
            showStatus("Scanning local network");
            for (int slot = 0; slot < plugCount; slot++) {
                drawButton(slot);
            }
        }

        private void drawButton(int slot) {
            int y = FIRST_BUTTON_Y + slot * (BUTTON_HEIGHT + BUTTON_MARGIN);
            int relayState = relayStates[slot];
            int color = relayState == KasaSmartPlug.RELAY_ON ? TftTouchShield.GREEN
                    : relayState == KasaSmartPlug.RELAY_OFF ? TftTouchShield.RED : TftTouchShield.GRAY;
            TftTouchShield.fillRect(BUTTON_MARGIN, y, TftTouchShield.width() - BUTTON_MARGIN * 2, BUTTON_HEIGHT, color);
            TftTouchShield.drawRect(BUTTON_MARGIN, y, TftTouchShield.width() - BUTTON_MARGIN * 2, BUTTON_HEIGHT,
                    TftTouchShield.WHITE);
            TftTouchShield.setTextColor(TftTouchShield.BLACK, color);
            TftTouchShield.setTextSize(2);
            TftTouchShield.setCursor(18, y + 8);
            TftTouchShield.print(models[slot] == KasaSmartPlug.MODEL_HS100 ? "HS100 " : "HS110 ");
            printAddress(slot);
            TftTouchShield.setCursor(18, y + 32);
            TftTouchShield.print(relayState == KasaSmartPlug.RELAY_ON ? "ON - TAP TO TURN OFF"
                    : relayState == KasaSmartPlug.RELAY_OFF ? "OFF - TAP TO TURN ON" : "TAP TO TOGGLE");
        }

        /** Shown while a tapped button's TCP exchange is in flight, so a slow or failed request never looks stuck. */
        private void drawButtonBusy(int slot) {
            int y = FIRST_BUTTON_Y + slot * (BUTTON_HEIGHT + BUTTON_MARGIN);
            TftTouchShield.fillRect(BUTTON_MARGIN, y, TftTouchShield.width() - BUTTON_MARGIN * 2, BUTTON_HEIGHT,
                    TftTouchShield.GRAY);
            TftTouchShield.drawRect(BUTTON_MARGIN, y, TftTouchShield.width() - BUTTON_MARGIN * 2, BUTTON_HEIGHT,
                    TftTouchShield.WHITE);
            TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.GRAY);
            TftTouchShield.setTextSize(2);
            TftTouchShield.setCursor(18, y + 8);
            TftTouchShield.print(models[slot] == KasaSmartPlug.MODEL_HS100 ? "HS100 " : "HS110 ");
            printAddress(slot);
            TftTouchShield.setCursor(18, y + 32);
            TftTouchShield.print("Working...");
        }

        private int buttonAt(int x, int y) {
            if (x < BUTTON_MARGIN || x >= TftTouchShield.width() - BUTTON_MARGIN) {
                return -1;
            }
            for (int slot = 0; slot < plugCount; slot++) {
                int buttonY = FIRST_BUTTON_Y + slot * (BUTTON_HEIGHT + BUTTON_MARGIN);
                if (y >= buttonY && y < buttonY + BUTTON_HEIGHT) {
                    return slot;
                }
            }
            return -1;
        }

        private void toggle(int[] selected, int slot) {
            int responseLength = relayStates[slot] == KasaSmartPlug.RELAY_ON
                    ? KasaSmartPlug.turnOff(selected, request, response, response.length)
                    : KasaSmartPlug.turnOn(selected, request, response, response.length);
            if (responseLength > 0) {
                relayStates[slot] = relayStates[slot] == KasaSmartPlug.RELAY_ON
                        ? KasaSmartPlug.RELAY_OFF : KasaSmartPlug.RELAY_ON;
            }
        }

        private void printAddress(int slot) {
            int offset = slot * Udp.ENDPOINT_SIZE;
            TftTouchShield.print(plugEndpoints[offset]);
            TftTouchShield.print(".");
            TftTouchShield.print(plugEndpoints[offset + 1]);
            TftTouchShield.print(".");
            TftTouchShield.print(plugEndpoints[offset + 2]);
            TftTouchShield.print(".");
            TftTouchShield.print(plugEndpoints[offset + 3]);
        }
    }

    private static void copyAddress(int[] source, int[] targets, int targetSlot) {
        int offset = targetSlot * Udp.ENDPOINT_SIZE;
        targets[offset] = source[0];
        targets[offset + 1] = source[1];
        targets[offset + 2] = source[2];
        targets[offset + 3] = source[3];
    }

    private static void copyAddress(int[] source, int sourceSlot, int[] target) {
        int offset = sourceSlot * Udp.ENDPOINT_SIZE;
        target[0] = source[offset];
        target[1] = source[offset + 1];
        target[2] = source[offset + 2];
        target[3] = source[offset + 3];
    }

    private static void showStatus(String status) {
        TftTouchShield.fillScreen(TftTouchShield.NAVY);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(12, 12);
        TftTouchShield.print("KASA HS100 / HS110");
        TftTouchShield.setTextSize(1);
        TftTouchShield.setCursor(12, 48);
        TftTouchShield.print(status);
    }
}
