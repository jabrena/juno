package io.github.jabrena.juno.api.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.jabrena.juno.ai.LanguageModel;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

/**
 * The web chat with the on-board TNY2 model, shared by the access-point and the Secret Store examples: {@code GET /}
 * serves the chat page, {@code POST /api/chat} with the question as the body streams the answer back one character at
 * a time as the model generates it, and {@code GET /api/status} reports the model. Whatever the network, the model
 * runs on the board.
 */
public final class Tny2WebChat {
    private static final LanguageModel MODEL = MCU.getLanguageModel();
    private static final byte[] QUESTION = new byte[LanguageModel.MAX_QUESTION];
    // The juno-site theme (LEGO Mindstorms RCX colors), inline: on the access point there is no internet for assets.
    private static final String PAGE = """
            <!doctype html><html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1"><title>TINY on Juno</title><style>
            :root{--pop:#ffcd03;--pop-tint:#fffbeb;--pop-line:#ffea99;--blue:#0055bf;--blue-dark:#00449a;
            --black:#1b2a34;--red:#c91a09;--green:#237841;--bg:#fff;--text:#1b2a34;--muted:#5b6b78;--line:#e3e8ec;
            --answer:#fffbeb;--answer-line:#ffea99;--field:#fff}
            @media(prefers-color-scheme:dark){:root{--bg:#171717;--text:#eef2f5;--muted:#9aa8b3;--line:#2c3a44;
            --answer:#2b2000;--answer-line:#5e4700;--field:#1f2a31}}
            *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);
            font:16px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;min-height:100vh;display:flex;
            flex-direction:column}header{background:var(--pop);color:var(--black);box-shadow:0 1px 0 rgba(0,0,0,.12)}
            .bar{max-width:760px;margin:auto;height:4rem;padding:0 16px;display:flex;align-items:center;gap:12px}
            .brand{font-weight:800;font-size:1.25rem;letter-spacing:-.01em}.brick{background:var(--black);color:#fff;
            border-radius:6px;padding:2px 10px;font-weight:800}.status{margin-left:auto;font-size:.85rem;
            display:flex;align-items:center;gap:6px}.dot{width:10px;height:10px;border-radius:50%;background:var(--red)}
            .dot.on{background:var(--green)}main{flex:1;width:100%;max-width:760px;margin:auto;padding:16px;
            display:flex;flex-direction:column;gap:12px}#log{flex:1;display:flex;flex-direction:column;gap:10px}
            .hint{color:var(--muted);font-size:.9rem}.msg{max-width:85%;padding:10px 14px;border-radius:14px;
            white-space:pre-wrap;word-wrap:break-word}.me{align-self:flex-end;background:var(--blue);color:#fff;
            border-bottom-right-radius:4px}.tiny{align-self:flex-start;background:var(--answer);
            border:1px solid var(--answer-line);border-bottom-left-radius:4px;font-family:ui-monospace,Menlo,monospace}
            .tiny.typing::after{content:"\\258b";animation:b 1s steps(1) infinite}@keyframes b{50%{opacity:0}}
            form{display:flex;gap:8px;position:sticky;bottom:0;background:var(--bg);padding:8px 0}
            input{flex:1;min-width:0;background:var(--field);color:var(--text);border:2px solid var(--line);
            border-radius:10px;padding:10px 12px;font:inherit}input:focus{outline:none;border-color:var(--blue)}
            button{background:var(--blue);color:#fff;border:0;border-radius:10px;padding:10px 18px;font:inherit;
            font-weight:700;cursor:pointer}button:hover{background:var(--blue-dark)}button:disabled{opacity:.5;
            cursor:wait}</style></head><body><header><div class="bar"><span class="brick">Juno</span>
            <span class="brand">TINY</span><span class="status"><span id="dot" class="dot"></span>
            <span id="state">connecting</span></span></div></header><main><div id="log">
            <p class="hint">A small language model running on this board, from PSRAM. Ask about Juno and its boards.</p>
            </div><form id="f"><input id="q" maxlength="72" autocomplete="off" placeholder="Ask TINY" autofocus>
            <button id="send">Send</button></form></main><script>
            const $=id=>document.getElementById(id),log=$('log'),q=$('q'),send=$('send');
            fetch('/api/status').then(r=>r.json()).then(s=>{$('dot').classList.toggle('on',s.loaded);
            $('state').textContent=s.loaded?s.layers+' layers, width '+s.width:'model offline'})
            .catch(()=>{$('state').textContent='offline'});
            function bubble(cls,text){const d=document.createElement('div');d.className='msg '+cls;d.textContent=text;
            log.append(d);d.scrollIntoView({block:'end'});return d}
            $('f').onsubmit=async e=>{e.preventDefault();const text=q.value.trim();if(!text)return;q.value='';
            send.disabled=true;bubble('me',text);const out=bubble('tiny typing','');try{
            const r=await fetch('/api/chat',{method:'POST',body:text});const reader=r.body.getReader(),
            d=new TextDecoder();for(;;){const{value,done}=await reader.read();if(done)break;
            out.textContent+=d.decode(value);out.scrollIntoView({block:'end'})}}catch(x){out.textContent='(no answer)'}
            out.classList.remove('typing');send.disabled=false;q.focus()};
            </script></body></html>
            """;

    private Tny2WebChat() {
    }

    /** Adds the chat page and API to {@code server}; start the server afterwards. */
    public static void register(HttpServer server) {
        server.createContext("/api/chat", Tny2WebChat::chat);
        server.createContext("/api/status", Tny2WebChat::status);
        server.createContext("/", Tny2WebChat::page);
    }

    private static void page(HttpExchange exchange) throws IOException {
        send(exchange, HTTP_OK, "text/html; charset=utf-8", PAGE);
    }

    private static void status(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(HTTP_OK, 0);
        try (OutputStream output = exchange.getResponseBody()) {
            writeText(output, MODEL.isLoaded() ? "{\"loaded\":true,\"layers\":" : "{\"loaded\":false,\"layers\":");
            writeNumber(output, MODEL.layers());
            writeText(output, ",\"width\":");
            writeNumber(output, MODEL.width());
            writeText(output, "}");
        }
    }

    /** Streams the answer to the question in the request body as plain text, each character as soon as it exists. */
    private static void chat(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, HTTP_BAD_METHOD, "text/plain", "POST a question");
            return;
        }
        int length = readBody(exchange.getRequestBody());
        if (MODEL.ask(QUESTION, length, 0) != LanguageModel.OK) {
            send(exchange, HTTP_UNAVAILABLE, "text/plain", "The model cannot answer that.");
            return;
        }
        Serial.print("[CHAT] ");
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(HTTP_OK, 0);
        try (OutputStream output = exchange.getResponseBody()) {
            for (int c = MODEL.nextChar(); c != LanguageModel.END; c = MODEL.nextChar()) {
                output.write(c);
                Serial.write(c);
            }
        }
        Serial.println("");
    }

    private static int readBody(InputStream body) throws IOException {
        int length = 0;
        while (length < QUESTION.length) {
            int count = body.read(QUESTION, length, QUESTION.length - length);
            if (count <= 0) {
                break;
            }
            length += count;
        }
        return length;
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length());
        try (OutputStream output = exchange.getResponseBody()) {
            writeText(output, body);
        }
    }

    private static void writeText(OutputStream output, String text) throws IOException {
        for (int i = 0; i < text.length(); i++) {
            output.write(text.charAt(i));
        }
    }

    private static void writeNumber(OutputStream output, int value) throws IOException {
        if (value >= 10) {
            writeNumber(output, value / 10);
        }
        output.write('0' + value % 10);
    }
}
