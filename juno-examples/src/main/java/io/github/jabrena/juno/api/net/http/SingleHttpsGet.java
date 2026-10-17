package io.github.jabrena.juno.api.net.http;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Wifi;

/** One HTTPS request, used as the serial baseline for {@link ParallelHttpsGet}. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoR3WiFiRev2.class, ArduinoUnoQ.class})
public final class SingleHttpsGet {
    private static final String URL = "https://httpbun.com/anything?request=single";

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        if (!connectWifi()) {
            return;
        }

        byte[] body = new byte[1];
        byte[] headers = new byte[256];
        int[] statusAndHeadersLength = new int[2];
        int bodyLength = HttpClient.get(URL, body, body.length, headers, headers.length, statusAndHeadersLength);
        Serial.print("single status=");
        Serial.print(statusAndHeadersLength[0]);
        Serial.print(" body=");
        Serial.println(bodyLength);
    }

    private static boolean connectWifi() {
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        int attempts = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < 30) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (Wifi.status() == Wifi.STATUS_CONNECTED) {
            Serial.println("wifi connected");
            return true;
        }
        Serial.println("wifi connection failed");
        return false;
    }
}
