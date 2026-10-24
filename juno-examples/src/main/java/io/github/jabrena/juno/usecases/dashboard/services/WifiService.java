package io.github.jabrena.juno.usecases.dashboard.services;

import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.WifiCredentials;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.WifiStatus;
import io.github.jabrena.juno.util.Delay;

/**
 * Joins the Wi-Fi network on a thread of its own and publishes the outcome into the model. It waits for the
 * credentials the SD-card service reads, and never touches the card itself.
 */
public final class WifiService {
    private static final Wifi WIFI = ArduinoOne.getWifi();

    private static final int MAX_CONNECTION_ATTEMPTS = 10;

    private final TftDashboardState model;

    public WifiService(TftDashboardState model) {
        this.model = model;
    }

    /** Starts the thread that connects and publishes the result. */
    public void start() {
        Thread.startVirtualThread(this::run);
    }

    // The board Thread subset has no interrupt-status API; this example publishes interruption as a Wi-Fi failure.
    @SuppressWarnings("java:S2142")
    private void run() {
        try {
            model.publishWifiStatus(connect(model.awaitCredentials()));
        } catch (InterruptedException interrupted) {
            Serial.println("Wi-Fi thread interrupted");
            model.publishWifiStatus(new WifiStatus(false));
        }
    }

    private WifiStatus connect(WifiCredentials credentials) {
        if (credentials == null) {
            return new WifiStatus(false);
        }
        WIFI.begin(credentials.ssid(), credentials.password());
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        return new WifiStatus(WIFI.status() == Wifi.STATUS_CONNECTED);
    }
}
