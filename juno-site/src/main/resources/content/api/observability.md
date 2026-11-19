---
title: "Observability"
description: "Observability: Serial is for USB output and input, and Syslog is for remote logging over Wi-Fi."
layout: page
---

Serial is for USB: printing and reading the program's output and input over the board's USB cable, the first thing to try. Syslog is for remote logging: it sends RFC 5424 log datagrams over Wi-Fi, so `juno:monitor` can follow the board without a cable.

| API | What it provides | Complete example |
|---|---|---|
| [Serial](serial) | Printing and reading over USB serial, the main way to see what a program is doing on a board. | [`SerialCounter`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialCounter.java) |
| [Syslog](syslog) | Sending allocation-free RFC 5424 log datagrams over Wi-Fi to `juno:monitor`, so a board can be followed without USB. | [`NanoCarrierRcxRegulatedDifferentialDrive`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxRegulatedDifferentialDrive.java) |
