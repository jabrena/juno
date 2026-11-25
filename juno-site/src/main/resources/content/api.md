---
title: "APIs"
description: "The hardware, network and AI APIs a Juno program can use."
layout: page
---

Juno's APIs fall into six categories. Each category page lists its APIs with what they provide and a complete example.

| Category | APIs | What it covers |
|---|---|---|
| [AI](ai) | [JEV decision models](ai/jev), [JLM language models](ai/lm) | JEV selects a legal action from a caller-owned state and action list. JLM1 generates text one character at a time from a model linked into the firmware. |
| [Observability](observability) | [Serial](observability/serial), [Syslog](observability/syslog) | Serial is for USB output and input, the first thing to try. Syslog is for remote logging over Wi-Fi, so `juno:monitor` can follow a board without a cable. |
| [Storage](storage) | [SD Card](storage/sdcard), [SecretStore](storage/secure-element) | SD Card is for files on a microSD card, such as configuration and Wi-Fi credentials read at boot. SecretStore is for named secrets kept in the secure element or the ESP32's NVS, out of the SD card and the firmware image. |
| [Internet](internet) | [Wifi](internet/wifi), [Time](internet/time), [HTTP client](internet/httpclient), [HTTP server](internet/httpserver), [Email](internet/email) | Wifi joins a network. Time reads UTC over SNTP and measures intervals. The HTTP client sends requests and reads JSON responses, and the HTTP server answers requests on the board. Email sends mail over SMTP and reads a mailbox over POP3. |
| [Utils](utils) | [MVC](utils/mvc) | MVC structures a multi-screen app with a model, views, a navigator and a router, so each screen stays separate from the logic that drives it. |
| [Hardware](hardware) | [Gpio](hardware/gpio), [Delay](hardware/delay), [Clock](hardware/clock) | Gpio drives and reads the board's pins. Delay pauses a program for a fixed time. Clock reads the board's timer for measuring intervals. |
