---
title: "Lichess Daily Challenges: a chess tactics trainer"
description: "A chess tactics trainer for the UNO R4 WiFi and TFT touch shield, powered by the Lichess API."
date: 2026-11-14
layout: post
link: /blog/lichess/
image: games/lichess-daily.gif
tags: [lichess, chess, wifi]
---

[`LichessDailyChallenges`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/lichess/LichessDailyChallenges.java)
is a chess tactics trainer for the UNO R4 WiFi and its TFT touch shield. The cover leads to a challenge menu built with
the [MVC](../../mvc) pattern. The daily puzzle and a fresh public puzzle are fetched from Lichess as a position and
its solution, and the player finds the moves on the touch board.

The program reads `wifi.ssid` and `wifi.password` from `application.properties` on the shield's microSD card. Its
buffers are allocated once at startup, so the frame loop allocates nothing.

<img src="../../images/games/lichess-daily.gif" alt="Lichess daily puzzle on the TFT shield: the board with the player's moves and Lichess's replies, then the win screen" width="240">

| Field | Value |
|---|---|
| Sources | [`usecases/lichess`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/lichess) |
| Board supported | [`UNO R4 WiFi`](../../arduino-uno-r4-wifi) |
| Features used | Wi-Fi, HTTPS, JSON, SD card, TFT touch screen, MVC view router, chess piece rendering, clock, serial output, delays |
