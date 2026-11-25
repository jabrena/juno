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
    private static final String PAGE = """
            <!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="icon" href="data:,">
            <title>Juno JEV Connect Four</title>
            <style>
            body{font:18px system-ui,sans-serif;max-width:30rem;margin:2rem auto;padding:0 1rem;text-align:center;background:#f6f7f9;color:#1d2330}
            #board{display:grid;grid-template-columns:repeat(7,1fr);gap:5px;margin:1.5rem auto;padding:8px;border-radius:12px;background:#2a5bd7}
            .cell{aspect-ratio:1;border:0;border-radius:50%;background:#fff;cursor:pointer;padding:0}
            .cell:disabled{cursor:default}.cell.H{background:#f2c230}.cell.M{background:#d43f3a}.cell.last{box-shadow:inset 0 0 0 4px #1d2330}
            #msg{min-height:1.6rem;font-weight:600}button.new{font-size:1rem;padding:.5rem 1rem;margin:.25rem;border-radius:8px;border:1px solid #9aa3b5;background:#fff;cursor:pointer}
            </style>
            <h1>Juno JEV</h1><p>You play <b style="color:#c59400">yellow</b>. The Nano ESP32 model plays <b style="color:#d43f3a">red</b>.</p>
            <div id="board"></div><p id="msg">Loading...</p>
            <button class="new" id="you">New game: you start</button><button class="new" id="model">New game: model starts</button>
            <script>
            const board=document.getElementById('board'),msg=document.getElementById('msg'),cells=[];
            const text={turn:'Your turn: pick a column',thinking:'Model is thinking...',lost:'The model wins',won:'You win!',draw:'Draw'};
            let busy=false,state={board:'-'.repeat(42),status:'turn',last:-1};
            for(let r=5;r>=0;r--)for(let c=0;c<7;c++){const b=document.createElement('button');b.className='cell';b.onclick=()=>play(c);board.append(b);cells[c*6+r]=b}
            function draw(){const done=state.status!=='turn';
            cells.forEach((b,i)=>{const v=state.board[i];b.className='cell '+(v==='-'?'':v)+(i===state.last?' last':'');b.disabled=busy||done||state.board[Math.floor(i/6)*6+5]!=='-'});
            msg.textContent=busy?text.thinking:text[state.status]}
            const wait=ms=>new Promise(r=>setTimeout(r,ms));
            function request(path,body){return fetch(path,body===undefined?{cache:'no-store'}:{method:'POST',body:body})
            .then(r=>{if(!r.ok)throw new Error('HTTP '+r.status);return r.json()})}
            // The board serves one connection at a time, so a request can fail while it is busy: retry, but reread the
            // board before resending a move or a reset, so neither is ever applied twice.
            async function call(path,body,applied){busy=true;draw();let error;
            for(let attempt=0;attempt<4;attempt++){
            try{if(attempt>0){await wait(700*attempt);if(applied){const s=await request('/api/state');if(applied(s)){state=s;busy=false;draw();return}}}
            state=await request(path,body);busy=false;draw();return}catch(e){error=e}}
            busy=false;draw();msg.textContent='Board busy ('+error.message+'), try again'}
            const discs=s=>s.board.split('').filter(v=>v==='H').length;
            function play(c){if(busy||state.status!=='turn')return;const before=discs(state);call('/api/move',''+c,s=>discs(s)>before)}
            const reset=who=>call('/api/reset',who,s=>discs(s)===0&&s.status==='turn');
            document.getElementById('you').onclick=()=>reset('h');document.getElementById('model').onclick=()=>reset('m');
            call('/api/state');
            </script>
            """;
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
        send(exchange, 200, "text/html; charset=utf-8", PAGE);
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
