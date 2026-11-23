---
title: "Choosing a Juno board: memory, concurrency and connectivity compared"
description: "A practical comparison of Juno boards for Java programs, from UNO R3 and UNO R4 to ESP32, Pico 2 W and Portenta H7."
date: 2026-11-23
layout: post
link: /blog/2026-11-28-juno-board-comparison/
tags: [boards, comparison, getting-started]
blogVisible: true
---

The best Juno board depends less on processor speed than on the shape of the program. A blinking LED, a Wi-Fi sensor
node, a camera application and a dual-core controller have very different memory and concurrency needs.

## At a glance

| Board | Java arena default | Default carriers | Scheduler slots, including `main` | HTTP leases | Best fit |
|---|---:|---:|---:|---:|---|
| Arduino UNO R3 | 1 KiB | 1 AVR scheduler | 4 ceiling* | Unsupported | Small GPIO and serial programs |
| Arduino UNO R4 WiFi | 8 KiB | 1 | Arena-bounded | 2 | Simple Wi-Fi and Java language experiments |
| Nano ESP32 | 64 KiB | 2 | 16 including `main` | 4 | General Wi-Fi and concurrent I/O |
| ESP32-CAM | 64 KiB | 2 | 16 including `main` | 4 | Camera plus network applications |
| M5Stack Cardputer | 64 KiB | 2 | 16 including `main` | 4 | Portable UI, input and Wi-Fi |
| Raspberry Pi Pico 2 W | 64 KiB | 2 | 16 including `main` | 2 | Low-cost wireless control and sensors |
| Arduino Portenta H7 | 64 KiB | M7 + M4 | 16 including `main` | 4 | Dual-core workloads and richer peripherals |

\* The UNO R3 ceiling is not practical with default sizing: three extra 512-byte stacks plus the 1 KiB arena already
exceed its 2 KiB SRAM before other static data and core overhead. `StructuredTaskScope` and `java.net.http` are
unsupported there.

These are runtime defaults and ceilings, not application capacity guarantees. Java arrays and Java HTTP response buffers
consume the Java arena. Native task stacks, driver buffers, Wi-Fi, TLS and camera buffers use a separate native-memory
budget. On boards with additional external memory, Juno's arena still uses the configured internal board memory.

## Pick by workload

Choose the UNO R3 when the program is tiny and does not need `java.net.http`. Its SRAM budget is extremely tight, and
the four-slot scheduler ceiling is not the same as four practical application tasks.

Choose the UNO R4 when you want a familiar Arduino form factor, a larger arena than AVR and built-in Wi-Fi. It has one
carrier, so virtual threads overlap waits but do not execute Java on multiple carriers.

Choose an ESP32 board when connectivity and concurrent I/O are central. The dual-carrier runtime and four HTTP leases
provide more headroom, but a camera or display can consume the native RAM that a networking example assumes is available.
The ESP32-CAM is the camera-oriented option, while the Cardputer trades camera capacity for its display, keyboard and
audio peripherals.

Choose the Pico 2 W when low-cost wireless control and a modest runtime are more important than the largest HTTP fan-out.
It has two carriers and a 64 KiB arena, but its HTTP transport limit is two exchanges.

Choose the Portenta H7 for demanding peripherals or a workload that can benefit from M7/M4 execution. Calls that require
M7-owned libraries may cross from an M4 task to an M7 worker, so measure coordination cost instead of assuming that two
cores automatically double throughput.

## A decision rule

1. Start with the peripherals your final firmware will actually use.
2. Check arena estimates, stack requirements and native linker/RAM reports.
3. Count active task slots separately from HTTP transport leases.
4. Measure the worst response size and the slowest expected operation.
5. Choose the smallest board that leaves a useful safety margin.

For example, a wireless sensor needing two overlapping HTTPS exchanges can shortlist UNO R4, Pico 2 W and the ESP32
boards by transport capacity, then remove any candidate whose Wi-Fi, TLS and sensor buffers leave too little native RAM.
The final choice comes from measuring that complete workload, not from the table's maximum counts.

The board table is a starting point. The correct choice is the board that can run the complete program—with its real
buffers, peripherals, network behavior and failure paths—without relying on the headline maximum.
