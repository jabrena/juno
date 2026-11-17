---
title: "HTTP client"
description: "Making HTTP and HTTPS requests with the JDK's java.net.http client: methods, headers, bodies, timeouts, redirects, trust anchors and JSON."
layout: page
---

A Juno program sends HTTP and HTTPS requests with the JDK's own client, `java.net.http.HttpClient`, once the board is
connected to Wi-Fi. The program is written and compiled against the JDK. On the board, Juno runs its own implementation
of the same classes, which fits in the board's memory.

`java.net.http` runs on the UNO R4 WiFi, Nano ESP32, AI Thinker ESP32-CAM, M5Stack Cardputer, Raspberry Pi Pico 2 W and
Portenta H7. The UNO R4 WiFi is the smallest board that can run it, so the compiler rejects it on the UNO WiFi Rev2, the
UNO R3 and the UNO Q.

## Making a GET request

```java
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.open-meteo.com/v1/forecast?latitude=40.4168"
        + "&longitude=-3.7038&current=temperature_2m")).build();

HttpResponse<String> reply = client.send(request, HttpResponse.BodyHandlers.ofString());
Serial.println(reply.statusCode());
Serial.println(Json.view(reply.body()).get("current").get("temperature_2m").asDouble());
```

`send` declares `IOException` and `InterruptedException`, as in the JDK. It throws `IOException` when the request
cannot be sent, and `HttpTimeoutException` when no response arrives in time. The board never interrupts a request.

Create the client and requests once, before a loop, and send the same request as often as needed.

## Requests

- **Methods:** `GET()`, `POST(body)`, `PUT(body)`, `DELETE()` and `HEAD()`. Any other method, such as `PATCH` or
  `QUERY`, goes through `method("PATCH", body)`. Unlike the JDK, `CONNECT` is also accepted. It is sent to the URI's
  `host:port` and reports its status, but opens no tunnel.
- **Headers:** `header`, `headers(...)` and `setHeader`, up to 512 bytes per request. As in the JDK, the client sets
  `Connection`, `Content-Length`, `Expect`, `Host` and `Upgrade` itself, so the builder rejects them.
- **Bodies:** `BodyPublishers.ofString` (up to 512 bytes), `ofByteArray(bytes)`, `ofByteArray(bytes, 0, length)` and
  `noBody()`. `ofByteArray(bytes, 0, length)` sends the first `length` bytes of a buffer the program allocated once.
  Unlike the JDK, its offset must be 0. A body without a `Content-Type` header is sent as `application/json`.

```java
HttpRequest post = HttpRequest.newBuilder(URI.create("https://httpbin.org/post"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"board\":\"juno\"}"))
        .build();
```

## Responses

How the body arrives depends on the body handler:

| Handler | Body | Memory |
|---|---|---|
| `BodyHandlers.ofString()` | A `String` of every byte received, sized to the response. | Allocates one `String` per response. There is no fixed limit, but a body that does not fit in the free memory makes `send` throw an `IOException`. |
| `BodyHandlers.ofByteArray()` | The client's own 384-byte buffer, refilled by every request. A longer body is truncated, and `body().length` is the buffer's size, not the size received. | Allocates nothing per request. |

Use `ofString()` to read a response. Use `ofByteArray()` when only the status matters, or when a page might be larger
than the board's memory: the buffer keeps the first bytes of a page of any size.

The body is not decoded: a runtime `String` holds bytes, so UTF-8 text stays UTF-8.

`headers()` answers `firstValue(name)` and `firstValueAsLong(name)`, with the name in any case:

```java
String date = reply.headers().firstValue("date").orElse("");
```

`uri()` is the URI the response came from: the request's own, or the last one a redirect led to.

## Timeouts and redirects

```java
HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build();
HttpRequest slow = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).build();
```

- **Timeouts:** the board has one timeout for the whole exchange. It is the request's timeout if it has one, else the
  client's `connectTimeout`, else 5 seconds.
- **Redirects:** `NEVER` (the default), `ALWAYS` and `NORMAL` (which never goes from `https` to `http`), with at most
  5 redirects per request. The client follows a redirect only to a scheme that one of the program's own URIs names,
  because only those requests are linked into the program.

## Closing a client

As in the JDK since 21, a client is `AutoCloseable`:

```java
try (HttpClient client = HttpClient.newHttpClient()) {
    client.send(request, HttpResponse.BodyHandlers.ofString());
}
```

A closed client throws `IOException` from `send`. Every request runs to completion in the thread that sends it, so
`isTerminated()` and `awaitTermination(...)` answer at once.

## URIs

`java.net.URI` parses any URI and has no length limit, so a long query string, such as a PostgREST filter, is fine. A URI
can be built at run time, for example from settings on the SD card:

```java
URI uri = URI.create("https://" + host + path);
URI next = uri.resolve("../v2/items?page=2");
Serial.println(next.getPath());
```

It has `getScheme`, `getHost`, `getPort`, `getPath`, `getQuery`, `getFragment`, `getUserInfo`, `getAuthority`, their
`getRaw...` forms, `resolve`, `toString`, `equals` and `hashCode`. Where it differs from the JDK:

- `resolve` follows RFC 3986. A reference that is only a query (`?q`) keeps the base path, and an absolute path loses
  its `.` and `..` segments.
- `new URI(...)` throws `IllegalArgumentException`, not `URISyntaxException`.
- A port above 65535 is rejected.

## Reading JSON responses

`Json.view` reads values straight from a body, with no parsing into objects. Its method names follow JDK 28's
JSON API (JEP 540): `get(String)` steps into an object member, `get(int)` into an array element, and `asInt()`,
`asDouble()` and the other conversions read the value.

```java
JsonView current = Json.view(reply.body()).get("current");
double temperature = current.get("temperature_2m").asDouble();
int humidity = current.get("relative_humidity_2m").asInt();
```

`Json.view` also takes a `byte[]` and the number of bytes in it, which is how `HttpServer` programs read a request
body. It checks the whole document once, before any value is read. It supports objects, arrays, strings, numbers,
booleans and `null`, nested up to 32 levels deep.

JSON needs the UNO R4 WiFi or a larger board. The compiler rejects it on the UNO R3 and the UNO WiFi Rev2.

### Navigating

| JSON | Steps |
|---|---|
| `{"temperature": 21}` | `.get("temperature")` |
| `{"current":{"temperature":21}}` | `.get("current").get("temperature")` |
| `{"users":[{"name":"Ada"}]}` | `.get("users").get(0).get("name")` |
| `[{"id":7}]` | `.get(0).get("id")` |

An index is a plain `int`, so a loop needs no path string:

```java
JsonView solution = Json.view(body).get("puzzle").get("solution");
for (int i = 0; i < solution.size(); i++) {
    int length = solution.get(i).copyString(move);
}
```

Each step returns a new `JsonView`: a 20-byte arena object that the collector reclaims. Keep a view you use more than
once in a local variable. A member name is matched as written; names that the document writes with JSON escapes cannot
be matched yet, and when a name appears twice the first one wins.

### Reading values

| Method | Returns |
|---|---|
| `asInt()` | an `int`, or `0` unless the value is an integral number that fits |
| `asLong()` | a `long`, or `0` unless the value is an integral number that fits |
| `asDouble()` | a `double`, or `0.0` |
| `asBoolean()` | a `boolean`, or `false` |
| `asString()` | the value's raw text as a `String`, or `null` for a missing value, an object or an array |
| `copyString(byte[] out)` | the number of UTF-8 bytes decoded into `out` |
| `size()` | the number of elements of an array or members of an object, or `-1` for any other value |

Nothing throws, unlike JEP 540: conversions return zero or `false` when the value cannot be read, so `asInt() == 0` is
ambiguous by itself. Use `type()`, or `isNumber()`, `isString()`, `isNull()`, `isMissing()` and the other checks,
when the difference matters:

```java
JsonView temperature = Json.view(body).get("current").get("temperature_2m");
int valueType = temperature.type();
if (valueType == Json.TYPE_NUMBER) {
    Serial.println(temperature.asDouble());
} else if (valueType == Json.TYPE_NULL) {
    Serial.println("Temperature is null");
} else if (valueType == Json.TYPE_MISSING) {
    Serial.println("Temperature is missing");
} else if (valueType == Json.TYPE_INVALID) {
    Serial.println("Invalid or truncated JSON");
}
```

The type constants are `TYPE_MISSING`, `TYPE_NULL`, `TYPE_BOOLEAN`, `TYPE_NUMBER`, `TYPE_STRING`, `TYPE_OBJECT`,
`TYPE_ARRAY` and `TYPE_INVALID`. A missing or invalid view stays that way through every later step.

`asString()` returns the text as written, with no escape decoding. That keeps a number such as `21.2` exactly as the
server wrote it. `copyString(out)` decodes escapes such as `\n`, `\"` and `\u00e9`, including surrogate pairs, into the
program's own buffer. It writes at most `out.length` bytes (or the capacity passed to `copyString(out, capacity)`) and
adds no terminating NUL.

## Writing JSON

`JsonWriter` writes JSON into a `byte[]` the program owns, placing the commas and escaping strings. Create it once and
call `reset()` for each body; writing allocates nothing:

```java
byte[] body = new byte[64];
JsonWriter json = new JsonWriter(body);

json.reset().beginObject()
        .name("uptime_ms").value(clock.millis())
        .name("ok").value(true)
        .endObject();
int length = json.length();
```

Output that does not fit the buffer is dropped, and `overflowed()` turns `true`. It also turns `true` for a name
outside an object or an unbalanced `end...` call.

## Interacting with HTTPS endpoints

An `https` URI makes the client use TLS. Before it sends anything, the client checks the server's certificate chain and
host name against the board's trusted root certificates. A certificate that fails the check makes `send` throw an
`IOException`.

### Trusted roots on each board

| Board | Where the trusted roots come from | Own trust anchors (`SSLContext`) | Certificate dates checked |
|---|---|---|---|
| UNO R4 WiFi | The root CA bundle in the ESP32-S3 coprocessor's firmware, which also runs the TLS. Arduino documents how to update it. | Not supported. | Yes, as the coprocessor does it: an expired certificate that chains to a trusted root is rejected (tested on the board). |
| Nano ESP32, ESP32-CAM, M5Stack Cardputer | Mozilla's root certificates, bundled into the program (about 55 KB of flash). | Supported. | Yes: the clock is set over SNTP before each connection. A connection fails if the clock cannot be set. |
| Raspberry Pi Pico 2 W | Mozilla's root certificates, through BearSSL. | Supported. | Yes, after the first HTTPS connection sets the clock over SNTP. |
| Portenta H7 | Juno's root bundle, through Mbed TLS. | Not supported. | Yes: after the handshake, each certificate's dates are compared with the clock, which is set over SNTP first. A connection fails if the clock cannot be set. |

On the UNO R4 WiFi and the Nano ESP32, an expired certificate that chains to a trusted root is rejected, as tested on the board.

### Trusting your own certificate

A program can trust its own certificate, such as the CA of a server on the local network, the way the JDK does it: load
the PEM certificate into a `KeyStore`, turn it into trust managers, and give the client an `SSLContext`:

```java
KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
store.load(null, null);
try (InputStream file = sdCard.open("ca.crt")) {
    store.setCertificateEntry("ca", CertificateFactory.getInstance("X.509").generateCertificate(file));
}
TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
trust.init(store);
SSLContext context = SSLContext.getInstance("TLS");
context.init(null, trust.getTrustManagers(), null);

HttpClient client = HttpClient.newBuilder().sslContext(context).build();
```

The context carries only trust anchors. The board's TLS client does the handshake, and client certificates are not
supported. A program that calls `sslContext(...)` uses its own anchors only: on the ESP32 boards, the bundled Mozilla
roots are then left out of the program. On a board that cannot change its trust at runtime, a request through such a
client fails with an `IOException`.

## Examples

- [`JdkHttpWeather`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/JdkHttpWeather.java)
  reads a temperature over plain HTTP, allocating nothing per request.
- [`JdkHttpRequests`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/JdkHttpRequests.java)
  and [`JdkHttpsRequests`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/JdkHttpsRequests.java)
  send every method, headers, bodies, redirects and timeouts.
- [`JdkHttpUri`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/JdkHttpUri.java)
  sends a 501-character URI and resolves a reference, with a try-with-resources client.
- [`JdkHttpsCertificates`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/JdkHttpsCertificates.java)
  checks that valid certificates are accepted and broken ones rejected.
- [`ParallelHttpsGet`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/ParallelHttpsGet.java)
  makes several requests at the same time with virtual threads.
- [`WeatherTFT`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/weather/WeatherTFT.java)
  shows a weather station that builds its URI at run time.
- [`MailboxCardputer`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/mailbox/client/MailboxCardputer.java)
  trusts its server's own CA through an `SSLContext`.
