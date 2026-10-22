---
title: "M5Stack Cardputer"
description: "Send a Java Hello World message from the Cardputer and read it in the serial monitor."
layout: page
---

Juno's first Cardputer support adds the board target and an example that sends text over USB
serial. The example uses Juno's existing `Serial` and `Delay` APIs. The M5 device facade will be
the entry point for Cardputer peripherals as those APIs are added. Display, keyboard and other
Cardputer peripherals are not part of this first step.

## Hello World

[`M5CardputerHelloWorld`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/m5stack/M5CardputerHelloWorld.java)
prints `Hello, World!` once a second, after a short startup pause so the serial monitor has time to
connect.

Install the Juno Maven plugin, then run `juno:install-deps` once to install the M5Stack ESP32 board
package along with the other supported board cores and libraries. Compile and upload the example:

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.m5stack.M5CardputerHelloWorld
```

Open the serial monitor in another terminal:

```bash
./mvnw -f juno-examples/pom.xml juno:monitor \
  -Djuno.board=m5stack-cardputer
```

The monitor uses 115200 baud by default, matching `Serial.begin(BaudRate.BAUD_115200)`.
