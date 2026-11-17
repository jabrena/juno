package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

/**
 * Joins the Wi-Fi network named in {@code application.properties} on the microSD card of a Nano Connector Carrier,
 * then sends an HTTPS GET every ten seconds with the JDK's {@code java.net.http} client. The host and the request
 * path both come from the card, so the URI is built at runtime. The TLS client checks the server's chain against
 * Mozilla's root certificates. Prints the status, the body length and the first bytes of the body at 115200 baud.
 *
 * <p>The card needs {@code wifi.ssid}, {@code wifi.password}, {@code https.host} and {@code https.path}, for example {@code https.host=api.open-meteo.com}
 * and {@code https.path=/v1/forecast?latitude=40.4168&longitude=-3.7038&current=temperature_2m}.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.NanoHttpsGetSD -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoHttpsGetSD {
    private static final Wifi WIFI = MCU.getWifi();
    private static final SdCard SD_CARD = new NanoConnectorCarrier(MCU.getGpio()).getSdCard();

    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int PREVIEW_BYTES = 80;

    private NanoHttpsGetSD() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Settings settings = loadSettings();
        if (settings == null) {
            return;
        }
        if (!connectWifi(settings.ssid(), settings.password())) {
            return;
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + settings.host() + settings.path())).build();
        while (true) {
            try {
                HttpResponse<String> reply = client.send(request, HttpResponse.BodyHandlers.ofString());
                Serial.print("status=");
                Serial.print(reply.statusCode());
                Serial.print(" body=");
                Serial.println(reply.body().length());
                printPreview(reply.body());
            } catch (IOException e) {
                Serial.println("request failed");
            }
            Delay.millis(10000);
        }
    }

    private static boolean connectWifi(String ssid, String password) {
        WIFI.begin(ssid, password);
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            return false;
        }
        Serial.println("WiFi connected");
        return true;
    }

    private static void printPreview(String body) {
        Serial.print("body: ");
        for (int i = 0; i < body.length() && i < PREVIEW_BYTES; i++) {
            Serial.print(body.charAt(i));
        }
        Serial.println("");
    }

    private static Settings loadSettings() throws IOException {
        try (InputStream file = SD_CARD.open("application.properties")) {
            if (file == null) {
                Serial.println("application.properties not found");
                return null;
            }
            Properties properties = new Properties();
            properties.load(file);
            String ssid = properties.getProperty("wifi.ssid");
            String password = properties.getProperty("wifi.password");
            String host = properties.getProperty("https.host");
            String path = properties.getProperty("https.path");
            if (ssid == null || password == null || host == null || path == null) {
                Serial.println("wifi.ssid, wifi.password, https.host or https.path is missing");
                return null;
            }
            return new Settings(ssid, password, host, path);
        }
    }

    private record Settings(String ssid, String password, String host, String path) {
    }
}
