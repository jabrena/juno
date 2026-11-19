---
title: "LEGO Power Functions"
description: "LEGO Power Functions motors and lights, driven over infrared with RemotePF."
layout: page
---

{|
[`RemotePF`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/pf/RemotePF.java)
drives the red (A) and blue (B) outputs of a LEGO Power Functions IR Receiver 8884. `PFLink` sends the 38 kHz
Power Functions protocol through an HX-53 emitter on a GPIO pin or a Grove infrared emitter. The link and receiver
have been tested with Power Functions motors; Juno also exposes the outputs as `PFMotor` instances implementing the
board-independent `DCMotor` contract.

<img src="../../../images/lego/lego-power-functions.jpg" alt="LEGO Power Functions IR Receiver 8884" width="280">

With a Grove Base Shield, connect the clear Grove infrared emitter to D3 and point it at the receiver. Create the link
once at startup, then bind motors to the receiver channel and output:

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.pf.PFChannel;
import io.github.jabrena.juno.remote.lego.pf.PFLink;
import io.github.jabrena.juno.remote.lego.pf.PFMotor;
import io.github.jabrena.juno.remote.lego.pf.PFPort;
import io.github.jabrena.juno.remote.lego.pf.RemotePF;
import io.github.jabrena.juno.util.Delay;

public final class PowerFunctionsExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        GroveBaseShield shield = new GroveBaseShield();
        PFLink link = new PFLink(new GroveInfraredEmitter(shield.getD3()));
        RemotePF remote = new RemotePF(link);
        PFMotor left = new PFMotor(remote, PFChannel.CHANNEL_1, PFPort.RED);
        PFMotor right = new PFMotor(remote, PFChannel.CHANNEL_1, PFPort.BLUE);

        while (true) {
            left.setPower(70);
            right.setPower(70);
            left.forward();
            right.forward();
            Delay.millis(500);
            left.backward();
            right.backward();
            Delay.millis(500);
            left.flt();
            right.flt();
            Delay.millis(1000);
        }
    }
}
```

`PFMotor.setPower` accepts `0..100` percent and maps it to the receiver's seven protocol speed steps. Use
`forward()`, `backward()`, `stop()` for an active brake, or `flt()` to let an output coast. The receiver channel switch
must match the `PFChannel` used by both motors. See [`PFPowerSequenceWithGrove`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/pf/PFPowerSequenceWithGrove.java)
and [`MagicSquarePFWithGrove`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/pf/MagicSquarePFWithGrove.java)
for complete examples.
|}
