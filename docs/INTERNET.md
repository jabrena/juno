# Internet access

Juno provides a small, allocation-free Internet stack for the Arduino UNO R4 WiFi. Programs can:

- connect to a Wi-Fi network with [`Wifi`](../juno/src/main/java/io/github/jabrena/juno/api/io/net/Wifi.java);
- send plain HTTP or TLS-protected HTTPS `GET`, `POST`, `DELETE`, `PATCH`, and `QUERY` requests with
  [`HttpClient`](../juno/src/main/java/io/github/jabrena/juno/api/io/net/HttpClient.java) and
  [`HttpsClient`](../juno/src/main/java/io/github/jabrena/juno/api/io/net/HttpsClient.java); and
- extract typed values directly from JSON response bytes with
  [`Json`](../juno/src/main/java/io/github/jabrena/juno/api/io/net/Json.java).

These APIs are compiler intrinsics. Java declares them as `native` methods, and Juno emits their
Arduino C++ implementations only when the program uses them. Response and string buffers belong to
the caller; the implementation does not allocate a JSON document or create runtime Java strings.

## Requirements and current limits

Wi-Fi, HTTP, and HTTPS require an UNO R4 WiFi entry point:

```java
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;

@Board(ArduinoUnoR4WiFi.class)
public final class InternetExample {
    // ...
}
```

`Json` itself does not touch the network and can parse any local `byte[]`.

The current network layer deliberately stays small:

- request hosts, paths, and request bodies must be compile-time strings;
- custom request headers, redirects, cookies, and authentication helpers are not available;
- HTTPS uses the root CA bundle installed in the board's WiFi firmware; Juno does not yet accept a
  custom CA certificate from Java source;
- the response status line and headers are consumed but are not exposed to Java code;
- each request has a fixed five-second timeout; and
- the response body may be truncated when it is larger than the supplied buffer.

Prefer `HttpsClient` for Internet services. Do not send API tokens, personal data, credentials, or
other secrets with plain `HttpClient`.

## Supplying Wi-Fi credentials

Do not put Wi-Fi credentials directly in Java source. Pass compile-time environment-variable reads
to `Wifi.begin` instead:

```java
Wifi.begin(
        System.getenv("JUNO_WIFI_SSID"),
        System.getenv("JUNO_WIFI_PASSWORD"));
```

Set the variables in the environment that runs `juno compile`:

```bash
export JUNO_WIFI_SSID='your-network-name'
export JUNO_WIFI_PASSWORD='your-network-password'

java -jar juno/target/juno-0.1.0-SNAPSHOT.jar compile \
  --main InternetExample \
  --classpath juno-examples/target/classes:juno/target/classes
```

They can also be scoped to that single command:

```bash
JUNO_WIFI_SSID='your-network-name' \
JUNO_WIFI_PASSWORD='your-network-password' \
java -jar juno/target/juno-0.1.0-SNAPSHOT.jar compile \
  --main InternetExample \
  --classpath juno-examples/target/classes:juno/target/classes
```

Juno evaluates `System.getenv("NAME")` on the development machine during compilation. The variable
name must be a string literal, and compilation fails with a clear error if the variable is absent.
The board does not have an environment and never calls `System.getenv` at runtime.

### Alternative: a `.env` file via `juno-maven-plugin`

For Maven-built examples, `juno-maven-plugin`'s `env` goal is a Maven-level alternative to
exporting shell environment variables by hand: it reads a git-ignored `.env` file (`KEY=VALUE` per
line) from the module's base directory and exports every entry into the Maven JVM's real process
environment, overwriting any value already set for that name. Nothing is generated — programs keep
using the exact same `System.getenv("NAME")` reads shown above, with no separate class or import.
Because `juno-maven-plugin:compile` runs later in that same JVM, `System.getenv(...)` sees the
`.env` values during compilation exactly as if the shell had exported them itself.

Enable it by adding an execution to the module's `juno-maven-plugin` configuration (see
[`juno-examples/pom.xml`](../juno-examples/pom.xml)):

```xml
<executions>
    <execution>
        <goals>
            <goal>env</goal>
        </goals>
    </execution>
</executions>
```

Then create `.env` (never committed — `.env` is in the repository's `.gitignore`) next to that
module's `pom.xml`:

```text
JUNO_WIFI_SSID=your-network-name
JUNO_WIFI_PASSWORD=your-network-password
```

The `env` goal runs during the `generate-sources` phase, before `compile` — no other configuration
is needed. A missing `.env` file is not an error: nothing is exported, so modules that don't use
WiFi are unaffected, and modules that do must still see the variable set some other way (shell
export, CI secret, etc.) or compilation fails with the same clear error as an unset variable
always produces.

Mutating the JVM's environment map is an unsupported-but-stable reflection trick, and on JDK 9+ it
requires the Maven JVM to be launched with `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens
java.base/java.util=ALL-UNNAMED`. This repository's [`.mvn/jvm.config`](../.mvn/jvm.config)
already adds both flags to every `mvn`/`mvnw` invocation, so no per-command setup is needed.

Environment variables keep credentials out of Java source and Git history, but they are not runtime
secret storage. Juno embeds the resolved value in the generated `.ino` sketch and firmware. Keep
`build/` artifacts private, avoid exposing the firmware, and clear exported variables when they are
no longer needed. Juno does not automatically read `.env` files; a shell or CI system must export
their values before invoking the compiler.

The same mechanism can supply an HTTP or HTTPS host, path, or request body when necessary:

```java
int responseBytes = HttpClient.get(
        System.getenv("JUNO_API_HOST"),
        80,
        System.getenv("JUNO_API_PATH"),
        response,
        response.length);
```

Any value supplied this way is still embedded in the generated firmware. `HttpsClient` protects it
in transit; `HttpClient` sends it without encryption.

## Connecting to Wi-Fi

`Wifi.begin` starts the connection. Poll `Wifi.status()` until it equals
`Wifi.STATUS_CONNECTED`. A bounded retry loop lets the program report or display a failure instead
of waiting forever:

```java
Wifi.begin(
        System.getenv("JUNO_WIFI_SSID"),
        System.getenv("JUNO_WIFI_PASSWORD"));

int attempts = 0;
while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < 30) {
    Delay.millis(1000);
    attempts = attempts + 1;
}

if (Wifi.status() != Wifi.STATUS_CONNECTED) {
    Serial.println("WiFi connection failed");
}
```

Call HTTP or HTTPS operations only after the connection succeeds. Applications that run indefinitely
should also decide how to handle a later disconnection, for example by waiting for connectivity or
calling `Wifi.begin` again before the next request.

## Calling REST APIs

`HttpClient.get` takes a host name, TCP port, path, response buffer, and buffer capacity:

```java
private static final String HOST = "api.open-meteo.com";
private static final int PORT = 80;
private static final String PATH = "/v1/forecast?latitude=40.4168&longitude=-3.7038"
        + "&current=temperature_2m";

byte[] response = new byte[512];
int responseBytes = HttpClient.get(HOST, PORT, PATH, response, response.length);
```

Pass the host without `http://` and normally begin the path with `/`. The host and path must be
string literals, constant literal expressions, or direct `System.getenv("LITERAL_NAME")` calls;
Juno cannot construct a runtime `String`.

For TLS, use the equivalent `HttpsClient` method, conventionally on port 443. Do not include
`https://` in the host:

```java
int responseBytes = HttpsClient.get(
        "httpbin.org", 443, "/anything", response, response.length);
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

Allocate reusable response buffers outside long-running loops. Juno uses a fixed program-lifetime
arena with no reclamation, so repeatedly allocating a new array inside `while (true)` eventually
exhausts it:

```java
byte[] response = new byte[512];

while (true) {
    int responseBytes = HttpClient.get(HOST, PORT, PATH, response, response.length);
    // Process response[0..responseBytes).
    Delay.millis(60000);
}
```

### Choosing an HTTP method

Both `HttpClient` and `HttpsClient` provide the following methods. They write the response body into
the supplied buffer and return the same result values described above:

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
        "example.com",
        80,
        "/api/readings",
        BODY,
        response,
        response.length);
```

`patch` uses the same signature to partially update a resource:

```java
int responseBytes = HttpClient.patch(
        "example.com",
        80,
        "/api/readings/7",
        "{\"value\":24}",
        response,
        response.length);
```

Use `query` only with a server that implements RFC 10008:

```java
int responseBytes = HttpClient.query(
        "example.com",
        80,
        "/api/readings/search",
        "{\"sensor\":\"outside\"}",
        response,
        response.length);
```

The body cannot be assembled from runtime sensor readings yet because Juno has no runtime `String`
construction. Custom authorization headers are also unsupported.

### Deleting a resource

`delete` sends no request body:

```java
int responseBytes = HttpClient.delete(
        "example.com",
        80,
        "/api/readings/7",
        response,
        response.length);
```

## Extracting information from JSON responses

Always pass the number returned by `HttpClient`, not the array capacity, to `Json`:

```java
int responseBytes = HttpClient.get(HOST, PORT, PATH, response, response.length);
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
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;
import io.github.jabrena.juno.api.io.net.HttpClient;
import io.github.jabrena.juno.api.io.net.Json;
import io.github.jabrena.juno.api.io.net.Wifi;

@Board(ArduinoUnoR4WiFi.class)
public final class InternetExample {
    private static final String HOST = "api.open-meteo.com";
    private static final String PATH = "/v1/forecast?latitude=40.4168&longitude=-3.7038"
            + "&current=temperature_2m";

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Wifi.begin(
                System.getenv("JUNO_WIFI_SSID"),
                System.getenv("JUNO_WIFI_PASSWORD"));

        int attempts = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < 30) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }

        if (Wifi.status() != Wifi.STATUS_CONNECTED) {
            while (true) {
                Serial.println("WiFi connection failed");
                Delay.millis(5000);
            }
        }

        byte[] response = new byte[512];
        while (true) {
            int responseBytes = HttpClient.get(HOST, 80, PATH, response, response.length);
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
java -jar juno/target/juno-0.1.0-SNAPSHOT.jar compile \
  --main InternetExample \
  --classpath juno-examples/target/classes:juno/target/classes

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
[`docs/ARDUINO.md`](ARDUINO.md). The repository includes
[`HttpMethods.java`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/HttpMethods.java), which verifies all supported
methods over plain HTTP,
[`HttpsMethods.java`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/HttpsMethods.java), which repeats the checks with
certificate-validated TLS, and
[`MadridWeather.java`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/weather/MadridWeather.java), which extracts live API
data for display on the LED matrix.

## Troubleshooting

- **Compilation says an environment variable is not set:** export it in the same shell or CI step
  that invokes `juno compile`; setting it only for `mvn package` is not sufficient.
- **Wi-Fi never reaches `STATUS_CONNECTED`:** verify the SSID/password and signal strength, and use
  a bounded retry loop so the failure is observable.
- **`HttpClient` returns `-1`:** the TCP connection failed. Confirm Wi-Fi connectivity, the host,
  port, DNS availability, and that the service accepts plain HTTP.
- **`HttpClient` returns `0`:** no body bytes were captured before the connection ended or timed out.
- **`HttpsClient` returns `-1`:** the TCP or TLS connection failed. Check the host and port, update
  the board's connectivity firmware, and ensure its root CA bundle trusts the service certificate.
- **`Json.type` returns `TYPE_INVALID`:** pass the returned HTTP byte count—not `response.length`—and
  increase the response buffer if the body may have been truncated.
- **A getter returns zero or `false`:** call `Json.type` to distinguish a legitimate value from a
  missing path, wrong type, or invalid document.
- **An API requires bearer headers, a custom per-program CA, a non-JSON request body, or runtime
  request data:** it is outside the current client feature set.
