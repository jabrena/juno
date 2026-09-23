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

Some examples also need an optional Arduino library not bundled with that core (`Mouse` for
`RatonLoco`, `ESP_SSLClient` for `Smtp`'s `STARTTLS` upgrade, `Servo` for `ServoSweep` — see their
sections below). Install the core and every one of those libraries in one go instead of hunting
them down per example:

```bash
./mvnw -f juno-examples/pom.xml juno:install-deps
```

Connect the board and find its serial port:

```bash
arduino-cli board list
```

The port usually looks like `/dev/cu.usbmodem...` on macOS. The plugin auto-detects it when exactly
one matching board is connected; otherwise pass it with `-Djuno.port=...`.

## Build Juno, the Maven plugin, and examples

From the repository root:

```bash
./mvnw clean install
```

This builds the compiler and Maven plugin, installs the reactor artifacts in the local Maven
repository, and compiles every program under `juno-examples/src/main/java`. Installing once lets
the example module resolve the development version of `juno-maven-plugin` by its `juno` prefix.

## Compile and upload a sketch

`juno-examples/pom.xml` configures `Blink` as its default entry point. Generate its complete
sketch:

```bash
./mvnw -f juno-examples/pom.xml compile juno:compile
```

The plugin generates a complete Arduino sketch directory:

```text
juno-examples/target/juno/BlinkAsm/
├── BlinkAsm.ino
├── Blink.S
└── BlinkShim.cpp
```

Verify it with the real Arduino toolchain without touching a connected board:

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify
```

`juno:verify` derives the FQBN from the entry point's `@Board` annotation. Upload performs the same
generation and verification first, then uses `arduino-cli board list --json` to select the only
connected matching board:

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload
```

If more than one matching board is connected, or a specific port is required, select it explicitly:

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload -Djuno.port=/dev/cu.YOUR_PORT
```

Uploading overwrites the board's current firmware. The built-in LED (pin 13) should then blink once
a second.

To compile another example, override the configured entry point. For example, `SerialCounter`:

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.io.usb.SerialCounter
```

Open an interactive serial monitor (115200 baud by default, matching every juno-examples program's
`Serial.begin(...)` rate) with:

```bash
./mvnw -f juno-examples/pom.xml juno:monitor
```

Use `-Djuno.baudRate=...` to select another baud rate and `-Djuno.port=...` to select a port.
Press `Ctrl+C` to exit.

### Example: SerialCounter (reading Serial output)

[`juno-examples/src/main/java/io/github/jabrena/juno/api/io/usb/SerialCounter.java`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/usb/SerialCounter.java) counts
up once a second over USB serial, so it doubles as a check that the toolchain and the board's
serial port both work end to end:

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.io.usb.SerialCounter
```

Then open the serial monitor at the same baud rate the sketch uses
(`Serial.begin(BaudRate.BAUD_115200)`, `juno:monitor`'s default):

```bash
./mvnw -f juno-examples/pom.xml juno:monitor
```

The intended output is `0`, `1`, `2`, ... once a second. Press `Ctrl+C` to exit the monitor.

Generated sketches keep the UNO R4's USB service polled even though the Java entry point never
returns (Juno invokes it from Arduino `setup()`): the backend emits a `yield()` call at every loop
backedge, and the generated `yield()` override polls `Serial`'s boolean conversion, the core's
supported hook into TinyUSB's `tud_task()`.

### LED matrix examples

Any of the `LedMatrix*` example classes (see the main README's Java API section for the full
list) follow the same pattern as `Blink` above — override `juno.main`, for example
`-Djuno.main=io.github.jabrena.juno.api.led.LedMatrixHeart`. They all draw on the UNO R4 WiFi's
built-in 12x8 LED matrix.

### LCD Keypad Shield examples

[`LcdKeypadShield`](../juno/src/main/java/io/github/jabrena/juno/api/lcd/LcdKeypadShield.java)
drives a common 16x2 HD44780-compatible "LCD Keypad Shield" (LCD on digital pins 4-9, backlight on
pin 10, 5 buttons on `A0`) — no new compiler intrinsic, built entirely from `Gpio`/`Delay` the same
way `LedCanvas` is built from `LedMatrix`.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.lcd.LcdKeypadDemo
```

[`LcdKeypadDemo`](../juno-examples/src/main/java/io/github/jabrena/juno/api/lcd/LcdKeypadDemo.java)
prints a header and shows which button is currently held. Assembled, linked, uploaded to, and
confirmed working on a real UNO R4 WiFi with this exact shield.

[`Pomodoro`](../juno-examples/src/main/java/io/github/jabrena/juno/api/lcd/pomodoro/Pomodoro.java)
is a standalone (no network) Pomodoro timer: pick a session length (5-60 minutes) with UP/DOWN,
confirm with SELECT, and a live `MM:SS` countdown runs on the LCD, logged to Serial every tick; the
alarm blinks until any button is pressed, then returns to minute selection.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.lcd.pomodoro.Pomodoro
```

### Example: RatonLoco (USB mouse control)

[`juno-examples/src/main/java/io/github/jabrena/juno/api/io/hid/RatonLoco.java`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/hid/RatonLoco.java) is a
port of [raton-loco.ino](https://github.com/jabrena/raton-loco/blob/main/arduino/raton-loco.ino):
it blinks the built-in LED, then drags the host computer's mouse cursor in a square (right, down,
left, up) over USB HID.

**Warning:** once uploaded, this sketch takes control of the real mouse cursor on whatever
computer the board's USB cable is plugged into — unplug the board or re-flash it with a different
sketch to stop it.

It needs the `Mouse` library (not bundled with the `arduino:renesas_uno` core, installed by
`juno:install-deps` above, or on its own with `arduino-cli lib install Mouse`) and a board with
native USB (UNO R4 WiFi):

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.io.hid.RatonLoco
```

**Re-uploading over a running HID sketch:** `arduino-cli upload` normally resets the board into
its bootloader by opening the serial port at 1200 baud and closing it again. That trick relies on
the sketch promptly servicing the USB connection, but `RatonLoco`'s `loop()` is busy driving the
mouse (`Mouse.move` + `delay`), so the automatic reset can be missed. The board may also
re-enumerate under a different `/dev/cu.*` path once it drops into the bootloader. If the upload
hangs or fails, double-tap the board's physical reset button to force it into the bootloader
manually (the onboard LED pulses), then immediately re-run the `juno:upload` command — and
run `arduino-cli board list` first if you're unsure which port it came back on. Supply the new port
to the plugin with `-Djuno.port=...`.

### Example: ServoSweep (hobby servo control)

[`juno-examples/src/main/java/io/github/jabrena/juno/api/motors/ServoSweep.java`](../juno-examples/src/main/java/io/github/jabrena/juno/api/motors/ServoSweep.java)
sweeps a 3-wire hobby servo back and forth between 0 and 180 degrees using
[`Servo`](../juno/src/main/java/io/github/jabrena/juno/api/motors/Servo.java) — see its wiring
diagram in the class Javadoc for how to connect the servo's signal/power/ground wires.

It needs the `Servo` library (not bundled with the `arduino:renesas_uno` core, installed by
`juno:install-deps` above, or on its own with `arduino-cli lib install Servo`):

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.motors.ServoSweep
```

### Example: InboxCount (basic email support)

[`Smtp`](../juno/src/main/java/io/github/jabrena/juno/api/io/net/email/Smtp.java) sends a
plain-text message to one recipient over `STARTTLS` (mail submission on port 587), and
[`Pop3Client`](../juno/src/main/java/io/github/jabrena/juno/api/io/net/email/Pop3Client.java)
reports the mailbox message count and reads the newest message's `From`/`Subject`/body over
POP3S (port 995) — see that package's Javadoc for the full scope and its deliberate limits (one
recipient, plain text only, no attachments/HTML/MIME/folders/OAuth2).

`Pop3Client` reuses `HttpsClient`'s native `WiFiSSLClient` and needs no extra library. `Smtp`
needs the third-party `ESP_SSLClient` library (the UNO R4 WiFi's native TLS client can only
negotiate TLS from the first byte, not upgrade an existing plaintext connection the way
`STARTTLS` requires) — installed by `juno:install-deps` above, or on its own with
`arduino-cli lib install ESP_SSLClient`.

[`InboxCount`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/email/InboxCount.java)
connects to WiFi and the mailbox configured by `SMTP_HOST`/`SMPT_USERNAME`/`SMTP_PASSWORD` in
`.env`, polls `Pop3Client.messageCount` every 30 seconds, and shows the current count on the LCD
Keypad Shield's first row (`Pop3Client` only, so it does not need `ESP_SSLClient`):

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.io.net.email.InboxCount
```

The same package also has
[`EmailHelloWorld`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/email/EmailHelloWorld.java)
(proves `Smtp.send` actually delivers by checking the inbox count before/after sending) and
[`EmailClient`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/email/EmailClient.java)
(a menu-driven client: Inbox count/subject-list, Send Email, About). See
[docs/EMAIL.md](EMAIL.md) for the full `Smtp`/`Pop3Client` reference, and
[docs/APIS.md](APIS.md) for how these compiler-intrinsic API classes work internally.

## The Cortex-M4 assembly backend

This is Juno's sole code-generation backend. It emits GNU ARM (Cortex-M4) assembly straight from
Juno IR: every
reachable method becomes its own function (real calls between them, including AAPCS stack-passed
arguments beyond the first four), with branches, `switch`, `int` arithmetic/comparisons, fixed-size
arrays, arena-allocated objects with fields, mutable static fields, GPIO/delay, `LedMatrix`, and
`Serial`. Unsupported instructions fail at generation time with a clear error rather than emitting
unchecked code. Every value and local variable lives in a fixed stack-frame slot rather than a
register, so this doesn't run out of registers regardless of how large or branchy a method is.

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.Blink
```

The plugin creates the `.S`, `<Name>Shim.cpp`, and matching `.ino` wrapper together in the isolated
`<Name>Asm` sketch directory. The shim provides `extern "C"` free functions for the things the
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
`extern "C"` runtime-shim helper (soft arithmetic, same idea as calling into `libgcc`, just
hand-written) rather than hand-rolled assembly; see `CortexM4AsmBackend`'s class doc for the exact
storage model. HTTP/HTTPS/JSON are backed by an `extern "C"` HTTP/1.1 codec and allocation-free
JSON scanner in the generated runtime shim. `MadridWeather` (WiFi connect, an HTTPS/TLS request, and JSON field extraction including a
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

**`Gpio.analogRead`, `Delay.micros`, and `Clock.millis`/`Clock.micros` support added.** Each resolves
straight to the Arduino core's own `analogRead`/`delayMicroseconds`/`millis`/`micros`, the same way
`digitalWrite`/`delay` already did — no runtime shim needed. `LcdKeypadDemo` (`analogRead` via
button reads) and `Pomodoro` (`Clock.millis` for the countdown) have both been assembled, linked,
uploaded to, and confirmed working on a real UNO R4 WiFi with an LCD Keypad Shield attached.
Reaching reliable characters on that shield took a real hardware lesson, not a code fix: the shield's
backlight pin must never be driven `HIGH` (float it via `INPUT` instead — driving it actively can
brown out the shared 5V rail enough to keep the LCD from initializing at all), and 4-bit HD44780
writes need generously wide settle margins (this driver uses 20us enable pulses and 200us
settle delays, well past the datasheet minimums) to stay reliable once the UNO R4 WiFi's ESP32-S3
module is actively drawing current — the exact right bytes were provably being sent (confirmed by
logging every character code over Serial) while the physical display still showed corrupted
characters at the datasheet-minimum timings, a byte-perfect-input/wrong-output signature of a
signal-integrity margin problem rather than a software bug.
