---
title: "Internet Access"
description: "Wi-Fi, HTTP and JSON intrinsics for network-connected sketches."
layout: page
---

Juno provides a small, allocation-free Internet stack for the Arduino UNO R4 WiFi. Programs can:

- connect to a Wi-Fi network with [`Wifi`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/net/Wifi.java);
- send plain HTTP or TLS-protected HTTPS `GET`, `POST`, `DELETE`, `PATCH`, and `QUERY` requests with
  [`HttpClient`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/net/http/HttpClient.java),
  where the URL's `http://` or `https://` scheme decides whether the request uses TLS; and
- extract typed values directly from JSON response bytes with
  [`Json`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/net/http/Json.java).

These APIs are compiler intrinsics, like every Juno API — see [docs/APIS.md](../apis) for what
that means, how they're built, and the general rules (compile-time-only `String` arguments,
caller-owned buffers) this guide relies on. This is the end-user reference for using them.

## Requirements and current limits

Wi-Fi, HTTP, and HTTPS require an UNO R4 WiFi entry point:

```java
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;

@Board(ArduinoUnoR4WiFiBoard.class)
public final class InternetExample {
    // ...
}
```

`Json` itself does not touch the network and can parse any local `byte[]`.

The current network layer deliberately stays small:

- request URLs and request bodies must be compile-time strings — except the path of
  `HttpClient.get`'s `byte[]` path overload (see [Runtime request paths](#runtime-request-paths));
- custom request headers, redirects, cookies, and authentication helpers are not available;
- HTTPS uses the root CA bundle installed in the board's WiFi firmware; Juno does not yet accept a
  custom CA certificate from Java source;
- the response status line and headers are consumed but are not exposed to Java code;
- each request has a fixed five-second timeout; and
- the response body may be truncated when it is larger than the supplied buffer.

Prefer `https://` URLs for Internet services. Do not send API tokens, personal data, credentials, or
other secrets to an `http://` URL.

## Supplying Wi-Fi credentials

Do not put Wi-Fi credentials directly in Java source. Either load them at runtime from an SD-card
properties file (see [docs/STORAGE.md](../storage)) or pass compile-time environment-variable reads
to `Wifi.begin` (on the `Wifi` that `ArduinoUno.getWifi()` returns) — see
[docs/APIS.md](../apis#supplying-compile-time-credentials-with-systemgetenv) for the full
mechanism, including the `.env`/`juno-maven-plugin` alternative and its security caveats:

```java
ArduinoUno.getWifi().begin(
        System.getenv("JUNO_WIFI_SSID"),
        System.getenv("JUNO_WIFI_PASSWORD"));
```

The same mechanism can supply a request URL or body when necessary:

```java
int responseBytes = HttpClient.get(
        System.getenv("JUNO_API_URL"),
        response, response.length, headers, headers.length, status);
```

Any value supplied this way is still embedded in the generated firmware. An `https://` URL protects
it in transit; an `http://` URL sends it without encryption.

Unlike HTTP hosts, paths, and bodies, `Wifi.begin` also accepts stable runtime strings returned by
`java.util.Properties.getProperty`, so credentials can remain on removable storage rather than in firmware.

## Connecting to Wi-Fi

Get the board's radio once, at startup: `ArduinoUno.getWifi()` on the boards with onboard Wi-Fi (UNO R4 WiFi,
UNO WiFi Rev2, UNO Q). The UNO R3 has no radio of its own, so the compiler rejects `ArduinoUno.getWifi()`
there; stack the retired [Arduino WiFi Shield](https://docs.arduino.cc/retired/shields/arduino-wifi-shield/)
and take the same `Wifi` from `new ArduinoWifiShield().getWifi()` instead. A unit test passes
`mock(Wifi.class)`.

`Wifi.begin` starts the connection. Poll `Wifi.status()` until it equals
`Wifi.STATUS_CONNECTED`. A bounded retry loop lets the program report or display a failure instead
of waiting forever:

```java
Wifi wifi = ArduinoUno.getWifi();
wifi.begin(
        System.getenv("JUNO_WIFI_SSID"),
        System.getenv("JUNO_WIFI_PASSWORD"));

int attempts = 0;
while (wifi.status() != Wifi.STATUS_CONNECTED && attempts < 30) {
    Delay.millis(1000);
    attempts = attempts + 1;
}

if (wifi.status() != Wifi.STATUS_CONNECTED) {
    Serial.println("WiFi connection failed");
}
```

Call HTTP or HTTPS operations only after the connection succeeds. Applications that run indefinitely
should also decide how to handle a later disconnection, for example by waiting for connectivity or
calling `Wifi.begin` again before the next request.

## Calling REST APIs

`HttpClient.get` takes a URL, a response buffer and its capacity, a headers buffer and its capacity,
and an `int[2]` that receives the HTTP status code and the number of header bytes:

```java
private static final String URL = "http://api.open-meteo.com/v1/forecast?latitude=40.4168"
        + "&longitude=-3.7038&current=temperature_2m";

byte[] response = new byte[512];
byte[] headers = new byte[256];
int[] status = new int[2];
int responseBytes = HttpClient.get(URL, response, response.length, headers, headers.length, status);
```

As with `java.net.http.HttpClient`, the URL decides how the request travels: `http://` sends it in
the clear and `https://` over TLS. The port defaults to 80 or 443; give another one explicitly, as in
`http://192.168.1.10:8080/led`. The URL must be a compile-time string (a constant built from
other constants is fine) — see [docs/APIS.md](../apis#consuming-an-api-httpclient-walkthrough) for
exactly what that means. The compiler splits it into host, port and path, so nothing is parsed on
the board, and a board that lacks the scheme's client (the UNO Q has no plain-HTTP client) is
rejected at compile time.

For TLS, use an `https://` URL:

```java
int responseBytes = HttpClient.get("https://httpbin.org/anything", response, response.length, headers, headers.length, status);
```

Juno emits Arduino's `WiFiSSLClient`, which validates the server certificate against the root CA
bundle installed in the UNO R4 WiFi firmware. A missing, expired, or untrusted CA makes the
connection return `-1`. Arduino documents how to update the bundle with
[Upload SSL root certificates](https://support.arduino.cc/hc/en-us/articles/360016119219-Upload-SSL-root-certificates).
Juno currently uses that bundle as-is and does not expose `setCACert` for a per-program custom CA.

The result is:

| Result | Meaning |
|---:|---|
| `-1` | The TCP connection failed. |
| `0` | No response-body bytes were captured. |
| `1..response.length` | Number of body bytes stored in the buffer. |

The implementation consumes HTTP headers, supports ordinary and chunked response bodies, and puts
only body bytes into `response`. Because the HTTP status code is not exposed, applications must
validate the response body rather than assuming every positive result is a successful API response.

If the body exceeds the buffer, only `response.length` bytes are retained. Choose a buffer large
enough for the API response. JSON validation normally reports an incomplete truncated document as
`Json.TYPE_INVALID`, but the HTTP API does not otherwise expose a separate truncation flag.

Allocate reusable response buffers outside long-running loops instead of inside `while (true)` —
see [docs/APIS.md](../apis#buffers-are-caller-owned-forever) for why:

```java
byte[] response = new byte[512];

while (true) {
    int responseBytes = HttpClient.get(URL, response, response.length, headers, headers.length, status);
    // Process response[0..responseBytes).
    Delay.millis(60000);
}
```

### Runtime request paths

When a path depends on runtime data — for example coordinates returned by an earlier request —
build it in a `byte[]` and use the `HttpClient.get` overload that takes a base URL (a scheme, host and
optional port, with no path of its own) plus the buffer and its length. Runtime `String`s are limited to 31 bytes, which is far too short for a
typical query string; the buffer overload accepts paths of up to 255 bytes and returns `-1` without
connecting when `pathLength` is larger:

```java
byte[] path = new byte[256];
int pathLength = 0;
// ... copy "/v1/forecast?latitude=", the runtime latitude, and so on into path ...
int responseBytes = HttpClient.get("https://api.open-meteo.com", path, pathLength,
        response, response.length, headers, headers.length, status);
```

The base URL must still be a compile-time string. Only `GET` has this overload today, for both
`http://` and `https://` base URLs.
[`WeatherTFT`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/weather/WeatherTFT.java)
uses it to pass the coordinates returned by ipinfo.io to Open-Meteo.

### Choosing an HTTP method

`HttpClient` provides the following methods, for `http://` and `https://` URLs alike. They write the
response body into the supplied buffer and return the same result values described above:

| Method | Request body | Typical purpose |
|---|---|---|
| `get` | None | Read a resource. |
| `post` | JSON | Create a resource or invoke a non-idempotent operation. |
| `delete` | None | Delete a resource. |
| `patch` | JSON | Partially update a resource. |
| `query` | JSON | Perform a safe, idempotent query whose parameters do not fit naturally in a URI. |

`QUERY` is the HTTP method standardized by [RFC 10008](https://www.rfc-editor.org/rfc/rfc10008.html).
The remote server must explicitly support it; many existing REST services do not. Juno sends
`Content-Type: application/json` for `POST`, `PATCH`, and `QUERY`, and currently provides no way to
select a different media type.

### Sending JSON with POST, PATCH, or QUERY

The body-carrying methods send a compile-time JSON body. For example, `post` can create a resource:

```java
private static final String BODY = "{\"sensor\":\"outside\",\"value\":23}";

int responseBytes = HttpClient.post(
        "http://example.com/api/readings",
        BODY,
        response, response.length, headers, headers.length, status);
```

`patch` uses the same signature to partially update a resource:

```java
int responseBytes = HttpClient.patch(
        "http://example.com/api/readings/7",
        "{\"value\":24}",
        response, response.length, headers, headers.length, status);
```

Use `query` only with a server that implements RFC 10008:

```java
int responseBytes = HttpClient.query(
        "http://example.com/api/readings/search",
        "{\"sensor\":\"outside\"}",
        response, response.length, headers, headers.length, status);
```

The body cannot be assembled from runtime sensor readings yet because Juno has no runtime `String`
construction. Custom authorization headers are also unsupported.

### Deleting a resource

`delete` sends no request body:

```java
int responseBytes = HttpClient.delete(
        "http://example.com/api/readings/7",
        response, response.length, headers, headers.length, status);
```

## Extracting information from JSON responses

Always pass the number returned by `HttpClient`, not the array capacity, to `Json`:

```java
int responseBytes = HttpClient.get(URL, response, response.length, headers, headers.length, status);
if (responseBytes > 0) {
    double temperature = Json.getDouble(
            response, responseBytes, "current.temperature_2m");
}
```

The parser validates the complete bounded document and reads values directly from the response
buffer. It supports objects, arrays, strings, numbers, booleans, and `null`, with nesting up to 32
levels.

### Paths

Object fields use dots and array elements use zero-based bracket indexes:

| JSON | Path |
|---|---|
| `{"temperature": 21}` | `"temperature"` |
| `{"current":{"temperature":21}}` | `"current.temperature"` |
| `{"users":[{"name":"Ada"}]}` | `"users[0].name"` |
| `[{"id":7}]` | `"[0].id"` |
| Any root value | `""` |

Paths must be compile-time string literals. A path cannot be assembled dynamically, so a runtime
loop cannot generate `"items[" + index + "]"`; programs currently access known indexes using
separate literal paths. Field names containing `.`, `[`, or `]`, and field names encoded with JSON
escapes, cannot currently be addressed.

### Detecting missing, null, wrong-type, and invalid values

Typed getters return convenient zero values on failure. That means `getInt(...) == 0` and
`getBool(...) == false` are ambiguous by themselves. Use `Json.type` when the distinction matters:

```java
int valueType = Json.type(response, responseBytes, "current.temperature_2m");
if (valueType == Json.TYPE_NUMBER) {
    double temperature = Json.getDouble(
            response, responseBytes, "current.temperature_2m");
} else if (valueType == Json.TYPE_NULL) {
    Serial.println("Temperature is null");
} else if (valueType == Json.TYPE_MISSING) {
    Serial.println("Temperature is missing");
} else if (valueType == Json.TYPE_INVALID) {
    Serial.println("Invalid or truncated JSON");
}
```

Available type constants are:

| Constant | Meaning |
|---|---|
| `TYPE_MISSING` | The path does not resolve. |
| `TYPE_NULL` | JSON `null`. |
| `TYPE_BOOLEAN` | `true` or `false`. |
| `TYPE_NUMBER` | A valid JSON number. |
| `TYPE_STRING` | A JSON string. |
| `TYPE_OBJECT` | A JSON object. |
| `TYPE_ARRAY` | A JSON array. |
| `TYPE_INVALID` | The document, length, or path syntax is invalid. |

### Reading numbers and booleans

Use the getter matching the response schema:

```java
int count = Json.getInt(response, responseBytes, "count");
long sequence = Json.getLong(response, responseBytes, "sequence");
double temperature = Json.getDouble(response, responseBytes, "temperature");
boolean enabled = Json.getBool(response, responseBytes, "enabled");
```

`getInt` and `getLong` accept JSON integer tokens and reject fractions, exponents, and values outside
their signed range by returning zero. `getDouble` accepts integer, fractional, and exponent forms
such as `12`, `-0.125`, and `1.25e2`. Check `Json.type` first when zero or `false` is a legitimate
value that must be distinguished from a conversion failure.

### Reading strings

Juno cannot create a runtime Java `String`, so `Json.getString` decodes into a caller-owned UTF-8
byte buffer:

```java
byte[] name = new byte[32];
int nameBytes = Json.getString(
        response, responseBytes, "user.name", name, name.length);

int index = 0;
while (index < nameBytes) {
    int unsignedByte = name[index] & 255;
    // Consume the UTF-8 byte here.
    index = index + 1;
}
```

The method decodes JSON escapes such as `\n`, `\"`, and `\u00e9`, including Unicode surrogate
pairs. It returns the number of bytes written, does not append a NUL terminator, and never writes
more than the supplied output length. A small output buffer truncates the decoded result.

### Inspecting arrays

Use `arraySize` to validate an expected array shape, and literal indexed paths to read known
elements:

```java
int userCount = Json.arraySize(response, responseBytes, "users");
if (userCount > 0) {
    int firstUserId = Json.getInt(response, responseBytes, "users[0].id");
}
```

`arraySize` returns `-1` when the path is missing, invalid, or not an array. Dynamic traversal of an
arbitrary-length array is not yet available because JSON paths must be literals.

## Complete REST example

This example connects with build-time credentials, requests Madrid's current temperature, validates
the JSON type, and prints an integer temperature over USB serial once a minute:

```java
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.net.Wifi;

@Board(ArduinoUnoR4WiFiBoard.class)
public final class InternetExample {
    private static final Wifi WIFI = ArduinoUno.getWifi();

    private static final String URL = "http://api.open-meteo.com/v1/forecast?latitude=40.4168"
            + "&longitude=-3.7038&current=temperature_2m";

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        WIFI.begin(
                System.getenv("JUNO_WIFI_SSID"),
                System.getenv("JUNO_WIFI_PASSWORD"));

        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < 30) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }

        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            while (true) {
                Serial.println("WiFi connection failed");
                Delay.millis(5000);
            }
        }

        byte[] response = new byte[512];
        byte[] headers = new byte[256];
        int[] status = new int[2];
        while (true) {
            int responseBytes = HttpClient.get(URL, response, response.length, headers, headers.length, status);
            if (responseBytes < 0) {
                Serial.println("HTTP connection failed");
            } else if (Json.type(response, responseBytes,
                    "current.temperature_2m") == Json.TYPE_NUMBER) {
                int temperature = (int) Json.getDouble(
                        response, responseBytes, "current.temperature_2m");
                Serial.print("Temperature C: ");
                Serial.println(temperature);
            } else {
                Serial.println("Unexpected JSON response");
            }
            Delay.millis(60000);
        }
    }
}
```

Place the class in `juno-examples/src/main/java/InternetExample.java`, then build, compile, upload,
and monitor it:

```bash
./mvnw package

JUNO_WIFI_SSID='your-network-name' \
JUNO_WIFI_PASSWORD='your-network-password' \
java -jar juno-compiler/target/juno-compiler-0.1.0-SNAPSHOT.jar compile \
  --main InternetExample \
  --classpath juno-examples/target/classes:juno-api/target/classes

arduino-cli compile \
  --fqbn arduino:renesas_uno:unor4wifi \
  build/juno/InternetExample

arduino-cli upload \
  --port /dev/cu.YOUR_PORT \
  --fqbn arduino:renesas_uno:unor4wifi \
  build/juno/InternetExample

arduino-cli monitor \
  --port /dev/cu.YOUR_PORT \
  --config baudrate=115200
```

Uploading replaces the board's current firmware. Find the correct port first with
`arduino-cli board list`. For the broader build/upload workflow, see
[`docs/ARDUINO.md`](../arduino). The repository includes
[`HttpMethods.java`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/hardware/net/HttpMethods.java), which verifies all supported
methods over plain HTTP,
[`HttpsMethods.java`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/hardware/net/HttpsMethods.java), which repeats the checks with
certificate-validated TLS, and
[`MadridWeather.java`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/weather/MadridWeather.java), which extracts live API
data for display on the LED matrix.

## Boards talking to each other over UDP

[`Udp`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/net/udp/Udp.java)
works on both the UNO R4 WiFi and the UNO Q, and unlike the HTTP client its payloads are runtime
`byte[]` buffers. That makes it the way for boards to exchange data they compute. Miner 2 builds a
small, decentralized factorization network on it (see [Games](../games#network)). Code they share, such as SHA-256 and the factorizer, lives in
[`games/blockchain`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/games/blockchain).

### Miner 2

[`Miner2`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/miner/Miner2.java)
has no leader. Every board is an equal node that mines, validates and relays blocks.

- **Proof-of-work.** A block counts when the SHA-256 hash of its 80-byte, Bitcoin-layout header has
  at least `bits` leading zero bits. The miner absorbs the fixed first 64 header bytes once (the
  midstate), so each attempt costs one compression.
- **Useful work.** Each block must also carry the factorization of a number derived from its parent's
  hash, which every node checks. The miner picks only the number's length, from 2 to 62 bits.
- **Sharing out lengths.** Before mining, a node broadcasts `CLAIM` for the shortest length nobody has
  mined or claimed, and waits 1.5 seconds. A node that owns that length, or has seen it mined, answers
  `TAKEN`, and the claimer tries the next one; silence makes it the owner until a block with that
  length joins the chain. On a simultaneous claim the lower node UUID keeps the length. Once every
  length is mined, a new round starts. The `BLOCKS` tab shows the board like Tetris: each mined length
  in the color of the node that mined it, and the lengths being mined outlined. Colors are unique
  between nodes and saved on the SD card beside the node's identity.
- **Consensus.** The branch with the most cumulative work is the chain. Every eight blocks the
  difficulty moves one bit toward a block every 30 seconds.
- **Joining late.** A node that boots late asks for the current tip, and asks again every 30 seconds.
  It fetches up to four missing ancestors, asking everyone again if an answer is lost; if it still
  can't connect them to a block it knows, it adopts the oldest as a checkpoint, much like a pruned
  Bitcoin node, and validates everything after it.
- **Heartbeats.** Every node broadcasts a small `HELLO` packet every five seconds or so, with its id and
  the height and work of its tip. A node a peer has not heard from for 15 seconds is *suspect*, and
  after 25 seconds it is declared *lost* and logged over serial; its next heartbeat brings it back.
  Time is measured on each board's own clock. The `CHAIN` tab shows `Nodes N  mining M`, counting the
  board itself. Heartbeats also carry the sender's blocks board, so a node that joins late learns it.
- **Catching up.** A heartbeat that announces more work than a node's tip holds makes it ask that peer for
  the tip, and pause mining (`SYNC` on the screen) instead of building a rival branch. The pause is
  limited to 30 seconds, so a stale or lying heartbeat cannot stop a node for good.
- **One thread.** The views, the network and the miner all run on the main thread: every frame the node
  serves a few packets, then mines for about 20 ms, then the active view redraws. No locks are needed,
  and the UNO R4 WiFi builds without extra stack settings.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload -Djuno.board=arduino-uno-r4-wifi \
  -Djuno.main=io.github.jabrena.juno.usecases.miner.Miner2
```

This is phase 1 of the plan in [issue #43](https://github.com/jabrena/juno/issues/43): blocks are
not signed yet, and block times are not bounded against the future.

### Wire format and other boards

The wire format is
plain fixed-size bytes on UDP port 4210 (documented in
[`ChainProtocol`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/miner/chain/ChainProtocol.java)).
Another board can still join with a hand-written sketch that speaks the same packets. Juno also
targets the 8-bit [UNO WiFi Rev2](../arduino-uno-r3-wifi), subject to its smaller memory limits.

## Troubleshooting

- **Compilation says an environment variable is not set:** export it in the same shell or CI step
  that invokes `juno compile`; setting it only for `mvn package` is not sufficient.
- **Wi-Fi never reaches `STATUS_CONNECTED`:** verify the SSID/password and signal strength, and use
  a bounded retry loop so the failure is observable.
- **`HttpClient` returns `-1` for an `http://` URL:** the TCP connection failed. Confirm Wi-Fi
  connectivity, the host, port, DNS availability, and that the service accepts plain HTTP.
- **`HttpClient` returns `0`:** no body bytes were captured before the connection ended or timed out.
- **`HttpClient` returns `-1` for an `https://` URL:** the TCP or TLS connection failed. Check the host and port, update
  the board's connectivity firmware, and ensure its root CA bundle trusts the service certificate.
- **`Json.type` returns `TYPE_INVALID`:** pass the returned HTTP byte count—not `response.length`—and
  increase the response buffer if the body may have been truncated.
- **A getter returns zero or `false`:** call `Json.type` to distinguish a legitimate value from a
  missing path, wrong type, or invalid document.
- **An API requires bearer headers, a custom per-program CA, a non-JSON request body, or runtime
  request data:** it is outside the current client feature set.
