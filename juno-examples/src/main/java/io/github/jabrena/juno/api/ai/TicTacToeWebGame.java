package io.github.jabrena.juno.api.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.jabrena.juno.ai.DecisionModel;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/** Serves a browser game whose computer moves are selected by the exhaustive JEV model. */
@Board(ArduinoNanoEsp32Board.class)
public final class TicTacToeWebGame {
    private static final int[] STATE = new int[4520];
    private static final int[] LEGAL_ACTIONS = new int[9];
    private static final int[] IP = new int[4];
    private static final DecisionModel MODEL = MCU.getDecisionModel();
    private static final String PAGE = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Juno JEV Tic-tac-toe</title>
            <style>body{font:18px system-ui;max-width:28rem;margin:2rem auto;text-align:center}button{font-size:2rem;width:5rem;height:5rem;margin:2px}#msg{min-height:2rem}</style>
            <h1>Juno JEV</h1><p>Play O against the Nano ESP32 model.</p><div id="board"></div><p id="msg"></p>
            <script>const b=document.querySelector('#board'),m=document.querySelector('#msg');function draw(s){b.innerHTML='';s.board.forEach((v,i)=>{let x=document.createElement('button');x.textContent=v||' ';x.onclick=()=>fetch('/api/move',{method:'POST',body:''+i}).then(r=>r.json()).then(draw);b.append(x)});m.textContent=s.message}fetch('/api/state').then(r=>r.json()).then(draw)</script>
            """;
    private static int xMask;
    private static int oMask;
    private static boolean finished;

    private TicTacToeWebGame() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("JEV web game starting");
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
        while (wifi.status() != Wifi.STATUS_CONNECTED) Delay.millis(500);
        HttpServer server = HttpServer.create(new InetSocketAddress(80), 2);
        server.createContext("/api/state", TicTacToeWebGame::state);
        server.createContext("/api/move", TicTacToeWebGame::move);
        server.createContext("/", TicTacToeWebGame::page);
        server.start();
        wifi.localIP(IP);
        Serial.print("JEV tic-tac-toe web game: http://");
        Serial.print(IP[0]); Serial.print("."); Serial.print(IP[1]); Serial.print("."); Serial.print(IP[2]);
        Serial.print("."); Serial.print(IP[3]); Serial.println("/");
        while (true) Delay.millis(1000);
    }

    private static void page(HttpExchange exchange) throws IOException {
        send(exchange, 200, PAGE);
    }

    private static void state(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder("{\"board\":[");
        for (int cell = 0; cell < 9; cell++) {
            if (cell > 0) json.append(',');
            json.append((xMask & (1 << cell)) != 0 ? "\"X\"" : (oMask & (1 << cell)) != 0 ? "\"O\"" : "null");
        }
        json.append("],\"message\":\"").append(finished ? "game over" : "your turn").append("\"}");
        send(exchange, 200, json.toString());
    }

    private static void move(HttpExchange exchange) throws IOException {
        int action = exchange.getRequestBody().read() - '0';
        if (finished || !"POST".equals(exchange.getRequestMethod()) || action < 0 || action > 8
                || ((xMask | oMask) & (1 << action)) != 0) {
            send(exchange, 400, "{\"error\":\"invalid move\"}");
            return;
        }
        oMask |= 1 << action;
        finished = terminal(oMask) || (xMask | oMask) == 511;
        if (!finished) computerMove();
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
        int code = (x << 9) | o;
        for (int offset = 0; offset < TicTacToeIndexTable.VALUE.length(); offset += 8) {
            if (hex(offset) == code) return hex(offset + 4);
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

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length());
        try (OutputStream output = exchange.getResponseBody()) {
            for (int index = 0; index < body.length(); index++) output.write(body.charAt(index));
        }
    }
}
