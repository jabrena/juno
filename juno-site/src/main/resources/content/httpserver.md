---
title: "HTTP server"
description: "Serving HTTP and HTTPS requests from a Juno program with HttpServer: accept, path, respond and HTTPS."
layout: page
---

`HttpServer` lets a Juno program answer HTTP requests from other devices on the same network. It is a minimal server:
it listens on a port, reads one request at a time and replies to it. Connect to Wi-Fi first, since the server listens on
the board's network address. The [Board features](../board-features) page lists which boards serve plain HTTP and which
serve HTTPS.

## Methods

| Method | Arguments | Purpose |
|---|---|---|
| `begin(int)` | a port | Starts listening for plain HTTP. Call it once, after Wi-Fi connects. |
| `beginTls(int, byte[], int, byte[], int)` | a port, the certificate, its length, the private key and its length | Starts listening for HTTPS with a PEM certificate and key. |
| `accept(byte[], int)` | a body buffer and its capacity | Reads the next request, or returns `-1` at once when no client is waiting. |
| `method()` | none | The method of the request from the last `accept`, such as `GET`. |
| `path()` | none | The path of the request from the last `accept`, such as `/status`. |
| `contentLength()` | none | The request's `Content-Length`, or `-1` when it has none. |
| `header(String, byte[], int)` | a header name, a buffer and its capacity | Copies a request header; returns its length, or `-1` when it is absent. |
| `respond(int, String, String)` | a status, a content type and a body | Sends the reply and closes the connection. |
| `respond(int, String, StringBuilder)` | a status, a content type and a `StringBuilder` | Sends the contents of a `StringBuilder` as the reply, with no size cap. |

`HttpStatus` provides the status codes, such as `OK` (200), `NOT_FOUND` (404) and `BAD_REQUEST` (400).

## Creating REST APIs

Call `accept` once per pass of the loop. It never waits for a client, so the rest of the loop keeps running. When it
returns a value of zero or more, a request has been read: check `path()`, then send exactly one `respond` before the next
`accept`:

```java
HttpServer.begin(80);
byte[] body = new byte[HttpServer.DEFAULT_BODY_BUFFER_SIZE];

while (true) {
    if (HttpServer.accept(body, body.length) >= 0) {
        if (HttpServer.path().equals("/status")) {
            HttpServer.respond(HttpStatus.OK, "text/plain", "online");
        } else {
            HttpServer.respond(HttpStatus.NOT_FOUND, "text/plain", "not found");
        }
    }
}
```

`accept` reads the request body into the buffer, truncated to its length. Check `contentLength()` before parsing a body that
might be larger than the buffer, because `accept` truncates it silently.

## HTTPS

`HttpServer.beginTls` serves HTTPS with a certificate and a private key, both in PEM form. A self-signed pair is enough
for a server on your own network. Clients must trust that certificate themselves, so the usual test is
`curl --cacert server.crt`. Use it on the boards whose TLS support is listed on the [Board features](../board-features)
page.

### Creating the certificate

Create the pair on your computer with `openssl`. This makes an EC P-256 key and a certificate valid for ten years, for
the name `juno.local`:

```bash
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 3650 \
  -subj /CN=juno.local -keyout server.key -out server.crt
```

`server.key` is the private key. Keep it private: anyone who has it can impersonate the server. `server.crt` is the
public certificate that clients trust.

### Putting the certificate on the SD card

On the Nano ESP32 and the Cardputer, copy both files to the root of the microSD card, next to `application.properties`:

```text
microSD card/
├── application.properties   wifi.ssid and wifi.password
├── server.crt
└── server.key
```

The program reads both files at startup into buffers it allocates once, then passes them to `beginTls`. The server parses
them and keeps its own copy, so the program does not hold the key afterwards. Each buffer must hold its whole file, so
keep the PEM text small: the examples use 2048-byte buffers.

On the UNO Q, `QHttpsServer` reads `server.crt` and `server.key` from the root of the Data Logger Shield's microSD card at
startup, so neither file is built into the program or committed to the repository.

### Testing the server

Find the board's address on the serial monitor, then request the status route from a computer that trusts the
certificate:

```bash
curl --cacert server.crt --resolve juno.local:443:<address> https://juno.local/status
```

The `--resolve` option sends the name `juno.local` to the board's address, so the certificate's name matches the request.

## Examples

- [`NanoHttpServerSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/NanoHttpServerSD.java)
  joins the network with credentials from the SD card, prints its address and answers `GET /status` on port 80.
- [`QHttpsServer`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/QHttpsServer.java)
  serves HTTPS on the UNO Q, with the certificate and key built in at build time.
- [`HttpServerBacklight`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/HttpServerBacklight.java)
  demonstrates `POST` handling with an LCD Keypad Shield attached.
