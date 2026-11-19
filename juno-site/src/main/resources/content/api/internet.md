---
title: "Internet"
description: "Internet: Wifi, Time, HTTP client and server, and Email for programs that talk to networks and services."
layout: page
---

Wifi joins a network. Time reads UTC over SNTP and measures intervals. The HTTP client sends requests and reads JSON responses. The HTTP server answers requests on the board. Email sends mail over SMTP and reads a mailbox over POP3.

| API | What it provides | Complete example |
|---|---|---|
| [Wifi](wifi) | Joining a Wi-Fi network on the boards that have a radio. | [`WifiStatus`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/wifi/WifiStatus.java) |
| [Time](time) | Reading UTC with `LocalDateTime.now()` over SNTP, and measuring elapsed time with `Clock`. | [`SntpTime`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/udp/SntpTime.java) |
| [HTTP client](httpclient) | HTTP and HTTPS requests with the JDK's `java.net.http` client, from the UNO R4 WiFi up, and reading JSON responses. | [`SingleHttpsGet`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/SingleHttpsGet.java) |
| [HTTP server](httpserver) | Serving HTTP and HTTPS requests from a program, with routing by path and a certificate on the SD card. | [`HttpServerStatus`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/HttpServerStatus.java) |
| [Email](email) | Sending mail over SMTP with `Smtp`, and reading a mailbox over POP3 with `Pop3Client`. | [`EmailClient`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email/EmailClient.java) |
