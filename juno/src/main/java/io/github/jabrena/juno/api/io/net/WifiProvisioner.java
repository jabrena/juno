package io.github.jabrena.juno.api.io.net;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.net.http.HttpMethod;
import io.github.jabrena.juno.api.io.net.http.HttpServer;
import io.github.jabrena.juno.api.io.net.http.HttpStatus;
import io.github.jabrena.juno.api.io.usb.Serial;

/**
 * Wi-Fi provisioning without baking credentials into the firmware, written in plain Java on top of
 * {@link Wifi} and {@link HttpServer} (it adds no intrinsics, and costs nothing in a program that
 * does not call it). {@link #run} opens an access point, serves an HTML form on port 80 and joins
 * the network the user submits. The caller must have started {@link Serial} first.
 *
 * <p>On <b>UNO R4 WiFi</b> the whole flow runs on the board: join the access point, open
 * {@code http://192.168.4.1/}, enter the SSID and password, and the board drops the access point,
 * joins that network and prints its address over {@link Serial}. If the connection fails within
 * {@value #CONNECT_TIMEOUT_MS} ms the access point comes back so the form can be submitted again.
 * The credentials live only in RAM.
 *
 * <p>On <b>UNO Q</b> Wi-Fi belongs to Linux, which the MCU cannot reconfigure: start the hotspot
 * there first ({@code nmcli device wifi hotspot ssid <ssid> password <password>}). The form is then
 * served through {@code arduino-router}, and the submitted credentials are printed over
 * {@link Serial} for Linux to apply ({@code nmcli device wifi connect <ssid> password <password>}).
 *
 * <p>Limits: SSID and password are at most {@value #MAX_FIELD} characters each (Juno's runtime
 * strings are small), and the form is plain HTTP, so the password crosses the setup network
 * unencrypted; on UNO R4 WiFi the access point's own WPA2 passphrase is what protects it.
 */
public final class WifiProvisioner {
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int MAX_FIELD = 31;

    private WifiProvisioner() {
    }

    private static final String FORM = "<!doctype html><html><head><meta name=viewport content=\"width=device-width\">"
            + "<title>Juno Wi-Fi setup</title></head><body><h1>Wi-Fi setup</h1>"
            + "<form method=post action=/connect>"
            + "<p><label>Network <input name=ssid maxlength=31></label></p>"
            + "<p><label>Password <input name=password type=password maxlength=31></label></p>"
            + "<p><button>Connect</button></p></form></body></html>";
    private static final String CONNECTING = "<!doctype html><html><body><h1>Connecting...</h1>"
            + "<p>The setup network will disappear. Watch the board's Serial output for its new address.</p>"
            + "</body></html>";

    /**
     * Serves the setup form until credentials are submitted and, on UNO R4 WiFi, the board has joined
     * that network; on UNO Q it returns once the credentials have been printed. {@code apPassword}
     * is a WPA2 passphrase of at least 8 characters, or empty for an open access point.
     */
    public static void run(String apSsid, String apPassword) {
        startAccessPoint(apSsid, apPassword);
        HttpServer.begin(80);
        Serial.println("Open http://192.168.4.1/ after joining the setup network");

        byte[] body = new byte[128];
        while (true) {
            int bodyLength = HttpServer.accept(body, body.length);
            if (bodyLength < 0) {
                continue;
            }
            String method = HttpServer.method();
            String path = HttpServer.path();
            if (method.equals(HttpMethod.POST) && path.equals("/connect")) {
                HttpServer.respond(HttpStatus.OK, "text/html", CONNECTING);
                if (provision(body, bodyLength)) {
                    return;
                }
                startAccessPoint(apSsid, apPassword);
            } else if (method.equals(HttpMethod.GET) && path.equals("/")) {
                HttpServer.respond(HttpStatus.OK, "text/html", FORM);
            } else {
                HttpServer.respond(HttpStatus.NOT_FOUND, "text/plain", "Not found");
            }
        }
    }

    private static void startAccessPoint(String apSsid, String apPassword) {
        Wifi.beginAP(apSsid, apPassword);
        while (Wifi.status() != Wifi.STATUS_AP_LISTENING && Wifi.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(200);
        }
        Serial.print("Access point ready: ");
        Serial.println(apSsid);
    }

    /** Joins the network submitted in {@code ssid=...&password=...}; returns whether the board is now connected. */
    private static boolean provision(byte[] body, int length) {
        StringBuilder ssid = new StringBuilder(32);
        StringBuilder password = new StringBuilder(32);
        int next = skipPast(body, length, 0, '=');
        next = copyValue(body, length, next, ssid);
        next = skipPast(body, length, next, '=');
        copyValue(body, length, next, password);

        Serial.print("Credentials received for: ");
        Serial.println(ssid.toString());
        if (Wifi.status() == Wifi.STATUS_CONNECTED) {
            // UNO Q: Linux owns Wi-Fi, so there is nothing for the MCU to join.
            Serial.print("Apply on Linux: nmcli device wifi connect ");
            Serial.print(ssid.toString());
            Serial.print(" password ");
            Serial.println(password.toString());
            return true;
        }

        Wifi.begin(ssid.toString(), password.toString());
        int deadline = Clock.millis() + CONNECT_TIMEOUT_MS;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && Clock.millis() < deadline) {
            Delay.millis(250);
        }
        if (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("Connection failed, reopening the setup network");
            return false;
        }
        printLocalIP();
        return true;
    }

    /** Index just after the next {@code marker} at or beyond {@code from}, or {@code length} when there is none. */
    private static int skipPast(byte[] body, int length, int from, char marker) {
        int i = from;
        while (i < length && body[i] != marker) {
            i = i + 1;
        }
        return i < length ? i + 1 : length;
    }

    /** Appends the URL-decoded value starting at {@code from} up to the next {@code &}; returns the index after it. */
    private static int copyValue(byte[] body, int length, int from, StringBuilder out) {
        int i = from;
        int count = 0;
        while (i < length && body[i] != '&') {
            int c = body[i];
            if (c == '%' && i + 2 < length) {
                c = hexValue(body[i + 1]) * 16 + hexValue(body[i + 2]);
                i = i + 2;
            } else if (c == '+') {
                c = ' ';
            }
            if (count < MAX_FIELD) {
                out.append((char) c);
                count = count + 1;
            }
            i = i + 1;
        }
        return i < length ? i + 1 : length;
    }

    private static int hexValue(int digit) {
        if (digit >= '0' && digit <= '9') {
            return digit - '0';
        }
        if (digit >= 'a' && digit <= 'f') {
            return digit - 'a' + 10;
        }
        if (digit >= 'A' && digit <= 'F') {
            return digit - 'A' + 10;
        }
        return 0;
    }

    private static void printLocalIP() {
        int[] ip = new int[4];
        Wifi.localIP(ip);
        Serial.print("Connected. IP: ");
        Serial.print(ip[0]);
        Serial.print(".");
        Serial.print(ip[1]);
        Serial.print(".");
        Serial.print(ip[2]);
        Serial.print(".");
        Serial.println(ip[3]);
    }
}
