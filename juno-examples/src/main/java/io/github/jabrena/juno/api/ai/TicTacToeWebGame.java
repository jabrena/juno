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
    private static final byte[] RESPONSE = new byte[48];
    private static final String PAGE = """
            <!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="icon" href="data:,">
            <title>Juno JEV Tic-tac-toe</title>
            <style>
            body{font:18px system-ui,sans-serif;max-width:24rem;margin:2rem auto;padding:0 1rem;text-align:center;background:#f6f7f9;color:#1d2330}
            #board{display:grid;grid-template-columns:repeat(3,1fr);gap:6px;margin:1.5rem auto;max-width:18rem}
            .cell{aspect-ratio:1;font-size:2.6rem;font-weight:700;border:0;border-radius:10px;background:#fff;box-shadow:0 1px 3px #0003;cursor:pointer}
            .cell:disabled{cursor:default}.cell.X{color:#d43f3a}.cell.O{color:#2a6fdb}.cell.win{background:#fff4c2}
            #msg{min-height:1.6rem;font-weight:600}#reset{font-size:1rem;padding:.5rem 1.2rem;border-radius:8px;border:1px solid #9aa3b5;background:#fff;cursor:pointer}
            </style>
            <h1>Juno JEV</h1><p>You play <b style="color:#2a6fdb">O</b>. The Nano ESP32 model plays <b style="color:#d43f3a">X</b>.</p>
            <div id="board"></div><p id="msg">Loading...</p><button id="reset">New game</button>
            <script>
            const board=document.getElementById('board'),msg=document.getElementById('msg'),cells=[];
            const lines=[[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]];
            const text={turn:'Your turn',thinking:'Model is thinking...',x:'The model wins',o:'You win!',draw:'Draw'};
            let busy=false,state={board:'---------',status:'turn'};
            for(let i=0;i<9;i++){const c=document.createElement('button');c.className='cell';c.onclick=()=>play(i);board.append(c);cells.push(c)}
            function draw(){const done=state.status!=='turn';const win=lines.find(l=>l.every(i=>state.board[i]!=='-'&&state.board[i]===state.board[l[0]]))||[];
            cells.forEach((c,i)=>{const v=state.board[i];c.textContent=v==='-'?'':v;c.className='cell '+(v==='-'?'':v)+(win.includes(i)?' win':'');c.disabled=busy||done||v!=='-'});
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
            function play(i){if(busy||state.board[i]!=='-')return;state.board=state.board.slice(0,i)+'O'+state.board.slice(i+1);
            call('/api/move',''+i,s=>s.board[i]==='O')}
            document.getElementById('reset').onclick=()=>call('/api/reset','',s=>s.board.indexOf('O')<0&&s.status==='turn');
            call('/api/state');
            </script>
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
        send(exchange, 200, "text/html; charset=utf-8", PAGE);
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
