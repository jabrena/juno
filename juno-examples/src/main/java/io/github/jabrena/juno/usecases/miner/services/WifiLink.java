package io.github.jabrena.juno.usecases.miner.services;

import io.github.jabrena.juno.games.blockchain.NodeIo;
import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.udp.Udp;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * {@link NodeIo#connect()} split into steps, so the frame loop keeps drawing while the node joins Wi-Fi: {@link #start}
 * asks the board to join, and each {@link #step} polls the join and then opens the UDP socket on {@link NodeIo#PORT},
 * never waiting. Once it is up, a step still polls the link now and then, so the header's Wi-Fi icon follows a link
 * that drops. Credentials are the build-time {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD}, as in {@code NodeIo}.
 */
final class WifiLink {
    private static final Wifi WIFI = ArduinoOne.getWifi();

    static final int IDLE = 0;
    static final int JOINING = 1;
    static final int LISTENING = 2;
    static final int READY = 3;

    /** How often the link is polled; the original blocking loop waited this long between polls too. */
    static final int POLL_MILLIS = 500;
    /** How long to wait before trying again a UDP port that another socket still holds. */
    static final int LISTEN_RETRY_MILLIS = 1000;

    private int state = IDLE;
    private boolean connected;
    private int nextPoll;

    /**
     * Asks the board to join Wi-Fi, or to join again when the link is up but stuck, closing the UDP socket first. On a
     * UNO R4 WiFi the modem's {@code WiFi.begin} itself may hold the call until it joins or gives up; the polling after
     * it never waits.
     */
    void start(int now) {
        Serial.println("Connecting Wi-Fi");
        // Close the old socket first: on the UNO Q its port stays held after a restart, so listening again fails.
        Udp.stop();
        connected = false;
        WIFI.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        state = JOINING;
        nextPoll = now;
    }

    /**
     * Drops the link from the program's side: closes the UDP socket and reports the link down until {@link #start}.
     * The board's Wi-Fi API has no disconnect, so the modem stays associated.
     */
    void disconnect() {
        Udp.stop();
        state = IDLE;
        connected = false;
    }

    /** Advances the join by at most one poll; does nothing before {@link #start} or between two polls. */
    void step(int now) {
        if (state == IDLE || now - nextPoll < 0) {
            return;
        }
        nextPoll = now + POLL_MILLIS;
        connected = WIFI.status() == Wifi.STATUS_CONNECTED;
        if (state == JOINING && connected) {
            state = LISTENING;
            nextPoll = now;
        } else if (state == LISTENING) {
            listen(now);
        }
    }

    /** Whether Wi-Fi is joined and the UDP socket open, so the node can send and receive. */
    boolean ready() {
        return state == READY;
    }

    /** Whether the board reported a Wi-Fi link at the last poll, socket or not. */
    boolean connected() {
        return connected;
    }

    int state() {
        return state;
    }

    private void listen(int now) {
        if (Udp.listen(NodeIo.PORT)) {
            Serial.println("Listening on UDP port " + NodeIo.PORT);
            state = READY;
        } else {
            Serial.println("UDP port busy, retrying");
            nextPoll = now + LISTEN_RETRY_MILLIS;
        }
    }
}
