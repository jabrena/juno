---
title: "Showcase"
description: "Complete Juno programs that go beyond the examples in the docs: a network arena game, cloud publishing, a chess tactics trainer, a Zelda ocarina player and a polyphonic LEGO RCX."
layout: page
---

Showcase programs are complete, working applications built with Juno. Each one runs on real hardware, uses several
parts of the API together, and is the best place to see how a larger program is organized. Their source is in
[`juno-examples`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases).

## Mailbox

Mailbox is a remote messaging system for Claude. Run Mailbox and define an API key to be shared with Claude and the M5Stack Cardputer. On the M5Stack Cardputer you can send messages to Mailbox, and Claude periodically reviews the mailbox to process them and notify the status.

<img src="../images/showcase/mailbox2.png" alt="The M5Stack Cardputer ADV running the Mailbox client, its screen showing a message and the keyboard below it" width="480">

<img src="../images/showcase/mailbox1.png" alt="The Mailbox setup: a Java logo on one TFT shield, the mailbox screen on another with its message count and address, and the Cardputer below" width="480">

| Field | Value |
|---|---|
| Sources | [`usecases/mailbox`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/mailbox) |
| Board supported | [`Arduino Q`](../arduino-uno-q) (server), [`M5Stack Cardputer`](../m5stack-cardputer) (client) |
| Features used | HTTPS server, HTTPS client, self-signed certificates, Wi-Fi, SD card, API key header, TFT touch screen, Cardputer keyboard, MVC view router, JSON, serial output, clock, delays |

## Gladiator

Gladiator is a game for the ELEGOO 2.8" TFT touch shield. Two gladiators fight in a Roman arena,
each seen from its own board, while a third board keeps the arena. The solo mode runs a CPU-versus-CPU duel on a single
board, with no network needed.

<img src="../images/showcase/gladiator-solo.gif" alt="Gladiator Solo: the title, class selection, the versus view, then a CPU-versus-CPU bout until the result" width="320">

| Field | Value |
|---|---|
| Sources | [`games/gladiator`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/games/gladiator) |
| Board supported | [`Arduino Q`](../arduino-uno-q) |
| Features used | Wi-Fi, UDP, SD card, TFT touch screen, rotating 3D wireframe view, CPU opponent AI, lion, javelins, smoke, stamina, random numbers, clock |

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
