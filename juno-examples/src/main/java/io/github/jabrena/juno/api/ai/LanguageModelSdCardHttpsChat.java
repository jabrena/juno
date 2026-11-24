package io.github.jabrena.juno.api.ai;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.github.jabrena.juno.ai.LanguageModel;
import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * {@link LanguageModelSdCardSecretStoreChat} over HTTPS: the Nano ESP32 loads {@code /JUNO.BIN} from the Nano Connector
 * Carrier's microSD card into PSRAM, joins the Wi-Fi network whose {@code wifi.ssid} and {@code wifi.password} are in
 * its Secret Store, and serves the {@link LanguageModelWebChat} page on port 443 with the certificate and key from the
 * same card. Open the {@code https://} address it prints every 10 seconds on the serial port.
 *
 * <p>Besides {@code JUNO.BIN}, the card needs {@code server.crt} and {@code server.key} in PEM form, as
 * {@code NanoHttpsServerSD} reads them: an unencrypted EC key (SEC1 or PKCS#8) and a certificate naming the address
 * clients connect to, e.g.:
 * <pre>{@code
 * openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 3650 \
 *   -subj /CN=<board-ip> -addext subjectAltName=IP:<board-ip> -keyout server.key -out server.crt
 * curl --cacert server.crt https://<board-ip>/api/status
 * curl --cacert server.crt -X POST --data 'What is Juno?' https://<board-ip>/api/chat
 * }</pre>
 * A browser warns about a self-signed certificate until it is trusted; the model still runs on the board, and TLS
 * only protects the page and the answers on the network.
 */
@Board({ArduinoNanoEsp32Board.class})
public final class LanguageModelSdCardHttpsChat {
    private static final LanguageModel MODEL = MCU.getLanguageModel();
    private static final String MODEL_FILE = "/JUNO.BIN";
    private static final int HTTPS_PORT = 443;
    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int RETRY_MS = 10000;
    private static final int PEM_CAPACITY = 2048;
    private static final int[] OCTETS = new int[4];

    private LanguageModelSdCardHttpsChat() {
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        SdCard card = new NanoConnectorCarrier(MCU.getGpio()).getSdCard();
        while (!card.begin()) {
            Serial.println("JLM1: no microSD card in the Nano Connector Carrier");
            Delay.millis(RETRY_MS);
        }
        Serial.print("JLM1: loading ");
        Serial.print(MODEL_FILE);
        Serial.println(" from the microSD card into PSRAM");
        ModelStatus status = MODEL.begin(card, MODEL_FILE);
        Serial.print("JLM1: model load status ");
        Serial.println(LanguageModelStatus.describe(status));

        Wifi wifi = MCU.getWifi();
        if (!joinWifi(wifi, MCU.getSecretStore())) {
            while (true) {
                Serial.println("JLM1: Wi-Fi unavailable; store wifi.ssid and wifi.password in the Secret Store");
                Delay.millis(RETRY_MS);
            }
        }
        Serial.println("JLM1: loading server.crt and server.key from the microSD card");
        HttpsServer server = HttpsServer.create(new InetSocketAddress(HTTPS_PORT), 4);
        server.setHttpsConfigurator(new HttpsConfigurator(loadSslContext(card)));
        LanguageModelWebChat.register(server);
        server.start();
        while (true) {
            Serial.print("JLM1: open https://");
            printAddress(wifi);
            Serial.print("JLM1: model from ");
            Serial.print(MODEL_FILE);
            Serial.println(MODEL.isLoaded() ? " online" : " offline");
            Delay.millis(RETRY_MS);
        }
    }

    /** Joins the network named in the Secret Store; {@code false} when a credential is missing or joining fails. */
    private static boolean joinWifi(Wifi wifi, SecretStore secrets) {
        String ssid = secrets.getString("wifi.ssid");
        String password = secrets.getString("wifi.password");
        if (ssid == null || password == null) {
            Serial.println("JLM1: wifi.ssid or wifi.password is missing from the Secret Store");
            return false;
        }
        wifi.begin(ssid, password);
        for (int attempt = 0; attempt < MAX_CONNECTION_ATTEMPTS && wifi.status() != Wifi.STATUS_CONNECTED; attempt++) {
            Serial.print("JLM1: Wi-Fi status ");
            Serial.println(wifi.status());
            Delay.millis(1000);
        }
        return wifi.status() == Wifi.STATUS_CONNECTED;
    }

    /** Builds the server identity from {@code server.crt} and {@code server.key}, in an in-memory key store. */
    private static SSLContext loadSslContext(SdCard card) throws IOException, GeneralSecurityException {
        Certificate certificate;
        try (InputStream input = card.open("server.crt")) {
            if (input == null) throw new IOException("server.crt is missing");
            certificate = CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        String base64;
        try (InputStream input = card.open("server.key")) {
            if (input == null) throw new IOException("server.key is missing");
            base64 = readPemBody(input);
        }
        PrivateKey key = KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(base64)));
        char[] password = new char[0];
        KeyStore identity = KeyStore.getInstance("PKCS12");
        identity.load(null, password);
        identity.setKeyEntry("server", key, password, new Certificate[]{certificate});
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(identity, password);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(managers.getKeyManagers(), null, null);
        return context;
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

    private static void printAddress(Wifi wifi) {
        wifi.localIP(OCTETS);
        Serial.print(OCTETS[0]);
        Serial.print(".");
        Serial.print(OCTETS[1]);
        Serial.print(".");
        Serial.print(OCTETS[2]);
        Serial.print(".");
        Serial.println(OCTETS[3]);
    }
}
