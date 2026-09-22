# Basic email support

Juno provides a small, allocation-free email stack for the Arduino UNO R4 WiFi. Programs can:

- send a plain-text message to one recipient with
  [`Smtp`](../juno/src/main/java/io/github/jabrena/juno/api/io/net/email/Smtp.java); and
- report a mailbox's message count, read the newest message, and read any message's subject with
  [`Pop3Client`](../juno/src/main/java/io/github/jabrena/juno/api/io/net/email/Pop3Client.java).

Like `HttpClient`/`HttpsClient` (see [the Internet access guide](INTERNET.md)), these are compiler
intrinsics: Java declares them as `native` methods, and Juno emits their Arduino C++
implementations only when the program uses them. Every buffer belongs to the caller; nothing here
allocates a runtime `String` or a heap object.

## Scope, deliberately

This is a basic client, not a full mail library:

- one recipient, plain text only;
- no attachments, HTML, multipart MIME, folders, or OAuth2;
- `host`, `username`, `password`, and every message field must be compile-time strings (a
  literal, or `System.getenv("NAME")` of a literal name) — Juno has no heap to build a runtime
  `String` from;
- responses (subjects, headers, bodies) are bounded to caller-owned buffers and may be truncated.

## Requirements

Both APIs need an UNO R4 WiFi entry point and an active `Wifi` connection:

```java
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;

@Board(ArduinoUnoR4WiFi.class)
public final class EmailExample {
    // ...
}
```

`Pop3Client` reuses `HttpsClient`'s native `WiFiSSLClient` (implicit TLS, POP3S on port 995) and
needs no extra library. `Smtp` needs the third-party `ESP_SSLClient` library — see
[Why `Smtp` needs an extra library](#why-smtp-needs-an-extra-library) below — installed once with:

```bash
./mvnw -f juno-examples/pom.xml juno:install-deps
```

(or on its own with `arduino-cli lib install ESP_SSLClient`; see
[docs/JUNO-MAVEN-PLUGIN.md](JUNO-MAVEN-PLUGIN.md)).

## Supplying mailbox credentials

Do not put mailbox credentials directly in Java source. Read them the same way as any other
compile-time credential — see
[docs/APIS.md](APIS.md#supplying-compile-time-credentials-with-systemgetenv) for the full
mechanism (either exported in the shell running `juno compile`, or via a git-ignored `.env` file
and `juno-maven-plugin`'s `env` goal):

```text
SMTP_HOST=mail.example.com
SMPT_USERNAME=me@example.com
SMTP_PASSWORD=your-mailbox-password
```

`SMPT_USERNAME` (missing the `H`) is not a typo introduced here — it's whatever key name is
already in this repository's own `.env`; rename it (and the `System.getenv(...)` calls that read
it) if that was accidental. `Smtp`/`Pop3Client` take the port as a plain `int` literal, not a
string, since Juno has no way to parse a compile-time string into a compile-time int — so a
`POP3_PORT`/`SMTP_PORT` entry in `.env` is documentation at best, never something the compiler
actually reads (see the port constants in the examples below).

## Sending a message

`Smtp.send` connects, upgrades to TLS with `STARTTLS`, authenticates with `AUTH LOGIN`, and sends
one plain-text message:

```java
int sent = Smtp.send(
        System.getenv("SMTP_HOST"), 587,
        System.getenv("SMPT_USERNAME"), System.getenv("SMTP_PASSWORD"),
        System.getenv("SMPT_USERNAME"), System.getenv("SMPT_USERNAME"),
        "Arduino alert", "Alarm activated");
```

Port 587 (mail submission over `STARTTLS`) is the common case; see
[Why `Smtp` needs an extra library](#why-smtp-needs-an-extra-library) for why it isn't port 465's
implicit TLS instead. `sent` is `0` on success, or a negative code identifying the failing step:

| Code | Failing step |
|---:|---|
| `-1` | Connect |
| `-2` | Server greeting |
| `-3` | `EHLO` |
| `-4` | `AUTH LOGIN` |
| `-5` | Authentication |
| `-6` | `MAIL FROM` |
| `-7` | `RCPT TO` |
| `-8` | `DATA` |
| `-9` | The message body |
| `-10` | Base64-encoding the username/password overflowed its internal buffer |
| `-11` | `STARTTLS` itself |
| `-12` | The TLS upgrade |

### Why `Smtp` needs an extra library

The UNO R4 WiFi's native `WiFiSSLClient` (the same class `HttpsClient`/`Pop3Client` use) can only
negotiate TLS from the very first byte of a connection — it has no way to start a plaintext
connection and upgrade it to TLS mid-stream, which is exactly what `STARTTLS` requires. The
third-party [`ESP_SSLClient`](https://github.com/mobizt/ESP_SSLClient) library adds that upgrade
path (`connect()` in plain-TCP mode, then `connectSSL()` once `STARTTLS` is negotiated), backed by
its own vendored BearSSL. Measured on real hardware, it fits comfortably in the UNO R4 WiFi's 32 KB
of RAM (well under 60% flash, well under 50% RAM for the full send-and-verify example below) — but
it is one more thing to install, so it stays a manual/`juno:install-deps` dependency rather than
being vendored into this repository (see the discussion in
[`InstallDepsMojo`](../juno-maven-plugin/src/main/java/io/github/jabrena/juno/maven/InstallDepsMojo.java)).

One consequence: `ESP_SSLClient`'s software TLS stack validates the server's host name but not its
full certificate chain (there is no CA bundle available to it on this board), unlike
`HttpsClient`/`Pop3Client`, which validate against the WiFi module's own firmware-backed CA bundle.
This is a deliberate tradeoff to fit `STARTTLS` in the available RAM, not an oversight.

## Reading a mailbox

### Message count

```java
int count = Pop3Client.messageCount(
        System.getenv("SMTP_HOST"), 995,
        System.getenv("SMPT_USERNAME"), System.getenv("SMTP_PASSWORD"));
```

`count` is the mailbox's message count on success, or a negative code:

| Code | Failing step |
|---:|---|
| `-1` | Connect |
| `-2` | Server greeting |
| `-3` | `USER` |
| `-4` | `PASS` |
| `-5` | `STAT` |

### The newest message

`readLatest` retrieves the highest-numbered message (`RETR` after `STAT`), extracting `From`/
`Subject` into one buffer and a bounded, dot-unstuffed plain-text body into another:

```java
byte[] headers = new byte[Pop3Client.DEFAULT_HEADERS_BUFFER_SIZE];
byte[] body = new byte[Pop3Client.DEFAULT_BODY_BUFFER_SIZE];
int[] status = new int[1];

int bodyLength = Pop3Client.readLatest(
        System.getenv("SMTP_HOST"), 995,
        System.getenv("SMPT_USERNAME"), System.getenv("SMTP_PASSWORD"),
        headers, headers.length, body, body.length, status);
int headersLength = status[0];
```

`bodyLength` is the number of bytes written to `body` on success, or a negative code:

| Code | Failing step |
|---:|---|
| `-1` | Connect |
| `-2` | Server greeting |
| `-3` | `USER` |
| `-4` | `PASS` |
| `-5` | `STAT` |
| `-6` | Empty mailbox |
| `-7` | `RETR` |
| `-8` | Connection dropped while streaming the message |

### A specific message's subject

`readSubject` is the cheap alternative to `readLatest` for listing several messages: it sends
`TOP messageNumber 0` (headers only, no body), so it costs far less bandwidth than downloading a
whole message just to show its subject. `messageNumber` is 1-based POP3 numbering (`1` is the
oldest message still in the mailbox):

```java
byte[] subject = new byte[32];
int subjectLength = Pop3Client.readSubject(
        System.getenv("SMTP_HOST"), 995,
        System.getenv("SMPT_USERNAME"), System.getenv("SMTP_PASSWORD"),
        1, subject, subject.length);
```

`subjectLength` is the number of bytes written on success (`0` for a message with no `Subject`
header), or a negative code:

| Code | Failing step |
|---:|---|
| `-1` | Connect |
| `-2` | Server greeting |
| `-3` | `USER` |
| `-4` | `PASS` |
| `-5` | `TOP` |
| `-6` | Connection dropped while streaming the headers |

Since Juno cannot build a runtime `String`, printing a subject means printing its raw bytes
directly — see `LcdKeypadShield.print(byte[], int)`, added for exactly this, in the `EmailClient`
example below.

## A note on transient connection failures

The UNO R4 WiFi's `Wifi.status()` can report `STATUS_CONNECTED` slightly before the module's
underlying TCP/IP stack (DHCP lease, routing) is actually ready to accept an outbound connection,
so the very first `Smtp`/`Pop3Client` call right after `Wifi.begin` succeeds can fail with `-1`
even though every later call works. Retrying a few times, a couple of seconds apart, absorbs this
one-off race instead of surfacing it — see `pollMessageCount` in the examples below.

## Complete examples

Three examples in `juno-examples` build on each other, in
[`juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/email/`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/email/):

- [`InboxCount`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/email/InboxCount.java) —
  the simplest: polls `Pop3Client.messageCount` every 30 seconds and shows it on the LCD Keypad
  Shield. Confirmed working on a real UNO R4 WiFi.
- [`EmailHelloWorld`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/email/EmailHelloWorld.java) —
  proves `Smtp.send` actually delivers, not just that the server accepted the message: reads the
  inbox count *before* sending a "Hello World" message to itself, then polls the count *after*
  sending until it goes up. Waits for a button press before sending.
- [`EmailClient`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/email/EmailClient.java) —
  a small menu-driven client: Inbox (a submenu of message Count and a paginated Subject List, up
  to the first 10 messages, 2 per screen), Send Email, and About.

Build and upload any of them the same way as every other example:

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.io.net.email.EmailClient
```

## Troubleshooting

- **`Smtp.send` returns `-1` or `-2` only on the very first call after connecting:** see
  [A note on transient connection failures](#a-note-on-transient-connection-failures) above.
- **`Smtp.send` returns `-11` or `-12`:** the mail server didn't offer `STARTTLS` on port 587, or
  the TLS upgrade itself failed. Confirm the host/port with another mail client first.
  `ESP_SSLClient` validates the host name but not the full certificate chain (see
  [Why `Smtp` needs an extra library](#why-smtp-needs-an-extra-library)), so this is not a
  certificate-trust failure in the usual sense.
  Confirm `arduino-cli lib install ESP_SSLClient` (or `juno:install-deps`) has actually run — a
  missing library fails the `arduino-cli compile` step, not the upload.
- **`Pop3Client` calls return `-5` on `readSubject`/`readLatest`:** the server may not support
  `TOP` (rare, but it's an optional POP3 command) — try `messageCount`/`readLatest` alone to
  confirm basic connectivity first.
- **A subject/body/header looks truncated:** the caller-owned buffer was smaller than the actual
  content; every read here is bounded, never dynamically sized.
- **Compilation says `SMTP_HOST`/`SMPT_USERNAME`/`SMTP_PASSWORD` is not set:** see
  [the Internet access guide's environment-variable troubleshooting](INTERNET.md#troubleshooting) —
  the same rules apply (export it where `juno compile` runs, or use a `.env` file).
