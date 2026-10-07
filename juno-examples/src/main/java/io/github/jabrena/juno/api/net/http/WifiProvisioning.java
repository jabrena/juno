package io.github.jabrena.juno.api.net.http;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.net.WifiProvisioner;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * Wi-Fi setup from a phone instead of build-time credentials: the board opens the access point
 * {@value #AP_SSID} (passphrase {@value #AP_PASSWORD}), and {@link WifiProvisioner} serves a form at
 * {@code http://192.168.4.1/} and joins the network the user submits, printing the new address over
 * Serial at 115200 baud. On UNO Q the hotspot must be started on the Linux side first; see
 * {@link WifiProvisioner} for what each board does with the submitted credentials.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class WifiProvisioning {
    private static final String AP_SSID = "juno";
    private static final String AP_PASSWORD = "juno";

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        WifiProvisioner.run(AP_SSID, AP_PASSWORD);
        Serial.println("Provisioning done");
    }
}
