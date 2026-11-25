package io.github.jabrena.juno.api.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.jabrena.juno.ai.DecisionModel;
import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * Serves a browser game whose computer moves are selected by the exhaustive JEV1 tic-tac-toe model, linked into the
 * firmware at build time. The board prints its address on Serial; open it in a browser on the same network.
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.api.ai.TicTacToeWebGame -Djuno.board=arduino-nano-esp32 \
 *   -Djuno.dm.model=../juno-ai/juno-models/src/main/resources/dm/TIC_TAC_TOE.JEV
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class, AiThinkerEsp32CamBoard.class, ArduinoPortentaH7Board.class})
public final class TicTacToeWebGame {
    private static final int[] STATE = new int[4520];
    private static final int[] LEGAL_ACTIONS = new int[9];
    private static final int[] IP = new int[4];
    private static final DecisionModel MODEL = MCU.getDecisionModel();
    private static final byte[] RESPONSE = new byte[48];
    private static final byte[] RESOURCE_BUFFER = new byte[512];
    private static int xMask;
    private static int oMask;
    private static boolean finished;

    private TicTacToeWebGame() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("JEV web game starting");
        ModelStatus status = MODEL.begin();
        if (status != ModelStatus.OK) {
            Serial.print("JEV web game could not load its model: ");
            Serial.println(status.ordinal());
            return;
        }
        computerMove();
        Wifi wifi = MCU.getWifi();
        SecretStore secrets = MCU.getSecretStore();
        String ssid = secrets.getString("wifi.ssid");
        String password = secrets.getString("wifi.password");
        if (ssid == null || password == null) {
            Serial.println("JEV web game needs wifi.ssid and wifi.password in the Secret Store");
            return;
        }
        Serial.println("JEV web game connecting");
        wifi.begin(ssid, password);
        while (wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.print("WiFi status: ");
            Serial.println(wifi.status());
            Delay.millis(1000);
        }
        Serial.println("WiFi connected");
        HttpServer server = HttpServer.create(new InetSocketAddress(80), 2);
        server.createContext("/api/state", TicTacToeWebGame::state);
        server.createContext("/api/move", TicTacToeWebGame::move);
        server.createContext("/api/reset", TicTacToeWebGame::reset);
        server.createContext("/jev/tic-tac-toe.css", TicTacToeWebGame::style);
        server.createContext("/jev/tic-tac-toe.js", TicTacToeWebGame::script);
        server.createContext("/", TicTacToeWebGame::page);
        server.start();
        // Repeat the address so a monitor attached after boot still finds the game.
        while (true) {
            wifi.localIP(IP);
            Serial.print("JEV tic-tac-toe web game: http://");
            Serial.print(IP[0]); Serial.print("."); Serial.print(IP[1]); Serial.print("."); Serial.print(IP[2]);
            Serial.print("."); Serial.print(IP[3]); Serial.println("/");
            Delay.millis(10000);
        }
    }

    private static void page(HttpExchange exchange) throws IOException {
        try (InputStream resource = TicTacToeWebGame.class.getResourceAsStream("/jev/tic-tac-toe.html")) {
            sendResource(exchange, resource, "text/html; charset=utf-8");
        }
    }

    private static void style(HttpExchange exchange) throws IOException {
        try (InputStream resource = TicTacToeWebGame.class.getResourceAsStream("/jev/tic-tac-toe.css")) {
            sendResource(exchange, resource, "text/css; charset=utf-8");
        }
    }

    private static void script(HttpExchange exchange) throws IOException {
        try (InputStream resource = TicTacToeWebGame.class.getResourceAsStream("/jev/tic-tac-toe.js")) {
            sendResource(exchange, resource, "text/javascript; charset=utf-8");
        }
    }

    private static void sendResource(HttpExchange exchange, InputStream resource, String contentType) throws IOException {
        if (resource == null) {
            send(exchange, 404, "text/plain; charset=utf-8", "resource not found");
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, resource.available());
        try (OutputStream output = exchange.getResponseBody()) {
            int count;
            while ((count = resource.read(RESOURCE_BUFFER, 0, RESOURCE_BUFFER.length)) >= 0) {
                if (count > 0) output.write(RESOURCE_BUFFER, 0, count);
            }
        }
    }

    private static void state(HttpExchange exchange) throws IOException {
        int length = 0;
        length = append(length, "{\"board\":\"");
        for (int cell = 0; cell < 9; cell++) {
            RESPONSE[length++] = (byte) ((xMask & (1 << cell)) != 0 ? 'X' : (oMask & (1 << cell)) != 0 ? 'O' : '-');
        }
        length = append(length, "\",\"status\":\"");
        length = append(length, terminal(xMask) ? "x" : terminal(oMask) ? "o" : finished ? "draw" : "turn");
        length = append(length, "\"}");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(RESPONSE, 0, length);
        }
    }

    private static int append(int length, String text) {
        for (int index = 0; index < text.length(); index++) RESPONSE[length++] = (byte) text.charAt(index);
        return length;
    }

    private static void move(HttpExchange exchange) throws IOException {
        int action = exchange.getRequestBody().read() - '0';
        if (finished || !"POST".equals(exchange.getRequestMethod()) || action < 0 || action > 8
                || ((xMask | oMask) & (1 << action)) != 0) {
            send(exchange, 400, "application/json", "{\"error\":\"invalid move\"}");
            return;
        }
        oMask |= 1 << action;
        finished = terminal(oMask) || (xMask | oMask) == 511;
        if (!finished) computerMove();
        state(exchange);
    }

    private static void reset(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json", "{\"error\":\"POST required\"}");
            return;
        }
        xMask = 0;
        oMask = 0;
        finished = false;
        computerMove();
        state(exchange);
    }

    private static void computerMove() {
        int feature = featureIndex(xMask, oMask);
        STATE[feature] = 1;
        int count = 0;
        for (int action = 0; action < 9; action++) if (((xMask | oMask) & (1 << action)) == 0) LEGAL_ACTIONS[count++] = action;
        int action = MODEL.evaluate(STATE, LEGAL_ACTIONS);
        STATE[feature] = 0;
        if (action >= 0) xMask |= 1 << action;
        finished = terminal(xMask) || (xMask | oMask) == 511;
    }

    private static int featureIndex(int x, int o) {
        int code = 0;
        for (int cell = 8; cell >= 0; cell--) {
            code = code * 3 + ((x & (1 << cell)) != 0 ? 1 : (o & (1 << cell)) != 0 ? 2 : 0);
        }
        int low = 0;
        int high = TicTacToeIndexTable.ENTRIES - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int entry = hex(middle * 8);
            if (entry == code) return hex(middle * 8 + 4);
            if (entry < code) low = middle + 1;
            else high = middle - 1;
        }
        return 0;
    }

    private static int hex(int offset) {
        int value = 0;
        for (int index = 0; index < 4; index++) {
            char digit = TicTacToeIndexTable.VALUE.charAt(offset + index);
            value = (value << 4) + (digit <= '9' ? digit - '0' : digit - 'a' + 10);
        }
        return value;
    }

    private static boolean terminal(int mask) {
        return (mask & 7) == 7 || (mask & 56) == 56 || (mask & 448) == 448 || (mask & 73) == 73
                || (mask & 146) == 146 || (mask & 292) == 292 || (mask & 273) == 273 || (mask & 84) == 84;
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length());
        try (OutputStream output = exchange.getResponseBody()) {
            for (int index = 0; index < body.length(); index++) output.write(body.charAt(index));
        }
    }
}
