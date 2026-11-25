package io.github.jabrena.juno.api.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.jabrena.juno.ai.DecisionModel;
import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
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
 * Serves a browser Connect Four game whose computer moves are chosen by the trained JEV1 Connect Four model, linked
 * into the firmware at build time. {@link ConnectFourGame} computes the model's 56 features on the board. The board
 * prints its address on Serial; open it in a browser on the same network.
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.api.ai.ConnectFourWebGame -Djuno.board=arduino-nano-esp32 \
 *   -Djuno.dm.model=../juno-ai/juno-models/src/main/resources/dm/CONNECT_FOUR.JEV
 * }</pre>
 */
@Board(ArduinoNanoEsp32Board.class)
public final class ConnectFourWebGame {
    private static final ConnectFourGame GAME = new ConnectFourGame();
    private static final DecisionModel MODEL = MCU.getDecisionModel();
    private static final int[] IP = new int[4];
    private static final byte[] RESPONSE = new byte[96];
    private static final byte[] RESOURCE_BUFFER = new byte[512];
    private static int modelPlayer = ConnectFourGame.SECOND;
    private static int lastMove = -1;

    private ConnectFourWebGame() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("JEV Connect Four starting");
        ModelStatus status = MODEL.begin();
        if (status != ModelStatus.OK) {
            Serial.print("JEV Connect Four could not load its model: ");
            Serial.println(status.ordinal());
            return;
        }
        Serial.print("model: ");
        Serial.print(MODEL.actions());
        Serial.print(" actions, ");
        Serial.print(MODEL.features());
        Serial.println(" features");
        selfTest();
        Wifi wifi = MCU.getWifi();
        SecretStore secrets = MCU.getSecretStore();
        String ssid = secrets.getString("wifi.ssid");
        String password = secrets.getString("wifi.password");
        if (ssid == null || password == null) {
            Serial.println("JEV Connect Four needs wifi.ssid and wifi.password in the Secret Store");
            return;
        }
        Serial.println("JEV Connect Four connecting");
        wifi.begin(ssid, password);
        while (wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.print("WiFi status: ");
            Serial.println(wifi.status());
            Delay.millis(1000);
        }
        Serial.println("WiFi connected");
        HttpServer server = HttpServer.create(new InetSocketAddress(80), 2);
        server.createContext("/api/state", ConnectFourWebGame::state);
        server.createContext("/api/move", ConnectFourWebGame::move);
        server.createContext("/api/reset", ConnectFourWebGame::reset);
        server.createContext("/jev/connect-four.css", ConnectFourWebGame::style);
        server.createContext("/jev/connect-four.js", ConnectFourWebGame::script);
        server.createContext("/", ConnectFourWebGame::page);
        server.start();
        // Repeat the address so a monitor attached after boot still finds the game.
        while (true) {
            wifi.localIP(IP);
            Serial.print("JEV Connect Four web game: http://");
            Serial.print(IP[0]); Serial.print("."); Serial.print(IP[1]); Serial.print("."); Serial.print(IP[2]);
            Serial.print("."); Serial.print(IP[3]); Serial.println("/");
            Delay.millis(10000);
        }
    }

    /** Prints the model's moves for two known positions, so the serial log shows it reads the board correctly. */
    private static void selfTest() {
        // Yellow has three in column 0: red must block there.
        GAME.reset();
        GAME.play(1); GAME.play(0); GAME.play(2); GAME.play(0); GAME.play(6); GAME.play(0);
        Serial.print("self-test block: column ");
        Serial.println(computerMove());
        // Red has three in column 3: red wins there.
        GAME.reset();
        GAME.play(3); GAME.play(0); GAME.play(3); GAME.play(1); GAME.play(3); GAME.play(6);
        Serial.print("self-test win: column ");
        Serial.println(computerMove());
        GAME.reset();
        modelPlayer = ConnectFourGame.SECOND;
        lastMove = -1;
    }

    private static void page(HttpExchange exchange) throws IOException {
        try (InputStream resource = ConnectFourWebGame.class.getResourceAsStream("/jev/connect-four.html")) {
            sendResource(exchange, resource, "text/html; charset=utf-8");
        }
    }

    private static void style(HttpExchange exchange) throws IOException {
        try (InputStream resource = ConnectFourWebGame.class.getResourceAsStream("/jev/connect-four.css")) {
            sendResource(exchange, resource, "text/css; charset=utf-8");
        }
    }

    private static void script(HttpExchange exchange) throws IOException {
        try (InputStream resource = ConnectFourWebGame.class.getResourceAsStream("/jev/connect-four.js")) {
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
        for (int column = 0; column < ConnectFourGame.COLUMNS; column++) {
            for (int row = 0; row < ConnectFourGame.ROWS; row++) {
                int cell = GAME.cell(column, row);
                RESPONSE[length++] = (byte) (cell == ConnectFourGame.EMPTY ? '-' : cell == modelPlayer ? 'M' : 'H');
            }
        }
        length = append(length, "\",\"status\":\"");
        int winner = GAME.winner();
        length = append(length, winner == modelPlayer ? "lost" : winner != ConnectFourGame.EMPTY ? "won"
                : GAME.isOver() ? "draw" : "turn");
        length = append(length, "\",\"last\":");
        if (lastMove < 0) {
            RESPONSE[length++] = '-';
            RESPONSE[length++] = '1';
        } else {
            if (lastMove >= 10) RESPONSE[length++] = (byte) ('0' + lastMove / 10);
            RESPONSE[length++] = (byte) ('0' + lastMove % 10);
        }
        RESPONSE[length++] = '}';
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
        int column = exchange.getRequestBody().read() - '0';
        if (!"POST".equals(exchange.getRequestMethod()) || GAME.turn() == modelPlayer || !GAME.canPlay(column)) {
            send(exchange, 400, "application/json", "{\"error\":\"invalid move\"}");
            return;
        }
        lastMove = landingCell(column);
        GAME.play(column);
        if (!GAME.isOver()) computerMove();
        state(exchange);
    }

    private static void reset(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json", "{\"error\":\"POST required\"}");
            return;
        }
        boolean modelStarts = exchange.getRequestBody().read() == 'm';
        GAME.reset();
        lastMove = -1;
        modelPlayer = modelStarts ? ConnectFourGame.FIRST : ConnectFourGame.SECOND;
        if (modelStarts) computerMove();
        state(exchange);
    }

    /** Plays the model's move for the player to move and returns its column, or -1. */
    private static int computerMove() {
        int column = MODEL.evaluate(GAME.encode(), GAME.legalActions());
        if (column >= 0) {
            lastMove = landingCell(column);
            GAME.play(column);
        }
        return column;
    }

    /** The cell index, column × 6 + row, a disc dropped into {@code column} lands on. */
    private static int landingCell(int column) {
        int row = 0;
        while (GAME.cell(column, row) != ConnectFourGame.EMPTY) row++;
        return column * ConnectFourGame.ROWS + row;
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length());
        try (OutputStream output = exchange.getResponseBody()) {
            for (int index = 0; index < body.length(); index++) output.write(body.charAt(index));
        }
    }
}
