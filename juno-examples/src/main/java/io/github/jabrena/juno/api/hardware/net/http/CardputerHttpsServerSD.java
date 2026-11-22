package io.github.jabrena.juno.api.hardware.net.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.display.Display;
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

import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * {@link NanoHttpsServerSD} on the M5Stack Cardputer: joins the Wi-Fi network named in {@code application.properties}
 * on its built-in microSD card, then loads a certificate and its private key from the same card and serves port 443
 * with {@code GET /api/status} and {@code POST /api/led?state=on|off}. The Cardputer has no plain LED, so its display
 * stands in for one: black when off, green when on. The board's address is printed over Serial at 115200 baud every
 * 10 seconds.
 *
 * <p>Besides {@code wifi.ssid} and {@code wifi.password}, the card needs {@code server.crt} and {@code server.key},
 * copied as-is in PEM form. The key is an unencrypted EC key, either SEC1 ({@code BEGIN EC PRIVATE KEY}) or PKCS#8
 * ({@code BEGIN PRIVATE KEY}); the board's TLS stack reads both. The certificate can be self-signed or signed by a
 * local CA, and must name the address clients connect to, e.g.:
 * <pre>{@code
 * openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 3650 \
 *   -subj /CN=<cert-ip> -addext subjectAltName=IP:<cert-ip> -keyout server.key -out server.crt
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.CardputerHttpsServerSD -Djuno.board=m5stack-cardputer
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=m5stack-cardputer -Djuno.baudRate=115200
 * curl --cacert server.crt https://<cert-ip>/api/status
 * curl --cacert server.crt -X POST 'https://<cert-ip>/api/led?state=on'
 * }</pre>
 * Pass {@code --cacert} the CA certificate instead when a CA signed {@code server.crt}. When DHCP gives the board an
 * address other than the one in the certificate, keep TLS verification by adding
 * {@code --connect-to <cert-ip>:443:<address>:443}.
 */
@Board({M5StackCardputerBoard.class})
public final class CardputerHttpsServerSD {
    private static boolean ledOn;

    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int HTTPS_PORT = 443;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_PROBLEM_JSON = "application/problem+json";
    private static final int PEM_CAPACITY = 2048;

    private CardputerHttpsServerSD() {
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("HTTPS: boot");

        Display display = MCU.getDisplay();
        display.fillScreen(Display.BLACK);
        Wifi wifi = MCU.getWifi();
        SdCard sdCard = openSdCard();
        if (sdCard == null || !joinWifi(wifi, sdCard)) return;
        startServer(display, loadSslContext(sdCard));
        reportAlive(wifi);
    }

    /** Mounts the built-in microSD card, or returns {@code null} when it is missing. */
    private static SdCard openSdCard() {
        Serial.println("HTTPS: opening microSD");
        SdCard sdCard = MCU.getSdCard();
        if (!sdCard.begin()) {
            Serial.println("HTTPS: microSD initialization failed");
            return null;
        }
        Serial.println("HTTPS: microSD initialized");
        return sdCard;
    }

    /** Joins the network named in {@code application.properties}; {@code false} when that fails. */
    private static boolean joinWifi(Wifi wifi, SdCard sdCard) throws IOException {
        Credentials credentials = loadCredentials(sdCard);
        if (credentials == null) {
            Serial.println("HTTPS: application.properties or Wi-Fi credentials unavailable");
            return false;
        }
        return connectWifi(wifi, credentials.ssid(), credentials.password());
    }

    /** Registers the API handlers and starts listening on port 443 with the given server identity. */
    private static void startServer(Display display, SSLContext sslContext) throws IOException {
        HttpsServer server = HttpsServer.create(new InetSocketAddress(HTTPS_PORT), 4);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        server.createContext("/api/status", CardputerHttpsServerSD::status);
        server.createContext("/api/led", exchange -> setLed(exchange, display));
        server.createContext("/", CardputerHttpsServerSD::notFound);
        server.start();
        Serial.println("Listening on port 443");
    }

    /** Prints the address every 10 seconds, so a monitor attached late still finds the server. */
    private static void reportAlive(Wifi wifi) {
        while (true) {
            printLocalIp(wifi);
            Serial.println("HTTPS: server alive");
            Delay.millis(10000);
        }
    }

    /** Builds the SSL context from {@code server.crt} and {@code server.key} on the microSD card. */
    private static SSLContext loadSslContext(SdCard sdCard) throws IOException, GeneralSecurityException {
        Serial.println("HTTPS: loading server.crt and server.key");
        Certificate certificate = loadCertificate(sdCard);
        PrivateKey privateKey = loadPrivateKey(sdCard);
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

    private static Certificate loadCertificate(SdCard sdCard) throws IOException, GeneralSecurityException {
        try (InputStream input = sdCard.open("server.crt")) {
            if (input == null) throw new IOException("server.crt is missing");
            return CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
    }

    /** Reads an unencrypted EC private key (SEC1 or PKCS#8 PEM); the board's TLS stack accepts either DER. */
    private static PrivateKey loadPrivateKey(SdCard sdCard) throws IOException, GeneralSecurityException {
        String base64;
        try (InputStream input = sdCard.open("server.key")) {
            if (input == null) throw new IOException("server.key is missing");
            base64 = readPemBody(input);
        }
        byte[] der = Base64.getMimeDecoder().decode(base64);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    /** The Base64 lines of a PEM file, without its {@code -----BEGIN/END} lines or line breaks. */
    private static String readPemBody(InputStream input) throws IOException {
        StringBuilder pem = new StringBuilder(PEM_CAPACITY);
        int pemLength = 0;
        boolean skipLine = false;
        boolean lineStart = true;
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
        return pem.toString();
    }

    private static void status(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, HTTP_BAD_METHOD, APPLICATION_PROBLEM_JSON, "{\"title\":\"method not allowed\"}");
            return;
        }
        send(exchange, HTTP_OK, APPLICATION_JSON, ledOn ? "{\"led\":\"on\"}" : "{\"led\":\"off\"}");
    }

    private static void setLed(HttpExchange exchange, Display display) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, HTTP_BAD_METHOD, APPLICATION_PROBLEM_JSON, "{\"title\":\"method not allowed\"}");
            return;
        }
        String path = exchange.getRequestURI().toString();
        if (!path.equals("/api/led?state=on") && !path.equals("/api/led?state=off")) {
            send(exchange, HTTP_BAD_REQUEST, APPLICATION_PROBLEM_JSON, "{\"title\":\"invalid state\"}");
            return;
        }
        ledOn = path.equals("/api/led?state=on");
        display.fillScreen(ledOn ? Display.GREEN : Display.BLACK);
        send(exchange, HTTP_OK, APPLICATION_JSON, ledOn ? "{\"led\":\"on\"}" : "{\"led\":\"off\"}");
    }

    private static void notFound(HttpExchange exchange) throws IOException {
        send(exchange, HTTP_NOT_FOUND, APPLICATION_PROBLEM_JSON, "{\"title\":\"not found\"}");
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set(CONTENT_TYPE, contentType);
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
