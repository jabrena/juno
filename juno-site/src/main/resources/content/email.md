---
title: "Email"
description: "Sending mail over SMTP with Smtp and reading a mailbox over POP3 with Pop3Client."
layout: page
---

Juno supports two email protocols: SMTP for sending mail and POP3 for reading it. Each has its own client, both are
compiler intrinsics, and both need an active [`Wifi`](../wifi) connection. Every buffer belongs to the caller.

The host, username, password, and message fields come from the microSD card, as runtime `String` values. Each one holds at
most 31 bytes, so keep them short (see [Credentials](#credentials)).

## Board support

`Smtp.sendTls` and `Pop3Client` work on the boards with the `TLS_EMAIL` capability: UNO R4 WiFi, UNO Q, UNO WiFi Rev2
and Nano ESP32. `Smtp.send` (STARTTLS) is limited to the UNO R4 WiFi and the Nano ESP32, because only those two
boards have the `SMTP_STARTTLS` capability. The compiler rejects a call the board cannot run.

## Credentials

Keep the mailbox settings and the password on the microSD card, not in the Java source. Put them in
`application.properties` next to the Wi-Fi settings, and read the file once at startup, as the Nano ESP32 examples do
(see [SD Card](../sdcard#reading-application-properties)):

```text
wifi.ssid=my-network
wifi.password=my-wifi-password
smtp.host=mail.example.com
smtp.username=me@example.com
smtp.password=your-mailbox-password
smtp.from=me@example.com
smtp.to=me@example.com
smtp.subject=Arduino alert
smtp.body=Alarm activated
pop3.host=mail.example.com
pop3.username=me@example.com
pop3.password=your-mailbox-password
```

The SMTP calls use the `smtp.*` keys and the POP3 calls use the `pop3.*` keys. Load them with the card's `SdCard` and
`Properties`:

```java
Properties properties = new Properties();
try (InputStream file = SD_CARD.open("application.properties")) {
    properties.load(file);
}
String host = properties.getProperty("smtp.host");
String username = properties.getProperty("smtp.username");
String password = properties.getProperty("smtp.password");
```

Check that the file and each key exist before using them; the examples print a message and stop when one is missing.
Values loaded this way are runtime `String`s, and a runtime `String` holds 31 bytes, so keep the password, the subject
and the body within that length.

The `InboxCount` and `EmailClient` examples read the SMTP values from the ATECC608 `SecretStore` through
`SecretStore`. SD-card properties remain supported for applications that intentionally use removable configuration;
they are not a substitute for provisioning credentials into the secure element.

## SMTP

[`Smtp`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/net/email/Smtp.java)
sends one plain-text message to one recipient. It offers two ways to start the encrypted connection:

| Method | Argument | Notes |
|---|---|---|
| `Smtp.send(...)` | host, port, username, password, from, to, subject, body | `STARTTLS` on port `587`; returns `0` or a step code (`-1` to `-12`) |
| `Smtp.sendTls(...)` | the same arguments | implicit TLS, normally port `465`; the same codes, without `-11` and `-12` |

### Sending with STARTTLS

`Smtp.send` connects in plain TCP, asks the server to upgrade with `STARTTLS`, then authenticates with `AUTH LOGIN`
over the encrypted connection. Port `587` is the usual submission port:

```java
int sent = Smtp.send(
        host, 587,
        username, password,
        username, username,
        "Arduino alert", "Alarm activated");
```

`STARTTLS` needs the third-party `ESP_SSLClient` library, which the Maven plugin installs with
`./mvnw -f juno-examples/pom.xml juno:install-deps`.

`send` returns `0` on success and a negative code naming the step that failed:

| Code | Step |
|---:|---|
| `-1` | Connect |
| `-2` | Server greeting |
| `-3` | `EHLO` |
| `-4` | `AUTH LOGIN` |
| `-5` | Authentication |
| `-6` | `MAIL FROM` |
| `-7` | `RCPT TO` |
| `-8` | `DATA` |
| `-9` | Message body |
| `-10` | Base64 encoding overflowed its buffer |
| `-11` | `STARTTLS` |
| `-12` | TLS upgrade, for example an untrusted certificate |
| `-13` | Clock not set: on the Nano ESP32 the certificate dates cannot be checked without it |

### Sending over implicit TLS

`Smtp.sendTls` encrypts from the first byte, the way SMTPS on port `465` expects, so it needs no `STARTTLS` step and no
extra library:

```java
int sent = Smtp.sendTls(
        host, 465,
        username, password,
        username, username,
        "Arduino alert", "Alarm activated");
```

It returns the same codes as `send`, except `-11`, `-12` and `-13`, which only apply to `STARTTLS`.

## POP3

[`Pop3Client`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/net/email/Pop3Client.java)
reads a mailbox over POP3S (implicit TLS, port `995`) and needs no extra library.

| Method | Argument | Notes |
|---|---|---|
| `Pop3Client.messageCount(...)` | host, port, username, password | returns the message count or `-1` to `-5` |
| `Pop3Client.readLatest(...)` | host, port, credentials, two buffers, `int[] status` | fills the headers and body buffers; returns the body length or `-1` to `-8` |
| `Pop3Client.readSubject(...)` | host, port, credentials, message number, a buffer | sends `TOP n 0`, so only the headers travel; returns the subject length or `-1` to `-6` |

### Counting messages

```java
int count = Pop3Client.messageCount(
        host, 995,
        username, password);
```

`messageCount` returns the count, or `-1` (connect), `-2` (greeting), `-3` (`USER`), `-4` (`PASS`) or `-5` (`STAT`).

### The newest message

`readLatest` retrieves the highest-numbered message. It writes the `From` and `Subject` lines into the headers buffer
and the dot-unstuffed plain-text body into the body buffer, both truncated to their buffer; `status[0]` receives the
headers length:

```java
byte[] headers = new byte[Pop3Client.DEFAULT_HEADERS_BUFFER_SIZE];
byte[] body = new byte[Pop3Client.DEFAULT_BODY_BUFFER_SIZE];
int[] status = new int[1];

int bodyLength = Pop3Client.readLatest(
        host, 995,
        username, password,
        headers, headers.length, body, body.length, status);
```

`bodyLength` is the body length on success. A negative value is `-6` for an empty mailbox, `-7` for a failed `RETR`, or
`-8` when the connection drops mid-message; the connection-stage codes match `messageCount`.

### One subject

`readSubject` is the cheap way to list a mailbox: it asks for the headers of one message (`TOP n 0`), not its body. The
message number is 1-based, so `1` is the oldest message still stored:

```java
byte[] subject = new byte[32];
int length = Pop3Client.readSubject(
        host, 995,
        username, password,
        1, subject, subject.length);
```

The result is the number of subject bytes written, `0` when the message has no `Subject` header, or `-5` (`TOP`) or
`-6` (connection dropped while streaming the headers). The subject is raw bytes in your buffer, not a `String`.

## Examples

The examples in
[`juno-examples`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email)
show each call in use:

- [`InboxCount`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email/InboxCount.java)
  polls `Pop3Client.messageCount` and shows the count on an LCD Keypad Shield.
- [`EmailHelloWorldLCD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email/EmailHelloWorldLCD.java)
  and [`EmailHelloWorldTFT`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email/EmailHelloWorldTFT.java)
  send a message to the mailbox and then check that the count goes up.
- [`EmailClient`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email/EmailClient.java)
  is a small menu with the message count, a list of subjects and a send action.
- [`NanoSmtpSendSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email/NanoSmtpSendSD.java),
  [`NanoSmtpStarttlsSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email/NanoSmtpStarttlsSD.java)
  and [`NanoPop3InboxSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email/NanoPop3InboxSD.java)
  run on a Nano ESP32 and read the Wi-Fi and mail settings from the SD card.

Build and upload one of them with the Maven plugin, then watch the output with `juno:monitor`:

```bash
./mvnw -f juno-examples/pom.xml compile juno:flash \
  -Djuno.main=io.github.jabrena.juno.api.hardware.net.email.EmailClient

./mvnw -f juno-examples/pom.xml juno:monitor \
  -Djuno.board=arduino-uno-r4-wifi
```

## Troubleshooting

- **`send` returns `-11` or `-12`:** the server did not offer `STARTTLS` on that port, or the upgrade failed. Confirm the
  host and port with another mail client, or use port `465` with `sendTls`.
- **`send` fails to build:** `ESP_SSLClient` is missing. Run `juno:install-deps`; without it, `arduino-cli` fails at
  the compile step, not at upload.
- **Connect fails (`-1`) right after `Wifi.begin`:** wait until `Wifi.status()` reports `Wifi.STATUS_CONNECTED` before
  the first call, as in [Wifi](../wifi).
- **`readSubject` returns `-5`:** the server may not support the `TOP` command. Check `messageCount` and `readLatest`
  first to confirm basic access.
- **A subject or body is cut short:** the buffer was smaller than the content. Enlarge it; the reads never grow.
- **The examples stop with `application.properties not found` or a missing `smtp.*`/`pop3.*` entry:** check the file is
  at the root of the card and that each key is spelled as in [Credentials](#credentials).
