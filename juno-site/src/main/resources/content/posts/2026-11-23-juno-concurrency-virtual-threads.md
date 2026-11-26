---
title: "Juno concurrency: virtual threads, structured concurrency and real limits"
description: "How Juno runs virtual threads on small boards, what StructuredTaskScope adds, and how to bound concurrent HttpClient requests without exhausting the board."
date: 2026-11-23
layout: post
link: /blog/2026-11-23-juno-concurrency-virtual-threads/
tags: [concurrency, http]
blogVisible: true
---

Java concurrency normally assumes a large operating system, a thread scheduler and plenty of memory. Juno brings a
useful part of that model to microcontrollers: virtual threads, locks, `ScopedValue` and
`StructuredTaskScope`. The API looks familiar, but the limits are different. A board may have only one or two native
execution contexts, a fixed Java arena and a small number of native task slots.

The important distinction is this:

- A **virtual thread** is a Java task and its state. It is cheap because it does not permanently own a full native
  stack on every board.
- A **carrier** is the native execution context that runs a virtual thread. A carrier has a real stack.
- A **task slot** is a runtime position for a thread on a dual-carrier port. Its native stack comes from the board's
  native heap, not from the Java arena.

This article explains how those pieces fit together and uses parallel HTTP requests to show why “how many threads can I
create?” is not the same question as “how many requests can this board handle safely?”

> This is the practical companion to the [Memory model](../../features/memory-model) and [HTTP client](../../api/internet/httpclient)
> reference pages.

## Virtual threads on a microcontroller

Juno does not create an operating-system thread for every Java virtual thread. A virtual thread runs on a carrier until
it reaches a supported blocking point such as `sleep`, waiting for a lock or waiting for a structured subtask. The
runtime then parks it and schedules another task.

On the UNO R4 WiFi, there is one Juno carrier. The runtime copies a parked virtual thread's live frames into a
continuation chunk in the arena, then reuses the carrier stack for another thread. The continuation is Java memory, so
deeply nested or blocked work consumes arena space even though it does not reserve another native stack.

On dual-carrier boards, two native execution contexts can run Java at the same time. Each active task slot has a native
stack, and many virtual threads can take turns through those slots. Parked tasks still occupy their scheduler slots and
retain their Java state. The native stack is outside the Java arena, but it still competes with Wi-Fi, TLS, display,
camera and other board libraries for RAM.

## What structured concurrency adds

`StructuredTaskScope` gives a group of virtual threads a lexical lifetime. The parent owns the subtasks inside the
`try` block: it forks them, joins them, reads their results and closes the scope. Juno implements the Java 27 preview
API, so the program must be compiled with preview support. With the default joiner, the first failed subtask cancels
its siblings and `join()` reports the failure; a normal `join()` only means that the subtasks completed, not that an
HTTP endpoint returned a 2xx status. Check the status inside each callable when that distinction matters.

```java
try (var scope = StructuredTaskScope.open()) {
    var weather = scope.fork(() -> readWeather());
    var time = scope.fork(() -> readTime());
    scope.join();
    show(weather.get(), time.get());
}
```

The scope does not make the work free. On 32-bit targets, the compiler estimates a 32-byte base scope payload; the
subtask list, thread handles, allocation headers and task-specific state add more. Captured lambdas, request objects,
response strings and parked continuations also add to the peak. When the scope closes, those objects become eligible for
the next collection only when the program retains no references to them.

That is still a valuable memory property. A forgotten unstructured thread can keep its handle, captures and continuation
alive for the rest of the program. A structured scope gives the compiler and runtime a clear lifetime to analyze.

## How many virtual threads can each board run?

There are two different limits: the maximum number of active runtime slots, and the practical number that fits alongside
the arena and native libraries.

| Board | Default carriers | Active thread slots | HTTP leases | Default stack | Java arena default |
|---|---:|---:|---:|---:|---:|
| UNO R3 | 1 AVR scheduler | 4 including `main`* | Unsupported | 512 B per extra task | 1 KiB |
| UNO R4 WiFi | 1 | Arena-bounded | 2 | 2 KiB carrier | 8 KiB |
| Nano ESP32 | 2 | 16 including `main` | 4 | 8 KiB per used slot | 64 KiB |
| ESP32-CAM | 2 | 16 including `main` | 4 | 8 KiB per used slot | 64 KiB |
| M5Stack Cardputer | 2 | 16 including `main` | 4 | 8 KiB per used slot | 64 KiB |
| Raspberry Pi Pico 2 W | 2 | 16 including `main` | 2 | 4 KiB per used slot | 64 KiB |
| Portenta H7 | 2: M7 + M4 | 16 including `main` | 4 | 8 KiB per used slot | 64 KiB |

\* The UNO R3's four-slot value is an implementation ceiling, not a promise that four tasks fit: three extra 512-byte
task stacks plus the default 1 KiB arena already exceed the board's 2 KiB SRAM budget. `StructuredTaskScope` and
`java.net.http` are unsupported on this board.

The dual-carrier count is a runtime slot limit, not a recommended request count. A slot can be reused after its thread
finishes, so a program may process a long sequence of work in batches. The UNO R4 can also process many short-lived
virtual threads over time, but the arena must hold the live handles, captures and parked continuations at the same
moment.

The compiler's runtime configuration reports the carrier count and stack size. The `Arena: estimated / capacity` line
and risk findings are more useful than multiplying a thread count by a single headline number. Native allocations are
not included in the Java arena estimate.

## The HTTP rule: bound concurrency, not just thread creation

Juno's `HttpClient.send` is synchronous: the request runs to completion in the virtual thread that calls `send`. That
makes virtual threads a natural way to overlap several network waits, but every concurrent request also needs:

- a live virtual-thread handle and its captured state;
- a request and response representation;
- a network/TLS connection and native buffers;
- carrier stack or continuation space;
- response memory if using `BodyHandlers.ofString()`.

There is one transport limit that is easy to miss. It is not a limit of two Java `HttpClient` objects: each client owns
its own configuration and response buffers, so creating multiple clients is possible but wasteful. The runtime instead
uses a small pool of HTTP transport leases:

- **UNO R4 WiFi and Raspberry Pi Pico 2 W:** at most **2 HTTP requests in flight**;
- **Nano ESP32, ESP32-CAM, Cardputer and Portenta H7:** at most **4 HTTP requests in flight**;
- **UNO R3:** `java.net.http` is unsupported.

If more virtual threads call `send` at once, the extra requests wait for a lease. They still consume thread, request and
possibly response memory while waiting, so the lease pool is a transport ceiling, not a recommendation to create that
many clients or tasks.

So this is risky on a small board:

```java
for (var uri : URIS) {
    Thread.startVirtualThread(() -> {
        try {
            client.send(request(uri), BodyHandlers.ofString());
        } catch (java.io.IOException ex) {
            Serial.println("HTTP I/O failed");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    });
}
```

It creates unstructured work, has no completion boundary and allows all endpoints to consume memory at once. The
following pattern is deliberately bounded. It launches three requests in one structured batch, while giving each
concurrent worker its own reusable client and request. Juno's client stores mutable response state, so overlapping
`send()` calls must not share one client instance:

```java
import io.github.jabrena.juno.hardware.serial.Serial;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.StructuredTaskScope;

final class BoundedHttpFanOut {
    private static final HttpClient WEATHER_CLIENT = HttpClient.newHttpClient();
    private static final HttpClient TIME_CLIENT = HttpClient.newHttpClient();
    private static final HttpClient STATUS_CLIENT = HttpClient.newHttpClient();
    private static final URI WEATHER = URI.create("https://api.open-meteo.com/v1/forecast?latitude=40.4&longitude=-3.7");
    private static final URI TIME = URI.create("https://worldtimeapi.org/api/timezone/Etc/UTC");
    private static final URI STATUS = URI.create("https://example.org/status");

    private static String get(HttpClient client, URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP status " + response.statusCode());
        }
        return response.body();
    }

    static void readBatch() throws Exception {
        try (var scope = StructuredTaskScope.open()) {
            var weather = scope.fork(() -> get(WEATHER_CLIENT, WEATHER));
            var time = scope.fork(() -> get(TIME_CLIENT, TIME));
            var status = scope.fork(() -> get(STATUS_CLIENT, STATUS));
            scope.join();

            Serial.println(weather.get().length());
            Serial.println(time.get().length());
            Serial.println(status.get().length());
        }
    }
}
```

This example has three concurrent requests, not sixteen. On the UNO R4 and Pico 2 W, all three tasks can be live, but
only two can hold an HTTP transport lease at once; the third waits while still occupying a task slot and retaining its
state. On the ESP32 boards and Portenta H7, all three fit under the four-lease transport limit. The runtime's 16-slot
limit does not make sixteen simultaneous TLS exchanges a safe workload.

For a memory-sensitive board, use `BodyHandlers.ofByteArray()` when the status or a bounded prefix is enough. Juno's
handler reuses a 384-byte buffer owned by that client and does not allocate a response string for every request. Consume
the bytes inside the owning worker: the next send through that client overwrites the buffer, and longer bodies are
truncated. With `ofString()`, the client can retain its latest response until it is replaced or the client becomes
unreachable.

## Choosing a safe batch size

Start with one request and measure the generated program. Then increase the batch size one request at a time while
watching the arena estimate, the final linker report and the board's serial output. A useful progression is:

1. Build with the board's default `juno.Xmx` and `juno.Xss`.
2. Use one structured HTTP task and confirm the response fits.
3. Add a second task, then a third, keeping each worker's client, request and response buffer reusable where possible.
4. Set a request timeout, but remember that waiting for an HTTP lease has its own wait path; a request timeout does not
   guarantee that a queued task immediately releases its slot and buffers.
5. Increase `juno.Xmx` only after accounting for native RAM; increase `juno.Xss` when the compiler's stack analysis or
   the call depth requires it.

For the UNO R4, a batch of two or three small responses may be reasonable, but a large HTTPS response can exhaust the
arena with only one request. For the 64 KiB dual-carrier boards, the 16-slot runtime limit is usually less important
than Wi-Fi/TLS buffers, response sizes and the board's native heap. Portenta H7 adds another consideration: M4-originated
calls that need Mbed libraries make an M4-to-M7 round trip, so a large HTTP fan-out may gain concurrency but also add
cross-core coordination.

## The practical model

Virtual threads make waiting cheap; they do not make network buffers, response bodies or native stacks free. Structured
concurrency makes the lifetime of concurrent work explicit; it does not remove the work's memory cost.

For Juno programs, the reliable pattern is:

- allocate the `HttpClient`, requests and reusable buffers at startup;
- use structured scopes so every batch has a clear end;
- admit work before `fork()` and cap the number of in-flight requests at or below the board's HTTP lease limit;
- prefer bounded byte handlers when a full response is unnecessary;
- check arena estimates and native linker/RAM reports together;
- treat the first successful request as a starting point, not a concurrency benchmark.

That is how Java concurrency becomes useful on a microcontroller: not by pretending the board has a server-sized thread
pool, but by making the amount of work visible, bounded and testable.
