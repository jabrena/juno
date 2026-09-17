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

You should see `0`, `1`, `2`, ... printed once a second. Press `Ctrl+C` to exit the monitor.

### LED matrix examples

Any of the `LedMatrix*` example classes (see the main README's Java API section for the full
list) follow the same pattern as `Blink` above — just swap in the class name, e.g. `--main
LedMatrixHeart`. They all draw on the UNO R4 WiFi's built-in 12x8 LED matrix and are not supported
on the UNO R4 Minima, which lacks that matrix.
