---
title: "HTTP client"
description: "Making HTTP and HTTPS requests with HttpClient: GET, POST, PATCH, DELETE and QUERY, with allocation-free buffers."
layout: page
---

`HttpClient` sends HTTP and HTTPS requests from a Juno program once the board is connected to Wi-Fi. Each method writes
the response body into a buffer that the program owns, so a request does not allocate memory.

## Methods

| Method | Request body | Typical purpose |
|---|---|---|
| `get` | none | Read a resource. |
| `post` | JSON | Create a resource or run a non-idempotent operation. |
| `delete` | none | Delete a resource. |
| `patch` | JSON | Partially update a resource. |
| `query` | JSON | Run a safe, idempotent query whose parameters do not fit in the URL. |

`QUERY` is the method standardised by [RFC 10008](https://www.rfc-editor.org/rfc/rfc10008.html). The server must
support it explicitly. `POST`, `PATCH` and `QUERY` send `Content-Type: application/json`.

Other static methods configure the client: `setTimeout(int)` sets the request deadline in milliseconds, and
`setTrustedCertificate(byte[], int)` supplies a certificate for an HTTPS server.

## Making a GET request

`HttpClient.get` takes a URL, a response buffer and its capacity, a headers buffer and its capacity, and an `int[2]`
that receives the HTTP status code and the number of header bytes:

```java
private static final String URL = "http://api.open-meteo.com/v1/forecast?latitude=40.4168"
        + "&longitude=-3.7038&current=temperature_2m";

byte[] response = new byte[512];
byte[] headers = new byte[256];
int[] status = new int[2];
int responseBytes = HttpClient.get(URL, response, response.length, headers, headers.length, status);
```

The URL must be a compile-time string. A constant built from other constants is fine, because the compiler splits it into
host, port and path before the program runs.

`http://` sends the request in the clear and `https://` uses TLS. The port defaults to 80 or 443; give another port in
the URL, as in `http://192.168.1.10:8080/led`. HTTPS validates the server certificate against the root certificates
the board trusts.

## Caller-owned buffers

The three arrays in the GET example are the design of Juno's HTTP client. The client never allocates memory: the program
creates the arrays once, and each request fills them in place.

| Array | What the client writes into it | When it is too small |
|---|---|---|
| `byte[] response` | the response body | the body is truncated; the return value is the number of bytes stored |
| `byte[] headers` | the response headers as text | the headers are truncated; `status[1]` reports how many bytes were written |
| `int[] status` | two results: `status[0]` is the HTTP status code and `status[1]` is the number of header bytes written | not applicable: the array always has two elements |

The reason is memory. The board has a fixed 8 KB arena and no `OutOfMemoryError` to catch, so Juno requires every size to
be known when the program is built. Each request then costs the same memory, however large the server's reply. The
consequences for your program:

- Choose the response size for the largest reply you expect, and check the return value: a full buffer may mean the body
  was cut short.
- Read `status[0]` before you trust the body. The client returns the body whatever the status, including a `404` page.
- Pass the lengths explicitly. The methods take a capacity next to each array, so one array can be reused with a smaller
  capacity.

## Reading the result

| Result | Meaning |
|---:|---|
| `-1` | The connection failed, or the certificate was not trusted. |
| `0` | No response body bytes were captured. |
| `1..response.length` | Number of body bytes stored in the buffer. |

The client does not expose the HTTP status code to the body result, but `status[0]` holds it. The body is truncated to the
buffer's length, so choose a buffer large enough for the response. Validate the body before you trust it: a positive
result is not proof that the request succeeded.

## Sending JSON with POST, PATCH and QUERY

The request body must be a compile-time string, so the same URL and body rules apply:

```java
String body = "{\"led\":\"on\"}";
int responseBytes = HttpClient.post("http://192.168.1.10:8080/led", body, response, response.length,
        headers, headers.length, status);
```

## Keeping the buffers outside the loop

Allocate the response and header buffers once, at startup, and reuse them. Creating them inside a loop makes the
program allocate on every pass:

```java
byte[] response = new byte[512];
byte[] headers = new byte[256];
int[] status = new int[2];

while (true) {
    int responseBytes = HttpClient.get(URL, response, response.length, headers, headers.length, status);
    // Process response[0..responseBytes).
    Delay.millis(60000);
}
```

## Building the path at run time

A path that depends on runtime data, such as coordinates returned by an earlier request, cannot be a literal. Build it in
a `byte[]` and use the overload that takes a base URL (a scheme, host and optional port, with no path) plus the path
buffer and its length. Runtime `String`s are limited to 31 bytes, so the buffer is the way to build longer paths:

```java
byte[] path = new byte[256];
int pathLength = 0;
// ... copy "/v1/forecast?latitude=", the runtime latitude, and so on into path ...
int responseBytes = HttpClient.get("https://api.open-meteo.com", path, pathLength,
        response, response.length, headers, headers.length, status);
```

The base URL must still be a compile-time string. Only `GET` has this overload. The
[`WeatherTFT`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/weather/WeatherTFT.java)
example uses it to pass the coordinates returned by one service to another.

## Reading JSON responses

`Json` reads values straight from the response bytes, with no parsing into objects. Pass it the number of bytes the client
returned, not the buffer's capacity:

```java
int responseBytes = HttpClient.get(URL, response, response.length, headers, headers.length, status);
if (responseBytes > 0) {
    double temperature = Json.getDouble(response, responseBytes, "current.temperature_2m");
}
```

The parser checks the whole document before it reads a value. It supports objects, arrays, strings, numbers, booleans and
`null`, nested up to 32 levels deep.

### Paths

Object fields are separated by dots, and array elements use zero-based indexes in brackets:

| JSON | Path |
|---|---|
| `{"temperature": 21}` | `"temperature"` |
| `{"current":{"temperature":21}}` | `"current.temperature"` |
| `{"users":[{"name":"Ada"}]}` | `"users[0].name"` |
| `[{"id":7}]` | `"[0].id"` |

A path is a compile-time string literal, so a loop cannot build one such as `"items[" + index + "]"`. Known positions are
read with separate literal paths. Field names that contain `.`, `[` or `]`, and names written with JSON escapes, cannot
yet be addressed.

### Reading values

| Method | Returns |
|---|---|
| `Json.getInt(...)` | an `int` |
| `Json.getLong(...)` | a `long` |
| `Json.getDouble(...)` | a `double` |
| `Json.getBool(...)` | a `boolean` |
| `Json.getString(..., byte[] out, int outLength)` | the number of UTF-8 bytes written to `out` |
| `Json.arraySize(...)` | the number of elements in an array, or `-1` when the path is missing, invalid or not an array |

Typed getters return zero or `false` when the value cannot be read, so `getInt(...) == 0` is ambiguous by itself. Use
`Json.type` when the difference matters:

```java
int valueType = Json.type(response, responseBytes, "current.temperature_2m");
if (valueType == Json.TYPE_NUMBER) {
    double temperature = Json.getDouble(response, responseBytes, "current.temperature_2m");
} else if (valueType == Json.TYPE_NULL) {
    Serial.println("Temperature is null");
} else if (valueType == Json.TYPE_MISSING) {
    Serial.println("Temperature is missing");
} else if (valueType == Json.TYPE_INVALID) {
    Serial.println("Invalid or truncated JSON");
}
```

The type constants are `TYPE_MISSING`, `TYPE_NULL`, `TYPE_BOOLEAN`, `TYPE_NUMBER`, `TYPE_STRING`, `TYPE_OBJECT`,
`TYPE_ARRAY` and `TYPE_INVALID`. A document cut short by a small response buffer reads as `TYPE_INVALID`.

### Reading strings

Juno cannot create a runtime Java `String` here, so `Json.getString` decodes into a `byte[]` that the program owns:

```java
byte[] name = new byte[32];
int nameBytes = Json.getString(response, responseBytes, "user.name", name, name.length);
```

The method decodes escapes such as `\n`, `\"` and `\u00e9`, including surrogate pairs, and writes at most `name.length`
bytes. It adds no terminating NUL. A small buffer truncates the result.

## Interacting with HTTPS endpoints

An `https://` URL makes the client use TLS. Before it sends anything, the client checks the server's certificate chain
against the trusted root certificates on the board. The calls are the same as for plain HTTP; only the scheme changes:

```java
int responseBytes = HttpClient.get("https://httpbin.org/anything", response, response.length, headers, headers.length, status);
```

A certificate that does not chain to a trusted root makes the request return `-1`.

### Trusted roots on each board

| Board | Where the trusted roots come from | Runtime trust anchor (`setTrustedCertificate`) |
|---|---|---|
| UNO R4 WiFi | The root CA bundle installed in the WiFi firmware. Arduino documents how to update it. | Not supported: returns `false`. |
| UNO WiFi Rev2 | The root certificates in the NINA-W102 module's firmware. | Not supported: returns `false`. |
| UNO Q | The Linux system trust store, used through the bridge. | Not supported: returns `false`. |
| Nano ESP32 and M5Stack Cardputer | Mozilla's root certificates, bundled into the program (about 55 KB of flash). | Supported. |

The ESP32 boards also do not check certificate validity dates, because their TLS library has no calendar clock. A
certificate that has expired can still be accepted there, so do not rely on the date check for security.

### Installing a trust anchor

On the Nano ESP32 and the Cardputer, a program can trust one extra certificate, such as a self-signed certificate for a
server on the local network. The certificate is PEM text, read into a caller-owned buffer, and its length excludes the
trailing NUL byte, so the buffer must hold that NUL too:

```java
byte[] certificate = new byte[1537];   // PEM text read from the SD card, plus a trailing NUL byte
int certificateLength = 1536;          // bytes of PEM text, excluding the NUL
certificate[certificateLength] = 0;

if (!HttpClient.setTrustedCertificate(certificate, certificateLength)) {
    Serial.println("This board cannot install a certificate");
}
```

The client keeps a pointer to the buffer, so keep the buffer reachable for as long as requests may use it. Do not reuse it
for other data while HTTPS requests are still running.

## Compared with the JDK HTTP client

The JDK's [`java.net.http.HttpClient`](https://openjdk.org/groups/net/httpclient/intro.html) is the standard client on the
desktop. Juno's client follows its purpose, sending HTTP and HTTPS requests, but not its design, because it has to run in
a fixed amount of RAM:

| | JDK `HttpClient` | Juno `HttpClient` |
|---|---|---|
| Creating a client | `HttpClient.newHttpClient()` or a builder | No object: static methods on `HttpClient` |
| Building a request | an immutable `HttpRequest` built with `HttpRequest.newBuilder()` | a URL string argument |
| Getting the response | an `HttpResponse<T>` object | the program's own `byte[]` buffers, filled in place |
| Choosing the body type | a `BodyHandler` such as `ofString`, `ofByteArray` or `ofFile` | always raw bytes in a caller-owned buffer |
| Synchronous and asynchronous | `send` and `sendAsync` (a `CompletableFuture`) | synchronous calls only |
| Headers | set on the request and read from the response | raw text in a buffer; no custom request headers |
| Memory | the library allocates objects and buffers | no allocation; every size is fixed at build time |
| Request body | `BodyPublishers` such as `ofString` or `ofFile` | a compile-time string literal |
| Redirects, cookies and authentication | configurable through the builder | not available |
| Timeouts | set per request and per client | `setTimeout` sets one deadline for requests |

### The same GET request in each client

Juno, as in [Making a GET request](#making-a-get-request):

```java
private static final String URL = "http://api.open-meteo.com/v1/forecast?latitude=40.4168"
        + "&longitude=-3.7038&current=temperature_2m";

byte[] response = new byte[512];
byte[] headers = new byte[256];
int[] status = new int[2];
int responseBytes = HttpClient.get(URL, response, response.length, headers, headers.length, status);
```

The JDK client, with the same URL:

```java
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(URL))
        .GET()
        .build();
HttpResponse<byte[]> reply = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
int statusCode = reply.statusCode();
byte[] body = reply.body();
```

The JDK version needs `IOException` and `InterruptedException` handled, since `send` declares both. Juno's call declares
neither. The two differ in where the data goes:

- In Juno the program creates the buffers first and passes them in. The client writes the result into them and returns
  a count, so the program keeps control of the memory.
- In the JDK the library creates the result: `send` returns an object holding the status, headers and body, and the
  program reads them with accessor methods.
- Juno's request is a single call with the URL and the buffers. The JDK builds an `HttpRequest` first, then sends it.

The JDK client also streams bodies as reactive `ByteBuffer` flows. Juno has no streams, because each reply is read into one
buffer that the program chose in advance.

## Examples

- [`SingleHttpsGet`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/SingleHttpsGet.java)
  makes one HTTPS request and prints the result.
- [`ParallelHttpsGet`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/ParallelHttpsGet.java)
  makes several requests at the same time with virtual threads.
- [`WeatherTFT`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/weather/WeatherTFT.java)
  shows a weather station that builds its request path at run time.
