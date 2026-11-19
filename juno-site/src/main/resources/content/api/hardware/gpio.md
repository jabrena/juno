---
title: "Gpio"
description: "Driving and reading the board's pins with Gpio and DigitalOutput, starting with the Blink example."
layout: page
---

[`Gpio`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/gpio/Gpio.java)
is the selected board's pin resolver and GPIO port. Get it once from `MCU`, such as
`MCU.getGpio()`, then resolve
the names printed on the board and use the returned pin numbers with the GPIO operations. The resolver keeps the
application independent of the MCU's physical GPIO numbering. [`DigitalOutput`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/gpio/DigitalOutput.java)
is a lighter handle for a pin you only switch on and off.

| Method | Argument | Notes |
|---|---|---|
| `digitalPin(name)` | a name such as `"D2"` | resolves a board-specific digital pin |
| `analogPin(name)` | a name such as `"A0"` | resolves a board-specific analog pin |
| `pinMode(pin, mode)` | a pin, and `gpio.getInput()`, `getOutput()` or `getInputPullup()` | sets the direction of a pin |
| `digitalWrite(pin, high)` | a pin and a `boolean` | drives the pin high (`true`) or low (`false`) |
| `digitalRead(pin)` | a pin | returns `true` when the pin is high |
| `analogRead(pin)` | an analog pin, such as `gpio.analogPin("A0")` | returns the ADC reading |
| `analogWrite(pin, value)` | a pin and a value | PWM output on a pin that supports it |
| `getBuiltinLed()` | none | the pin of the board's onboard LED |

Use the portable digital names `D0` through `D13` and analog names `A0` through `A5`. The selected board resolves each
name to the logical pin value expected by its runtime; for example, Nano ESP32 `D13` is translated to ESP32-S3 GPIO48.
M5Stack Cardputer also accepts its documented `G*` and `GPIO*` aliases. A malformed, unknown, unavailable or null name
raises `IllegalArgumentException` in the generated program.

`DigitalOutput.of(pin)` sets an already-resolved pin as an output. When starting from a board name, use
`DigitalOutput.of(gpio, "D13")`; it resolves the name through the supplied board context and returns the same
allocation-free handle with `high()` and `low()`.

## Blink

[`Blink`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/gpio/Blink.java)
turns the board's onboard LED on and off forever: on for 500 milliseconds, off for 500 milliseconds, one full blink each
second. It is the first program to run on any board, because it needs no wiring:

```java
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.util.Delay;

public class Blink {

    public static void main(String[] args) {

        DigitalOutput led = DigitalOutput.of(MCU.getGpio().getBuiltinLed());

        while (true) {
            led.high();
            Delay.millis(500);

            led.low();
            Delay.millis(500);
        }
    }
}
```

Step by step:

1. `MCU.getGpio().getBuiltinLed()` asks the linked board which pin its LED is on. The same line works on every
   board, so the program names no pin.
2. `DigitalOutput.of(...)` configures that pin as an output and returns a handle to it.
3. The loop sets the pin high, waits with [`Delay`](../delay), sets it low and waits again. Creating the handle happens
   once, before the loop, so the loop allocates nothing.

Build, verify and upload it with the Maven plugin. `juno:verify` compiles the generated sketch with the Arduino toolchain
without touching the board:

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink \
  -Djuno.board=arduino-uno-r4-wifi

./mvnw -f juno-examples/pom.xml compile juno:flash \
  -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink \
  -Djuno.board=arduino-uno-r4-wifi
```

Blink prints nothing over [Serial](../serial), so there is no output to read with `juno:monitor`. The LED is the result.

## Reading an input

`digitalRead` and `analogRead` need a pin that is configured as an input. A button between a digital pin and ground,
with the internal pull-up enabled, reads `false` while the button is pressed:

```java
Gpio gpio = MCU.getGpio();
int buttonPin = gpio.digitalPin("D2");
gpio.pinMode(buttonPin, gpio.getInputPullup());

boolean pressed = !gpio.digitalRead(buttonPin);
```

`analogRead` on an analog pin returns the ADC value, which is a number from the converter rather than a voltage:

```java
int light = gpio.analogRead(gpio.analogPin("A0"));
```

## Troubleshooting

- **The LED does not blink:** check the board name in `-Djuno.board` matches the board you plugged in, and that
  `juno:flash` finished without an error.
- **A `digitalRead` always returns the same value:** the pin was not set as an input with `pinMode` first, or a
  button is not wired between the pin and ground.
