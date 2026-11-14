---
title: "Gladiator: a network arena game"
description: "A Roman arena game for the ELEGOO TFT touch shield, with solo and networked battles."
date: 2026-11-14
layout: post
link: /blog/gladiator/
image: showcase/gladiator-solo.gif
tags: [games, wifi, udp]
---

Gladiator is a game for the ELEGOO 2.8" TFT touch shield. Two gladiators fight in a Roman arena, each seen from its
own board, while a third board keeps the arena. The solo mode runs a CPU-versus-CPU duel on a single board, with no
network needed.

<img src="../../images/showcase/gladiator-solo.gif" alt="Gladiator Solo: the title, class selection, the versus view, then a CPU-versus-CPU bout until the result" width="320">

| Field | Value |
|---|---|
| Sources | [`games/gladiator`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/games/gladiator) |
| Board supported | [`Arduino Q`](../../arduino-uno-q) |
| Features used | Wi-Fi, UDP, SD card, TFT touch screen, rotating 3D wireframe view, CPU opponent AI, lion, javelins, smoke, stamina, random numbers, clock |
