---
title: "Gpio"
description: "Driving and reading the board's pins with Gpio and DigitalOutput, starting with the Blink example."
layout: page
---

[`Gpio`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/gpio/Gpio.java)
is the board's set of pins. Get it once with `ArduinoUno.getGpio()` or `ArduinoNano.getGpio()`, then set a pin as an
output, write it high or low, or read an input. [`DigitalOutput`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/gpio/DigitalOutput.java)
is a lighter handle for a pin you only switch on and off.

| Method | Argument | Notes |
|---|---|---|
| `pinMode(pin, mode)` | a pin, and `Gpio.getInput()`, `getOutput()` or `getInputPullup()` | sets the direction of a pin |
| `digitalWrite(pin, high)` | a pin and a `boolean` | drives the pin high (`true`) or low (`false`) |
| `digitalRead(pin)` | a pin | returns `true` when the pin is high |
| `analogRead(pin)` | an analog pin, such as `Gpio.getA0()` | returns the ADC reading |
| `analogWrite(pin, value)` | a pin and a value | PWM output on a pin that supports it |
| `getBuiltinLed()` | none | the pin of the board's onboard LED |

Pins are portable numbers, not the chip's own GPIO numbers. `Gpio.getD0()` through `Gpio.getD13()` return `0` to `13`, and
`Gpio.getA0()` through `Gpio.getA5()` return `14` to `19`. The compiler translates these numbers to the linked board's
pins, so the same program runs on a UNO R4 WiFi or a Nano ESP32 without changes.

`DigitalOutput.of(pin)` sets the pin as an output and returns a handle with `high()` and `low()`.

## Blink

[`Blink`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/gpio/Blink.java)
turns the board's onboard LED on and off forever: on for 500 milliseconds, off for 500 milliseconds, one full blink each
second. It is the first program to run on any board, because it needs no wiring:

```java
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.util.Delay;

public class Blink {

    public static void main(String[] args) {

        DigitalOutput led = DigitalOutput.of(ArduinoUno.getGpio().getBuiltinLed());

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

1. `ArduinoUno.getGpio().getBuiltinLed()` asks the linked board which pin its LED is on. The same line works on every
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

./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink \
  -Djuno.board=arduino-uno-r4-wifi
```

Blink prints nothing over [Serial](../serial), so there is no output to read with `juno:monitor`. The LED is the result.

## Reading an input

`digitalRead` and `analogRead` need a pin that is configured as an input. A button between a digital pin and ground,
with the internal pull-up enabled, reads `false` while the button is pressed:

```java
Gpio gpio = ArduinoUno.getGpio();
gpio.pinMode(Gpio.getD2(), Gpio.getInputPullup());

boolean pressed = !gpio.digitalRead(Gpio.getD2());
```

`analogRead` on an analog pin returns the ADC value, which is a number from the converter rather than a voltage:

```java
int light = gpio.analogRead(Gpio.getA0());
```

## Troubleshooting

- **The LED does not blink:** check the board name in `-Djuno.board` matches the board you plugged in, and that
  `juno:upload` finished without an error.
- **A `digitalRead` always returns the same value:** the pin was not set as an input with `pinMode` first, or a
  button is not wired between the pin and ground.
