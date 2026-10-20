package io.github.jabrena.juno.usecases.dashboard.services;

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
    private static final int MAX_CONNECTION_ATTEMPTS = 10;

    private final TftDashboardState model;

    public WifiService(TftDashboardState model) {
        this.model = model;
    }

    /** Starts the thread that connects and publishes the result. */
    public void start() {
        Thread.startVirtualThread(this::run);
    }

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
        Wifi.begin(credentials.ssid(), credentials.password());
        int attempts = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        return new WifiStatus(Wifi.status() == Wifi.STATUS_CONNECTED);
    }
}
