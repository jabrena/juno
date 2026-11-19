---
title: "LEGO Mindstorms RCX remote control"
description: "The 9738 infrared handset that sends button commands to an RCX brick, read with RcxRemoteControl."
layout: page
---

{|
### Receive the RCX remote control

The LEGO Mindstorms 9738 remote control sends its buttons over the same infrared link the RCX brick listens to. With
only an IR receiver on the board, [`RcxRemoteControl`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/rcx/RcxRemoteControl.java)
turns the board into the brick's place: point the remote at the receiver and each click arrives as an
`RcxRemoteControlButton`, from the three message buttons and the six motor arrows to the five programs, Stop and Sound.

<img src="../../../images/lego/lego-rcx-remote-control.png" alt="LEGO Mindstorms 9738 remote control" width="280">

Create the remote once, at startup, and call `start()`: a virtual receiver thread decodes the frames and queues every
click, so the main loop takes them with `pollButton()` whenever it is ready, and gets `RcxRemoteControlButton.NONE`
when nothing is waiting. Clicking the same button again queues it again; holding a button down counts as one click.

```java
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxRemoteControl;
import io.github.jabrena.juno.remote.lego.rcx.RcxRemoteControlButton;
import io.github.jabrena.juno.util.Delay;

public final class RcxRemoteControlExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RcxRemoteControl remote = new RcxRemoteControl(new HXM121InfraredReceiver(2));
        Delay.millis(2000);
        Serial.println("Listening for the LEGO RCX remote");
        remote.start();

        while (true) {
            RcxRemoteControlButton button = remote.pollButton();
            if (button != RcxRemoteControlButton.NONE) {
                Serial.print("button: ");
                Serial.println(button.label());
            }
            Delay.millis(20);
        }
    }
}
```

[`RcxRemoteControlReceiver`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxRemoteControlReceiver.java)
prints each button with its mask on the UNO R4 WiFi, with an HX-M121 receiver on pin 2.
|}
