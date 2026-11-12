package io.github.jabrena.juno.api.hardware.device.kasa;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.security.ExampleSecrets;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.kasa.KasaSmartPlug;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Finds legacy TP-Link Kasa HS100/HS110 plugs on the local network and controls them from an ELEGOO TFT shield.
 *
 * <p>Provision {@code wifi.ssid} and {@code wifi.password} with {@code juno:secret}. The display stays live while the
 * board joins Wi-Fi or reopens discovery, and its three fixed buttons toggle the first three plugs found.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class KasaSmartPlugDiscovery {
    private static final Wifi WIFI = ArduinoUno.getWifi();

    private static final Clock CLOCK = ArduinoUno.getClock();
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
        TftTouchShield tft = new TftTouchShield();
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(500);
        Serial.println("Kasa discovery");
        tft.setRotation(TftTouchShield.LANDSCAPE_FLIPPED);
        new Controller().run(tft);
    }

    /** A plug found on the network: where it is, its model, and its relay as its last reply said. */
    private static final class Plug {
        private InetAddress address;
        private int model;
        private int relayState;
    }

    /** Fixed startup allocations and an allocation-free state machine, following Miner2's board-side service model. */
    private static final class Controller {
        private static final int JOINING = 0;
        private static final int LISTENING = 1;
        private static final int READY = 2;

        private final byte[] request = new byte[KasaSmartPlug.REQUEST_BUFFER_SIZE];
        private final byte[] response = new byte[KasaSmartPlug.DEFAULT_RESPONSE_BUFFER_SIZE];
        private final Plug[] plugs = new Plug[MAX_PLUGS];
        private int plugCount;
        private int state;
        private int nextPoll;
        private int nextDiscovery;
        private DatagramSocket socket;
        private DatagramPacket requestPacket;
        private DatagramPacket replyPacket;

        Controller() {
            for (int slot = 0; slot < MAX_PLUGS; slot++) {
                plugs[slot] = new Plug();
            }
        }

        void run(TftTouchShield tft) {
            startWifi(tft, CLOCK.millis());
            while (true) {
                int now = CLOCK.millis();
                stepLink(tft, now);
                if (state == READY) {
                    discover(tft, now);
                    handleTouch(tft);
                }
                Delay.millis(10);
            }
        }

        private void startWifi(TftTouchShield tft, int now) {
            closeDiscovery();
            showStatus(tft, "Connecting WiFi...");
            Serial.println("Kasa: connecting WiFi");
            WIFI.begin(ExampleSecrets.wifiSsid(), ExampleSecrets.wifiPassword());
            state = JOINING;
            nextPoll = now;
        }

        private void stepLink(TftTouchShield tft, int now) {
            if (now - nextPoll < 0) {
                return;
            }
            nextPoll = now + WIFI_POLL_MILLIS;
            if (state == JOINING) {
                if (WIFI.status() == Wifi.STATUS_CONNECTED) {
                    state = LISTENING;
                    nextPoll = now;
                    showStatus(tft, "Opening discovery...");
                    Serial.println("Kasa: WiFi connected");
                }
                return;
            }
            if (state == LISTENING) {
                if (openDiscovery()) {
                    state = READY;
                    nextDiscovery = now;
                    renderPlugs(tft);
                    Serial.println("Kasa discovery ready");
                } else {
                    nextPoll = now + LISTEN_RETRY_MILLIS;
                    showStatus(tft, "Retrying discovery...");
                }
                return;
            }
            if (WIFI.status() != Wifi.STATUS_CONNECTED) {
                startWifi(tft, now);
            }
        }

        /** Opens the discovery socket and its two packets, once; false while the port is still taken. */
        private boolean openDiscovery() {
            try {
                socket = KasaSmartPlug.openDiscovery();
                requestPacket = KasaSmartPlug.discoveryRequest(request);
                replyPacket = new DatagramPacket(response, response.length);
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        private void closeDiscovery() {
            if (socket != null) {
                socket.close();
                socket = null;
            }
        }

        private void discover(TftTouchShield tft, int now) {
            if (now - nextDiscovery >= 0) {
                KasaSmartPlug.broadcastDiscovery(socket, requestPacket);
                nextDiscovery = now + DISCOVERY_INTERVAL_MILLIS;
            }
            int discoveryLength = KasaSmartPlug.receiveDiscovery(socket, replyPacket);
            if (discoveryLength <= 0) {
                return;
            }
            int model = KasaSmartPlug.model(response, discoveryLength);
            if (model == KasaSmartPlug.MODEL_UNKNOWN) {
                return;
            }
            InetAddress sender = replyPacket.getAddress();
            int slot = indexOf(sender);
            int relayState = KasaSmartPlug.relayState(response, discoveryLength);
            if (slot < 0 && plugCount < MAX_PLUGS) {
                Plug plug = plugs[plugCount];
                plug.address = sender;
                plug.model = model;
                plug.relayState = relayState;
                plugCount++;
                renderPlugs(tft);
            } else if (slot >= 0 && relayState != plugs[slot].relayState) {
                plugs[slot].relayState = relayState;
                drawButton(tft, slot);
            }
        }

        private void handleTouch(TftTouchShield tft) {
            if (!tft.readTouch()) {
                return;
            }
            int slot = buttonAt(tft, tft.touchX(), tft.touchY());
            if (slot >= 0) {
                drawButtonBusy(tft, slot);
                toggle(plugs[slot]);
                drawButton(tft, slot);
            }
            while (tft.readTouch()) {
                Delay.millis(25);
            }
        }

        private int indexOf(InetAddress candidate) {
            for (int slot = 0; slot < plugCount; slot++) {
                if (plugs[slot].address.equals(candidate)) {
                    return slot;
                }
            }
            return -1;
        }

        private void renderPlugs(TftTouchShield tft) {
            showStatus(tft, "Scanning local network");
            for (int slot = 0; slot < plugCount; slot++) {
                drawButton(tft, slot);
            }
        }

        private void drawButton(TftTouchShield tft, int slot) {
            int y = FIRST_BUTTON_Y + slot * (BUTTON_HEIGHT + BUTTON_MARGIN);
            Plug plug = plugs[slot];
            int relayState = plug.relayState;
            int color = relayState == KasaSmartPlug.RELAY_ON ? TftTouchShield.GREEN
                    : relayState == KasaSmartPlug.RELAY_OFF ? TftTouchShield.RED : TftTouchShield.GRAY;
            tft.fillRect(BUTTON_MARGIN, y, tft.width() - BUTTON_MARGIN * 2, BUTTON_HEIGHT, color);
            tft.drawRect(BUTTON_MARGIN, y, tft.width() - BUTTON_MARGIN * 2, BUTTON_HEIGHT,
                    TftTouchShield.WHITE);
            tft.setTextColor(TftTouchShield.BLACK, color);
            tft.setTextSize(2);
            tft.setCursor(18, y + 8);
            tft.print(plug.model == KasaSmartPlug.MODEL_HS100 ? "HS100 " : "HS110 ");
            tft.print(plug.address.toString());
            tft.setCursor(18, y + 32);
            tft.print(relayState == KasaSmartPlug.RELAY_ON ? "ON - TAP TO TURN OFF"
                    : relayState == KasaSmartPlug.RELAY_OFF ? "OFF - TAP TO TURN ON" : "TAP TO TOGGLE");
        }

        /** Shown while a tapped button's TCP exchange is in flight, so a slow or failed request never looks stuck. */
        private void drawButtonBusy(TftTouchShield tft, int slot) {
            int y = FIRST_BUTTON_Y + slot * (BUTTON_HEIGHT + BUTTON_MARGIN);
            Plug plug = plugs[slot];
            tft.fillRect(BUTTON_MARGIN, y, tft.width() - BUTTON_MARGIN * 2, BUTTON_HEIGHT,
                    TftTouchShield.GRAY);
            tft.drawRect(BUTTON_MARGIN, y, tft.width() - BUTTON_MARGIN * 2, BUTTON_HEIGHT,
                    TftTouchShield.WHITE);
            tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.GRAY);
            tft.setTextSize(2);
            tft.setCursor(18, y + 8);
            tft.print(plug.model == KasaSmartPlug.MODEL_HS100 ? "HS100 " : "HS110 ");
            tft.print(plug.address.toString());
            tft.setCursor(18, y + 32);
            tft.print("Working...");
        }

        private int buttonAt(TftTouchShield tft, int x, int y) {
            if (x < BUTTON_MARGIN || x >= tft.width() - BUTTON_MARGIN) {
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

        private void toggle(Plug plug) {
            int responseLength = plug.relayState == KasaSmartPlug.RELAY_ON
                    ? KasaSmartPlug.turnOff(plug.address, request, response, response.length)
                    : KasaSmartPlug.turnOn(plug.address, request, response, response.length);
            if (responseLength > 0) {
                plug.relayState = plug.relayState == KasaSmartPlug.RELAY_ON
                        ? KasaSmartPlug.RELAY_OFF : KasaSmartPlug.RELAY_ON;
            }
        }
    }

    private static void showStatus(TftTouchShield tft, String status) {
        tft.fillScreen(TftTouchShield.NAVY);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        tft.setTextSize(2);
        tft.setCursor(12, 12);
        tft.print("KASA HS100 / HS110");
        tft.setTextSize(1);
        tft.setCursor(12, 48);
        tft.print(status);
    }
}
