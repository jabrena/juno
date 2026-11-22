package io.github.jabrena.juno.api.hardware.net.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Properties;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * {@link NanoHttpServerSD} over HTTPS: joins the Wi-Fi network named in {@code application.properties} on the microSD
 * card of a Nano Connector Carrier, then loads a certificate and its private key from the same card and serves port
 * 443 with {@code GET /api/status} and {@code POST /api/led?state=on|off}. Wi-Fi is joined before the TLS identity is
 * loaded, and the board's address is printed over Serial at 115200 baud every 10 seconds.
 *
 * <p>Besides {@code wifi.ssid} and {@code wifi.password}, the card needs {@code server.crt} and {@code server.key},
 * copied as-is in PEM form. The key is an unencrypted EC key, either SEC1 ({@code BEGIN EC PRIVATE KEY}, what
 * {@code openssl ecparam -genkey} writes) or PKCS#8 ({@code BEGIN PRIVATE KEY}); the board's TLS stack reads both. The
 * certificate can be self-signed or signed by a local CA, and must name the address clients connect to, e.g.:
 * <pre>{@code
 * openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 3650 \
 *   -subj /CN=<cert-ip> -addext subjectAltName=IP:<cert-ip> -keyout server.key -out server.crt
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.NanoHttpsServerSD -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * curl --cacert server.crt https://<cert-ip>/api/status
 * curl --cacert server.crt -X POST 'https://<cert-ip>/api/led?state=on'
 * }</pre>
 * Pass {@code --cacert} the CA certificate instead when a CA signed {@code server.crt}. When DHCP gives the board an
 * address other than the one in the certificate, keep TLS verification by adding
 * {@code --connect-to <cert-ip>:443:<address>:443}. The server identity is loaded once at startup into an in-memory
 * key store and installed through an SSL context.
 */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoHttpsServerSD {
    private static boolean ledOn;

    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int HTTPS_PORT = 443;
    private static final int PEM_CAPACITY = 2048;

    private NanoHttpsServerSD() {
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("HTTPS: boot");

        Wifi wifi = MCU.getWifi();
        Gpio gpio = MCU.getGpio();
        int ledPin = gpio.getBuiltinLed();
        Serial.println("HTTPS: opening Nano Connector Carrier SD");
        SdCard sdCard = new NanoConnectorCarrier(gpio).getSdCard();
        if (!sdCard.begin()) {
            haltAndReport("HTTPS: Nano Connector Carrier SD initialization failed");
            return;
        }
        Serial.println("HTTPS: Nano Connector Carrier SD initialized");
        Credentials credentials = loadCredentials(sdCard);
        if (credentials == null) {
            haltAndReport("HTTPS: application.properties or Wi-Fi credentials unavailable");
            return;
        }
        if (!connectWifi(wifi, credentials.ssid(), credentials.password())) {
            haltAndReport("HTTPS: Wi-Fi connection failed");
            return;
        }
        Serial.println("HTTPS: loading server.crt and server.key");
        SSLContext sslContext = loadSslContext(sdCard);

        gpio.pinMode(ledPin, gpio.getOutput());
        gpio.digitalWrite(ledPin, false);
        HttpsServer server = HttpsServer.create(new InetSocketAddress(HTTPS_PORT), 4);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        server.createContext("/api/status", NanoHttpsServerSD::status);
        server.createContext("/api/led", exchange -> setLed(exchange, gpio, ledPin));
        server.createContext("/", NanoHttpsServerSD::notFound);
        server.start();
        Serial.println("Listening on port 443");

        while (true) {
            printLocalIp(wifi);
            Serial.println("HTTPS: server alive");
            Delay.millis(10000);
        }
    }

    /** Loads an X.509 certificate and an unencrypted EC private key (SEC1 or PKCS#8 PEM) from the carrier's SD card. */
    private static SSLContext loadSslContext(SdCard sdCard) throws IOException, GeneralSecurityException {
        Certificate certificate;
        try (InputStream input = sdCard.open("server.crt")) {
            if (input == null) throw new IOException("server.crt is missing");
            certificate = CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        StringBuilder pem = new StringBuilder(PEM_CAPACITY);
        int pemLength = 0;
        boolean skipLine = false;
        boolean lineStart = true;
        try (InputStream input = sdCard.open("server.key")) {
            if (input == null) throw new IOException("server.key is missing");
            int value;
            while ((value = input.read()) >= 0) {
                if (value == '\n') {
                    skipLine = false;
                    lineStart = true;
                } else if (lineStart && value == '-') {
                    skipLine = true;
                    lineStart = false;
                } else if (!skipLine && value != '\r') {
                    if (pemLength == PEM_CAPACITY) throw new IOException("server.key exceeds PEM_CAPACITY");
                    pem.append((char) value);
                    pemLength++;
                    lineStart = false;
                }
            }
        }
        byte[] der = Base64.getMimeDecoder().decode(pem.toString());
        PrivateKey privateKey = KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
        // This store exists only in memory; no environment variable or password file is needed.
        char[] password = new char[0];
        KeyStore identity = KeyStore.getInstance("PKCS12");
        identity.load(null, password);
        identity.setKeyEntry("server", privateKey, password, new Certificate[]{certificate});
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(identity, password);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(managers.getKeyManagers(), null, null);
        return context;
    }

    private static void status(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, "application/problem+json", "{\"title\":\"method not allowed\"}");
            return;
        }
        send(exchange, 200, "application/json", ledOn ? "{\"led\":\"on\"}" : "{\"led\":\"off\"}");
    }

    private static void setLed(HttpExchange exchange, Gpio gpio, int ledPin) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, "application/problem+json", "{\"title\":\"method not allowed\"}");
            return;
        }
        String path = exchange.getRequestURI().toString();
        if (!path.equals("/api/led?state=on") && !path.equals("/api/led?state=off")) {
            send(exchange, 400, "application/problem+json", "{\"title\":\"invalid state\"}");
            return;
        }
        ledOn = path.equals("/api/led?state=on");
        gpio.digitalWrite(ledPin, ledOn);
        send(exchange, 200, "application/json", ledOn ? "{\"led\":\"on\"}" : "{\"led\":\"off\"}");
    }

    private static void notFound(HttpExchange exchange) throws IOException {
        send(exchange, 404, "application/problem+json", "{\"title\":\"not found\"}");
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length());
        try (OutputStream output = exchange.getResponseBody()) {
            for (int index = 0; index < body.length(); index++) {
                output.write(body.charAt(index));
            }
        }
    }

    private static boolean connectWifi(Wifi wifi, String ssid, String password) {
        wifi.begin(ssid, password);
        int attempts = 0;
        while (wifi.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Serial.print("WiFi status: ");
            Serial.println(wifi.status());
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            return false;
        }
        Serial.println("WiFi connected");
        return true;
    }

    private static void haltAndReport(String message) {
        while (true) {
            Serial.println(message);
            Delay.millis(5000);
        }
    }

    private static void printLocalIp(Wifi wifi) {
        int[] octets = new int[4];
        wifi.localIP(octets);
        Serial.print("address=");
        Serial.print(octets[0]);
        Serial.print(".");
        Serial.print(octets[1]);
        Serial.print(".");
        Serial.print(octets[2]);
        Serial.print(".");
        Serial.println(octets[3]);
    }

    private static Credentials loadCredentials(SdCard sdCard) throws IOException {
        try (InputStream file = sdCard.open("application.properties")) {
            if (file == null) {
                Serial.println("application.properties not found");
                return null;
            }
            Properties properties = new Properties();
            properties.load(file);
            String ssid = properties.getProperty("wifi.ssid");
            String password = properties.getProperty("wifi.password");
            if (ssid == null || password == null) {
                Serial.println("WiFi credentials are missing");
                return null;
            }
            return new Credentials(ssid, password);
        }
    }

    private record Credentials(String ssid, String password) {
    }
}
