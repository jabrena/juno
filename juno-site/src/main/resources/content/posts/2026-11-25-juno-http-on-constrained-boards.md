---
title: "HTTP on constrained boards: making Juno HttpClient predictable"
description: "Understand Juno's HttpClient transport leases, reusable buffers, response limits and safe batching on small boards."
date: 2026-11-23
layout: post
link: /blog/2026-11-25-juno-http-on-constrained-boards/
tags: [http, memory]
blogVisible: true
---

`HttpClient` makes a network request look like ordinary Java, but an HTTPS exchange consumes much more than a Java
object. It needs a transport lease, native network and TLS buffers, request state, a response buffer and a virtual
thread or carrier continuation.

## The important limits

Juno does not impose a universal limit of two `HttpClient` objects. Each client owns configuration and response state.
The runtime instead limits concurrent HTTP transport exchanges:

| Board family | Maximum HTTP exchanges in flight |
|---|---:|
| UNO R4 WiFi | 2 |
| Nano ESP32, ESP32-CAM, Cardputer, Portenta H7, Raspberry Pi Pico 2 W | 4 |
| UNO R3 | Unsupported |

The limit comes from each board's network stack, not from its number of cores: how many sockets it can keep open and
whether their TLS buffers fit in memory at once. Each value was measured with the `HttpLatencyBenchmark` and
`HttpsLatencyBenchmark` examples, which time four requests to an endpoint that answers after one second. On a Pico 2 W,
for example, four plain HTTP requests in flight finish in about 1.6 s instead of 2.7 s with two, while HTTPS barely
changes, because each TLS handshake is mostly CPU work. The UNO R4 WiFi keeps two: its Wi-Fi radio is a separate
ESP32-S3 modem that the board reaches over a serial link.

If every lease is busy, another virtual thread waits. That waiting task still occupies a scheduler slot and retains its
request and captured state. The lease count is therefore a transport ceiling, not a recommendation for a matching number
of large tasks.

## Reuse clients, but do not share them concurrently

Create clients during startup and give each overlapping worker its own client. A Juno client stores mutable response
state, so concurrent `send()` calls on one instance can overwrite status, headers or the latest body.

```java
static final HttpClient WEATHER_CLIENT = HttpClient.newHttpClient();
static final HttpClient TIME_CLIENT = HttpClient.newHttpClient();
static final URI WEATHER_URI = URI.create("https://example.org/weather");
static final URI TIME_URI = URI.create("https://example.org/time");
static final HttpRequest WEATHER_REQUEST = HttpRequest.newBuilder(WEATHER_URI).build();
static final HttpRequest TIME_REQUEST = HttpRequest.newBuilder(TIME_URI).build();

static int get(HttpClient client, HttpRequest request) throws Exception {
    var response = client.send(
            request,
            HttpResponse.BodyHandlers.ofByteArray());
    if (response.statusCode() / 100 != 2) {
        throw new IllegalStateException("HTTP " + response.statusCode());
    }
    // Consume the bytes needed by this worker before the next send reuses the buffer.
    return response.statusCode();
}
```

The example returns a status code while the worker owns the reusable response buffer. On Juno, `body().length` is the
fixed 384-byte buffer capacity, not the number of meaningful bytes received. The next request through that client
overwrites the buffer, so do not retain the array as if it were an immutable snapshot. In a real worker, parse or copy
the required value before returning.

## Choose the body handler deliberately

`ofByteArray()` uses Juno's reusable 384-byte response buffer. It is suitable for a status, a short sensor payload or a
bounded prefix. Longer bodies are truncated. `ofString()` is convenient when the complete text is small, but it creates
and retains a response string until the client replaces it or becomes unreachable.

The right pattern is to parse or copy only the value needed by the application, then release the larger response before
starting the next batch.

## Timeouts and retries

Always set a timeout for a remote operation, but treat it as a bound on response waiting, not a guarantee that a queued
request immediately releases every native resource. A task waiting for a transport lease has a separate wait path, and
the native timeout setting is shared, so overlapping workers should use the same timeout when predictable timing matters.

Retries should be bounded and reserved for idempotent operations. On a small board, an immediate retry can turn one slow
endpoint into a queue of tasks retaining buffers. Prefer a small batch, a clear failure result and a later retry from the
main control loop.

## A safe starting point

Start with one request. Add a second only after checking the arena estimate and native RAM on the target board. Keep the
batch at or below the board's HTTP lease count when every request must begin without waiting, and measure again when
response sizes, TLS settings or other peripherals change.

The useful question is not “how many clients can I instantiate?” It is “how many complete exchanges can this board keep
alive while the rest of the firmware still has room to work?”
