---
title: "LEGO ecosystem"
description: "Remote-controlling LEGO Powered Up hubs and Mindstorms RCX bricks from Java."
layout: page
---

LEGO has made several generations of motorised, remote-controlled systems. Juno drives three of them today, and two more are pending. Each system has its own page.

| System | Since | What it is | Protocol used | Board with support | Juno class |
|---|---|---|---|---|---|
| [LEGO Powered Up](lego-powered-up) | 2017 | Hubs (City, Technic, BOOST, DUPLO Train) with motors and sensors, driven by LEGO's apps | Bluetooth LE | UNO R4 WiFi, AI Thinker ESP32-CAM, Nano ESP32, M5Stack Cardputer | [`RemotePoweredUpHub`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/poweredup/RemotePoweredUpHub.java) |
| LEGO Mindstorms EV3 | 2013 | The third Mindstorms brick: four motor outputs, four sensor inputs, a screen and a speaker, running Linux | Bluetooth Classic (serial port profile), LEGO's EV3 direct commands | — | Pending |
| [LEGO Power Functions](lego-power-functions) | 2007 | Motors, lights and servos plugged into an IR Receiver (8884), normally driven by the IR remote (8885) | Infrared, through an IR LED on the board | UNO R4 WiFi, Nano ESP32 | [`RemotePF`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/pf/RemotePF.java) |
| LEGO Mindstorms NXT | 2006 | The second Mindstorms brick: three motor outputs, four sensor inputs, a screen and a speaker | Bluetooth Classic (serial port profile), LEGO's NXT direct commands | — | Pending |
| [LEGO Mindstorms RCX](lego-mindstorms-rcx) | 1998 | The yellow programmable brick: three motor outputs, three sensor inputs, an LCD and a speaker | Infrared, through an IR LED and receiver on the board | UNO R4 WiFi, Nano ESP32 | [`RemoteRcx`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/rcx/RemoteRcx.java) |
| [LEGO Mindstorms RCX remote control](lego-mindstorms-rcx-remote-control) | 1998 | The 9738 infrared handset that sends button commands to an RCX brick | Infrared, using RCX remote-control frames | UNO R4 WiFi, Nano ESP32 | [`RcxRemoteControl`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/rcx/RcxRemoteControl.java) |
