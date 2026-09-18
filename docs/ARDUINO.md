# Arduino CLI workflow

This guide covers installing `arduino-cli`, and using it to compile, upload, and monitor
Juno-generated sketches on a real UNO R4 board. See the main [README](../README.md) for the
project overview and the Java API.

## Install Arduino CLI on macOS

Install Arduino CLI with Homebrew and confirm that it is available:

```bash
brew update
brew install arduino-cli
arduino-cli version
```

Then update the board index and install the UNO R4 toolchain:

```bash
arduino-cli core update-index
arduino-cli core install arduino:renesas_uno
arduino-cli core list
```

Connect the board and find its serial port:

```bash
arduino-cli board list
```

The port usually looks like `/dev/cu.usbmodem...` on macOS. Keep that value for the upload step.

## Build the Juno compiler and examples

From the repository root:

```bash
./mvnw clean package
```

This builds the `juno` module's compiler jar (`juno/target/juno-0.1.0-SNAPSHOT.jar`) and
compiles every program under `juno-examples/src/main/java` (`juno-examples/target/classes`). Every example
below reuses the same classpath, `juno-examples/target/classes:juno/target/classes`, no matter which
Juno API it uses.

## Compile and upload a sketch

Generate the `.ino` sketch for a given example's main class, then hand it to `arduino-cli`:

```bash
java -jar juno/target/juno-0.1.0-SNAPSHOT.jar compile \
  --main <ExampleClassName> \
  --classpath juno-examples/target/classes:juno/target/classes

arduino-cli compile --fqbn arduino:renesas_uno:unor4wifi build/juno/<ExampleClassName>
arduino-cli upload \
  --port /dev/cu.YOUR_PORT \
  --fqbn arduino:renesas_uno:unor4wifi \
  build/juno/<ExampleClassName>
```

The generated sketch is `build/juno/<ExampleClassName>/<ExampleClassName>.ino`. Replace
`/dev/cu.YOUR_PORT` with the port reported by `arduino-cli board list`, and use
`arduino:renesas_uno:minima` instead of `arduino:renesas_uno:unor4wifi` for the UNO R4 Minima.

### Example: Blink

```bash
java -jar juno/target/juno-0.1.0-SNAPSHOT.jar compile \
  --main Blink \
  --classpath juno-examples/target/classes:juno/target/classes

arduino-cli compile --fqbn arduino:renesas_uno:unor4wifi build/juno/Blink
arduino-cli upload \
  --port /dev/cu.YOUR_PORT \
  --fqbn arduino:renesas_uno:unor4wifi \
  build/juno/Blink
```

The board's built-in LED (pin 13) should start blinking once a second.

### Example: SerialCounter (reading Serial output)

[`juno-examples/src/main/java/SerialCounter.java`](../juno-examples/src/main/java/SerialCounter.java) counts
up once a second over USB serial, so it doubles as a check that the toolchain and the board's
serial port both work end to end:

```bash
java -jar juno/target/juno-0.1.0-SNAPSHOT.jar compile \
  --main SerialCounter \
  --classpath juno-examples/target/classes:juno/target/classes

arduino-cli compile --fqbn arduino:renesas_uno:unor4wifi build/juno/SerialCounter
arduino-cli upload \
  --port /dev/cu.YOUR_PORT \
  --fqbn arduino:renesas_uno:unor4wifi \
  build/juno/SerialCounter
```

Then open the serial monitor at the same baud rate the sketch uses (`Serial.begin(9600)`):

```bash
arduino-cli monitor -p /dev/cu.YOUR_PORT -c baudrate=9600
```

The intended output is `0`, `1`, `2`, ... once a second. Press `Ctrl+C` to exit the monitor.

Known limitation: USB serial output is not currently reliable when the Java entry point never
returns. Juno invokes the entry point from Arduino `setup()`, and an infinite Java loop can prevent
the UNO R4 USB service from being polled normally. During the runtime-risk board test the monitor
connected successfully but received no bytes. LED-based examples remain reliable while this backend
integration issue is addressed.

### LED matrix examples

Any of the `LedMatrix*` example classes (see the main README's Java API section for the full
list) follow the same pattern as `Blink` above — just swap in the class name, e.g. `--main
LedMatrixHeart`. They all draw on the UNO R4 WiFi's built-in 12x8 LED matrix and are not supported
on the UNO R4 Minima, which lacks that matrix.

### Example: RatonLoco (USB mouse control)

[`juno-examples/src/main/java/RatonLoco.java`](../juno-examples/src/main/java/RatonLoco.java) is a
port of [raton-loco.ino](https://github.com/jabrena/raton-loco/blob/main/arduino/raton-loco.ino):
it blinks the built-in LED, then drags the host computer's mouse cursor in a square (right, down,
left, up) over USB HID.

**Warning:** once uploaded, this sketch takes control of the real mouse cursor on whatever
computer the board's USB cable is plugged into — unplug the board or re-flash it with a different
sketch to stop it.

It needs the `Mouse` library (not bundled with the `arduino:renesas_uno` core) and a board with
native USB (UNO R4 WiFi/Minima):

```bash
arduino-cli lib install Mouse

java -jar juno/target/juno-0.1.0-SNAPSHOT.jar compile \
  --main RatonLoco \
  --classpath juno-examples/target/classes:juno/target/classes

arduino-cli compile --fqbn arduino:renesas_uno:unor4wifi build/juno/RatonLoco
arduino-cli upload \
  --port /dev/cu.YOUR_PORT \
  --fqbn arduino:renesas_uno:unor4wifi \
  build/juno/RatonLoco
```

**Re-uploading over a running HID sketch:** `arduino-cli upload` normally resets the board into
its bootloader by opening the serial port at 1200 baud and closing it again. That trick relies on
the sketch promptly servicing the USB connection, but `RatonLoco`'s `loop()` is busy driving the
mouse (`Mouse.move` + `delay`), so the automatic reset can be missed. The board may also
re-enumerate under a different `/dev/cu.*` path once it drops into the bootloader. If the upload
hangs or fails, double-tap the board's physical reset button to force it into the bootloader
manually (the onboard LED pulses), then immediately re-run the `arduino-cli upload` command — and
run `arduino-cli board list` first if you're unsure which port it came back on.
