package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** One HTTPS request, used as the serial baseline for {@link ParallelHttpsGet}. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoR3WiFiRev2Board.class, ArduinoUnoQBoard.class})
public final class SingleHttpsGet {
    private static final Wifi WIFI = ArduinoUno.getWifi();

    private static final String URL = "https://example.com/?request=single";

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
        WIFI.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < 30) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() == Wifi.STATUS_CONNECTED) {
            Serial.println("wifi connected");
            return true;
        }
        Serial.println("wifi connection failed");
        return false;
    }
}
