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

This builds the `juno` module's compiler jar and hardware API classes together
(`juno/target/juno-0.1.0-SNAPSHOT.jar`, `juno/target/classes`), and compiles every program under
`juno-examples/src/main/java` (`juno-examples/target/classes`). Every example below reuses the
same classpath, `juno-examples/target/classes:juno/target/classes`, no matter which Juno API it
uses.

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
`/dev/cu.YOUR_PORT` with the port reported by `arduino-cli board list`.

### Example: Blink

```bash
java -jar juno/target/juno-0.1.0-SNAPSHOT.jar compile \
  --main io.github.jabrena.juno.api.Blink \
  --classpath juno-examples/target/classes:juno/target/classes

arduino-cli compile --fqbn arduino:renesas_uno:unor4wifi build/juno/Blink
arduino-cli upload \
  --port /dev/cu.YOUR_PORT \
  --fqbn arduino:renesas_uno:unor4wifi \
  build/juno/Blink
```

The board's built-in LED (pin 13) should start blinking once a second.

### Example: SerialCounter (reading Serial output)

[`juno-examples/src/main/java/io/github/jabrena/juno/api/io/usb/SerialCounter.java`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/usb/SerialCounter.java) counts
up once a second over USB serial, so it doubles as a check that the toolchain and the board's
serial port both work end to end:

```bash
java -jar juno/target/juno-0.1.0-SNAPSHOT.jar compile \
  --main io.github.jabrena.juno.api.io.usb.SerialCounter \
  --classpath juno-examples/target/classes:juno/target/classes

arduino-cli compile --fqbn arduino:renesas_uno:unor4wifi build/juno/SerialCounter
arduino-cli upload \
  --port /dev/cu.YOUR_PORT \
  --fqbn arduino:renesas_uno:unor4wifi \
  build/juno/SerialCounter
```

Then open the serial monitor at the same baud rate the sketch uses
(`Serial.begin(BaudRate.BAUD_9600)`):

```bash
arduino-cli monitor -p /dev/cu.YOUR_PORT -c baudrate=9600
```

The intended output is `0`, `1`, `2`, ... once a second. Press `Ctrl+C` to exit the monitor.

Generated sketches keep the UNO R4's USB service polled even though the Java entry point never
returns (Juno invokes it from Arduino `setup()`): the backend emits a `yield()` call at every loop
backedge, and the generated `yield()` override polls `Serial`'s boolean conversion, the core's
supported hook into TinyUSB's `tud_task()`.

### LED matrix examples

Any of the `LedMatrix*` example classes (see the main README's Java API section for the full
list) follow the same pattern as `Blink` above — just swap in the class name, e.g. `--main
LedMatrixHeart`. They all draw on the UNO R4 WiFi's built-in 12x8 LED matrix.

### Example: RatonLoco (USB mouse control)

[`juno-examples/src/main/java/io/github/jabrena/juno/api/io/hid/RatonLoco.java`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/hid/RatonLoco.java) is a
port of [raton-loco.ino](https://github.com/jabrena/raton-loco/blob/main/arduino/raton-loco.ino):
it blinks the built-in LED, then drags the host computer's mouse cursor in a square (right, down,
left, up) over USB HID.

**Warning:** once uploaded, this sketch takes control of the real mouse cursor on whatever
computer the board's USB cable is plugged into — unplug the board or re-flash it with a different
sketch to stop it.

It needs the `Mouse` library (not bundled with the `arduino:renesas_uno` core) and a board with
native USB (UNO R4 WiFi):

```bash
arduino-cli lib install Mouse

java -jar juno/target/juno-0.1.0-SNAPSHOT.jar compile \
  --main io.github.jabrena.juno.api.io.hid.RatonLoco \
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

## Experimental: Cortex-M4 assembly backend

`juno asm` emits GNU ARM (Cortex-M4) assembly straight from Juno IR instead of Arduino C++: every
reachable method becomes its own function (real calls between them, including AAPCS stack-passed
arguments beyond the first four), with branches, `switch`, `int` arithmetic/comparisons, fixed-size
arrays, arena-allocated objects with fields, mutable static fields, GPIO/delay, `LedMatrix`, and
`Serial`. Anything else (long/float/double, more than 2 array dimensions) fails at generation time
with a clear error rather than emitting unchecked code. Every value and local variable lives in a
fixed stack-frame slot rather than a register, so this doesn't run out of registers regardless of
how large or branchy a method is.

```bash
java -jar juno/target/juno-0.1.0-SNAPSHOT.jar asm \
  --main io.github.jabrena.juno.api.Blink \
  --classpath juno-examples/target/classes:juno/target/classes
```

This writes `build/juno/Blink/Blink.S`. It isn't a sketch by itself: put it in its own sketch folder
(don't share a folder with the normal `.ino` build of the same class — two `setup()`/`loop()`
definitions in one sketch won't compile) alongside a tiny `.ino` that declares the generated function
`extern "C"` and calls it from `setup()`:

```cpp
// build/juno/BlinkAsm/BlinkAsm.ino, next to a copy of the generated Blink.S
extern "C" void juno_Blink_asm();

void setup() {
  juno_Blink_asm();
}

void loop() {
}
```

```bash
arduino-cli compile --fqbn arduino:renesas_uno:unor4wifi build/juno/BlinkAsm
arduino-cli upload --port /dev/cu.YOUR_PORT --fqbn arduino:renesas_uno:unor4wifi build/juno/BlinkAsm
```

`juno asm` always also writes a `<Name>Shim.cpp` next to the `.S` file — copy both into the wrapper
sketch folder and compile them together. It provides `extern "C"` free functions for the things the
generated assembly can't call directly: the arena allocator/panic handler backing fixed-size arrays,
and `ArduinoLEDMatrix`/`Serial` (C++-only objects — `ArduinoLEDMatrix`'s methods are inline-only with
private timer/frame state, and `Serial` is a `HardwareSerial` instance with virtual dispatch; neither
has a stable symbol to call without knowing its private layout or vtable).

**Verification status:** `Blink`, `LedMatrixCircles`, `LedMatrixCountUp`, and `ArenaFeaturesPulse`
(arena objects with fields, a mutable static field set in `<clinit>`, and an enum `switch`) have all
been assembled, linked, uploaded to, and run on a real UNO R4 WiFi, confirmed working. `SerialCounter`
was independently confirmed by reading the board's actual USB serial output: it printed an
incrementing counter once per second, exactly as expected. `LedMatrixHeart`, `LedMatrixSnake` (16
reachable methods, branches, `int` arithmetic including hardware `sdiv`/`mls`, and a `boolean[][]`
frame buffer via the arena allocator), and `LedMatrixAsciiScroll` (116 reachable methods, 1343 IR
blocks — the full printable-ASCII font dispatch tree) have all been assembled and linked against the
real `arduino:renesas_uno` toolchain and uploaded to real hardware; on-device confirmation of their
actual on-screen behavior is pending. Any other program is unverified beyond "assembles and links";
treat this backend as experimental.

**`long`/`float`/`double` and `Wifi`/`HttpClient`/`HttpsClient`/`Json` support added.** The RA4M1 has
no hardware FPU, so every nontrivial `long`/`float`/`double` operation is a call to a small
`extern "C"` runtime-shim helper (soft arithmetic, same idea as calling into `libgcc`, just hand-written
and reused verbatim from `ArduinoCppBackend`'s own already-verified `long` helpers where possible)
rather than hand-rolled assembly; see `CortexM4AsmBackend`'s class doc for the exact storage model.
HTTP/HTTPS/JSON reuse `ArduinoCppBackend`'s own HTTP/1.1 codec and JSON scanner as `extern "C"` shim
functions. `MadridWeather` (WiFi connect, an HTTPS/TLS request, and JSON field extraction including a
`Json.getDouble` result immediately narrowed to `int`, matching this backend's `(int)
Json.getDouble(...)` support) has been assembled and linked against the real toolchain and confirmed
working on real UNO R4 WiFi hardware via its own USB serial output (`WiFi status: 3`, then repeated
`HTTPS response bytes: N` / `HTTPS JSON: OK` cycles); its LED-matrix display output itself still needs
the user's own eyes, same as `LedMatrixHeart`/`Snake`/`AsciiScroll` above. Assembling a program this
large also surfaced a new toolchain-only bug: `ldr rN, =symbol` (string literals, static fields) uses a
PC-relative literal pool that only reaches 4095 bytes forward, and every previous program was small
enough that the assembler's default end-of-file pool was always in range — fixed by flushing the pool
(`.ltorg`) after every basic block, always safe since a block's terminator already ends in an
unconditional branch or return.
