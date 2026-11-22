---
title: "HTTP server"
description: "Serving HTTP and HTTPS requests from a Juno program with the JDK's com.sun.net.httpserver API: contexts, exchanges, request headers and bodies, streaming and HTTPS."
layout: page
---

A Juno program serves HTTP and HTTPS the way a JDK program does, with `com.sun.net.httpserver`: create an `HttpServer`
or `HttpsServer`, register a handler per path with `createContext`, and `start()` it. Juno has no other server API.
Connect to Wi-Fi first, since the server listens on the board's network address. The
[Board features](../../../features/board-features) page lists which boards serve plain HTTP and which serve HTTPS.

The board's server is a bounded subset of the JDK's: it serves one connection at a time, on a virtual thread that
`start()` launches, so the program's own `main` keeps running.

## The API

| Class | Methods | Notes |
|---|---|---|
| `HttpServer` | `create(InetSocketAddress, int)`, `createContext(String, HttpHandler)`, `start()` | The backlog is ignored. A context matches by path prefix; the longest prefix wins. |
| `HttpsServer` | the same, plus `setHttpsConfigurator(HttpsConfigurator)` | Needs a configurator before `start()`. |
| `HttpsConfigurator` | `HttpsConfigurator(SSLContext)`, `getSSLContext()` | The context's key manager holds the server's certificate and key. |
| `HttpExchange` | `getRequestMethod()`, `getRequestURI()`, `getRequestHeaders()`, `getRequestBody()`, `getResponseHeaders()`, `sendResponseHeaders(int, long)`, `getResponseBody()` | One exchange per request; close the response body to end it. |
| `Headers` | `getFirst(String)` on request headers, `set(String, String)` on response headers | The response sends only `Content-Type`, with any value. |

Use `java.net.HttpURLConnection`'s constants, such as `HTTP_OK` and `HTTP_NOT_FOUND`, for the status codes:
`javac` copies them into the program, so they cost nothing on the board.

## Creating REST APIs

Register one handler per route, then `start()` the server. Each handler sets the content type, sends the status and
length with `sendResponseHeaders`, writes the body and closes it:

```java
HttpServer server = HttpServer.create(new InetSocketAddress(80), 4);
server.createContext("/api/status", exchange -> send(exchange, HTTP_OK, "{\"led\":\"off\"}"));
server.createContext("/", exchange -> send(exchange, HTTP_NOT_FOUND, "{\"title\":\"not found\"}"));
server.start();

static void send(HttpExchange exchange, int status, String body) throws IOException {
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length());
    try (OutputStream output = exchange.getResponseBody()) {
        for (int index = 0; index < body.length(); index++) {
            output.write(body.charAt(index));
        }
    }
}
```

`sendResponseHeaders` sends the status line and headers at once. A length above zero becomes `Content-Length`, and
`-1` means no body. The body's bytes go to the client as they are written, so a response can be as large as the
program can produce.

## Request headers and bodies

`getRequestHeaders().getFirst(name)` reads one request header, matched case-insensitively with spaces around the
value trimmed, or returns `null` when the request has none. Only the first 384 bytes of the request's header lines
are kept, and a value is truncated to 128 bytes.

`getRequestBody()` is an `InputStream` over the request's body. Its first 512 bytes arrive with the request; reading
past them takes the rest from the client, waiting up to five seconds for it, so a body can be larger than any buffer the
board holds. Compare the declared `Content-Length` header with the size your handler accepts before reading. A server
that never calls these two methods spends no memory on them.

## Streaming

Pass a length of `0` to `sendResponseHeaders` for a body of unknown length, such as an MJPEG camera stream: the body
then ends when the connection closes. A write throws `IOException` once the client has gone, which ends the stream:

```java
exchange.getResponseHeaders().set("Content-Type", "multipart/x-mixed-replace; boundary=frame");
exchange.sendResponseHeaders(HTTP_OK, 0);
try (OutputStream output = exchange.getResponseBody()) {
    while (true) {
        int length = camera.capture(frame);
        // write one part: its headers, then frame[0..length)
    }
} catch (IOException closed) {
    // the viewer disconnected
}
```

## HTTPS

`HttpsServer` serves HTTPS with a certificate and its private key, loaded the JDK way: a `CertificateFactory` reads the
certificate, a `KeyFactory("EC")` turns the key into a `PrivateKey`, and an in-memory `KeyStore` and `KeyManagerFactory`
hand both to an `SSLContext`. The key may be SEC1 (`BEGIN EC PRIVATE KEY`) or PKCS#8 (`BEGIN PRIVATE KEY`): the board's
TLS stack reads both. Use it on the boards whose TLS server support is listed on the
[Board features](../../../features/board-features) page; the UNO R4 WiFi has none.

### Creating the certificate

Create the pair on your computer with `openssl`. This makes an EC P-256 key and a certificate valid for ten years, for
the address clients will connect to:

```bash
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 3650 \
  -subj /CN=<cert-ip> -addext subjectAltName=IP:<cert-ip> -keyout server.key -out server.crt
```

`server.key` is the private key. Keep it private: anyone who has it can impersonate the server. `server.crt` is the
public certificate that clients trust. A certificate signed by your own CA works the same way.

### Putting the certificate on the SD card

Copy both files, unchanged, to the root of the microSD card next to `application.properties`:

```text
microSD card/
├── application.properties   wifi.ssid and wifi.password
├── server.crt
└── server.key
```

The program reads both files at startup and builds its `SSLContext`; the server parses the identity in `start()` and
wipes the key, so the program does not hold it afterwards. On the Portenta H7 the
[`Mailbox`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/mailbox/server/Mailbox.java)
server reads them from the root of the Hat Carrier's microSD card.

### Testing the server

Find the board's address on the serial monitor, then call it from a computer that trusts the certificate. Pass
`--cacert` the CA's certificate when a CA signed `server.crt`:

```bash
curl --cacert server.crt https://<cert-ip>/api/status
```

When DHCP gives the board another address than the certificate names, keep the check by sending the certificate's
address to the board's: `--connect-to <cert-ip>:443:<address>:443`.

## Memory

The server's dispatch thread needs a larger carrier stack than the UNO R4 WiFi's default, so build a server for the R4
with `-Djuno.Xss=5120 -Djuno.Xmx=4096`. Check the build's `Arena:` line and `JUNO-RISK-002` before flashing.

## Examples

- [`NanoHttpServerSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/NanoHttpServerSD.java)
  and [`NanoHttpsServerSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/NanoHttpsServerSD.java)
  serve `GET /api/status` and `POST /api/led?state=on|off` over HTTP and HTTPS on the Nano ESP32, with the Wi-Fi
  credentials and the certificate on the SD card. The Cardputer, ESP32-CAM, UNO R4 WiFi and Portenta H7 have the same
  pair or its HTTP half.
- [`HttpServerHeader`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/HttpServerHeader.java)
  greets the caller by a request header.
- [`HttpServerBacklight`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/HttpServerBacklight.java)
  handles `POST` with an LCD Keypad Shield attached.
- [`AiThinkerEsp32CamStream`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/AiThinkerEsp32CamStream.java)
  streams the camera as MJPEG.
