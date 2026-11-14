---
title: "Mailbox: remote messaging for Claude"
description: "A remote messaging system connecting Claude with an M5Stack Cardputer and an HTTPS Juno server."
date: 2026-11-14
layout: post
link: /blog/mailbox/
image: showcase/mailbox1.png
tags: [mailbox, https, wifi]
---

Mailbox is a remote messaging system for Claude. Run Mailbox and define an API key to be shared with Claude and the
M5Stack Cardputer. On the M5Stack Cardputer you can send messages to Mailbox, and Claude periodically reviews the
mailbox to process them and notify the status.

<img src="../../images/showcase/mailbox2.png" alt="The M5Stack Cardputer ADV running the Mailbox client, its screen showing a message and the keyboard below it" width="480">

<img src="../../images/showcase/mailbox1.png" alt="The Mailbox setup: a Java logo on one TFT shield, the mailbox screen on another with its message count and address, and the Cardputer below" width="480">

| Field | Value |
|---|---|
| Sources | [`usecases/mailbox`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/mailbox) |
| Boards supported | [`Arduino Q`](../../arduino-uno-q) (server), [`M5Stack Cardputer`](../../m5stack-cardputer) (client) |
| Features used | HTTPS server, HTTPS client, self-signed certificates, Wi-Fi, SD card, API key header, TFT touch screen, Cardputer keyboard, MVC view router, JSON, serial output, clock, delays |
