---
title: "Showcase"
description: "Complete Juno programs that go beyond the examples in the docs: a network arena game, cloud publishing, a chess tactics trainer, a Zelda ocarina player and a polyphonic LEGO RCX."
layout: page
---

Showcase programs are complete, working applications built with Juno. Each one runs on real hardware, uses several
parts of the API together, and is the best place to see how a larger program is organized. Their source is in
[`juno-examples`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases).

## Gladiator

[Gladiator](../games#gladiator) is a game for the ELEGOO 2.8" TFT touch shield. Two gladiators fight in a Roman arena,
each seen from its own board, while a third board keeps the arena. The solo mode runs a CPU-versus-CPU duel on a single
board, with no network needed.

<img src="../images/games/gladiator-solo.gif" alt="Gladiator Solo: the title, class selection, the versus view, then a CPU-versus-CPU bout until the result" width="320">

| Field | Value |
|---|---|
| Sources | [`games/gladiator`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/games/gladiator) |
| Board supported | [`Arduino Q`](../arduino-uno-q) |
| Features used | Wi-Fi, UDP, SD card, TFT touch screen, rotating 3D wireframe view, CPU opponent AI, lion, javelins, smoke, stamina, random numbers, clock |

## Doom

[DOOM](../games#doom) runs in landscape on the ELEGOO 2.8" TFT touch shield, drawn as a wireframe with DOOM's own BSP
renderer. Pick **HUMAN** (touch to turn, walk and fire) or **CPU** (an autopilot), then the episode and skill. The game
reads your own `DOOM1.WAD` from the microSD card.

<img src="../images/games/doom-cpu.gif" alt="DOOM on the TFT shield in the emulator: the CPU on I'm Too Young to Die clears E1M1 and E1M2 with the pistol, then picks up the shotgun on E1M3 and fights there until it dies" width="320">

| Field | Value |
|---|---|
| Sources | [`games/doom`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/games/doom) |
| Board supported | [`Arduino Q`](../arduino-uno-q) |
| Features used | TFT touch screen, SD card with RandomAccessFile, MVC view router, BSP wireframe renderer, CPU autopilot, serial output, delays, clock |

## Supabase

[`SupabaseGroveTemperatures`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/supabase/publisher/SupabaseGroveTemperatures.java)
reads three Grove temperature sensors once a minute and inserts them as one row of a Supabase table through its
PostgREST API. It shows Wi-Fi, HTTPS requests with caller-owned buffers, and a timestamp taken once
from `LocalDateTime.now()` and then advanced by the board's clock, so the program makes no extra time requests.

A failing sensor is sent as `null`, so one broken sensor does not stop the other two from being published. The board is
a public client, so it uses a publishable key that is protected by Row Level Security. The
[UNO R4 WiFi](../arduino-uno-r4-wifi) page describes the wiring.

| Field | Value |
|---|---|
| Sources | [`usecases/supabase`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/supabase) |
| Board supported | [`UNO R4 WiFi`](../arduino-uno-r4-wifi), [`UNO Q`](../arduino-uno-q) |
| Features used | Wi-Fi, HTTPS, Grove sensors, Grove Base Shield, LocalDateTime, clock, TFT touch screen, MVC view router, serial output, delays |

## Lichess

[`LichessDailyChallenges`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/lichess/LichessDailyChallenges.java)
is a chess tactics trainer for the UNO R4 WiFi and its TFT touch shield. The cover leads to a challenge menu built with
the [MVC](../mvc) pattern. The daily puzzle and a fresh public puzzle are fetched from Lichess as a position and its
solution, and the player finds the moves on the touch board.

The program reads `wifi.ssid` and `wifi.password` from `application.properties` on the shield's microSD card. Its
buffers are allocated once at startup, so the frame loop allocates nothing.

<img src="../images/games/lichess-daily.gif" alt="Lichess daily puzzle on the TFT shield: the board with the player's moves and Lichess's replies, then the win screen" width="240">

| Field | Value |
|---|---|
| Sources | [`usecases/lichess`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/lichess) |
| Board supported | [`UNO R4 WiFi`](../arduino-uno-r4-wifi) |
| Features used | Wi-Fi, HTTPS, JSON, SD card, TFT touch screen, MVC view router, chess piece rendering, clock, serial output, delays |

## Polyphonic RCX

[`ZeldaPolyphonicRcx`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/zeldapoliphonicrcx/ZeldaPolyphonicRcx.java)
plays the opening of Ocarina of Time as three synchronized instruments, one on each of the LEGO RCX bricks' channels one,
two and three. The three bricks sound together, so the melody and its harmony come from different bricks.

The same program runs on an Arduino Nano ESP32 through the Nano Connector Carrier, in
[`ZeldaPolyphonicRcxNano`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/zeldapoliphonicrcx/ZeldaPolyphonicRcxNano.java).
Its infrared transmitter is on a Grove socket and its receiver on the Grove I2C socket. See the
[LEGO](../lego-ecosystem) page for the RCX link. This version adds a bass instrument on a fourth brick, channel 4.

| Field | Value |
|---|---|
| Sources | [`usecases/zeldapoliphonicrcx`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/zeldapoliphonicrcx) |
| Board supported | [`Arduino Nano ESP32`](../arduino-nano-esp32) |
| Features used | Infrared link, RCX channels, multi-brick playback, RCX indicator lights, Grove infrared emitter and receiver, Nano Connector Carrier, serial output, delays |
