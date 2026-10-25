package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;

/**
 * Follows the Wi-Fi join that {@code main} started, one cheap status poll per frame, so the cover and settings stay
 * responsive while the board connects. The ESP32 keeps retrying a join on its own; a join that takes longer than
 * {@link #SLOW_JOIN_MILLIS} is only reported as slow.
 */
public final class MailboxConnection {
    public static final int NO_SETTINGS = 0;
    public static final int JOINING = 1;
    public static final int CONNECTED = 2;
    public static final int SLOW = 3;

    private static final int POLL_MILLIS = 500;
    private static final int SLOW_JOIN_MILLIS = 30000;

    private final Wifi wifi;
    private final Clock clock;
    private final int[] address = new int[4];
    private int state;
    private int joinStartedAt;
    private int nextPoll;
    private int revision;

    /** A connection to follow; {@code configured} is false when the SD card held no Wi-Fi settings to join with. */
    public MailboxConnection(Wifi wifi, boolean configured) {
        this(wifi, ArduinoUno.getClock(), configured);
    }

    MailboxConnection(Wifi wifi, Clock clock, boolean configured) {
        this.wifi = wifi;
        this.clock = clock;
        state = configured ? JOINING : NO_SETTINGS;
        joinStartedAt = clock.millis();
        nextPoll = joinStartedAt;
    }

    public void step() {
        int now = clock.millis();
        if (state == NO_SETTINGS || now - nextPoll < 0) {
            return;
        }
        nextPoll = now + POLL_MILLIS;
        if (wifi.status() == Wifi.STATUS_CONNECTED) {
            // DHCP can lag the association, so the link only counts once it has an address.
            wifi.localIP(address);
            if (state != CONNECTED && address[0] != 0) {
                printAddress();
                change(CONNECTED);
            }
            return;
        }
        if (state == CONNECTED) {
            Serial.println("Mailbox client: WiFi lost");
            joinStartedAt = now;
        }
        change(now - joinStartedAt >= SLOW_JOIN_MILLIS ? SLOW : JOINING);
    }

    public boolean isConnected() {
        return state == CONNECTED;
    }

    public int state() {
        return state;
    }

    /** Changes whenever {@link #state()} does, so a view can redraw only then. */
    public int revision() {
        return revision;
    }

    /** The board's address octet at {@code index}; meaningful only while {@link #isConnected()}. */
    public int address(int index) {
        return address[index];
    }

    private void change(int next) {
        if (state != next) {
            state = next;
            revision = revision + 1;
        }
    }

    private void printAddress() {
        Serial.println("Mailbox client: WiFi connected");
        Serial.print("Mailbox client: IP ");
        for (int index = 0; index < address.length; index++) {
            if (index > 0) {
                Serial.print(".");
            }
            Serial.print(address[index]);
        }
        Serial.println("");
    }
}
