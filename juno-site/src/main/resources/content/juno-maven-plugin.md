---
title: "Maven Plugin"
description: "The juno-maven-plugin goals: compile, verify, upload and monitor."
layout: page
---

[`juno-maven-plugin`](https://github.com/jabrena/juno/tree/main/juno-maven-plugin) is the Maven integration for Juno. It turns compiling
a Java entry point into a sketch, verifying it with `arduino-cli`, uploading it, and opening the
serial monitor into ordinary Maven goals, so a full edit/flash/observe cycle needs no hand-written
`arduino-cli` invocations. See [Getting Started](../getting-started) for installing `arduino-cli` itself.

## Goals

| Goal | Default phase | What it does |
|---|---|---|
| `juno:env` | `generate-sources` | Reads a git-ignored `.env` file and exports its entries into the Maven JVM's real process environment. |
| `juno:install-deps` | (none — run explicitly) | Installs the supported Arduino cores (including M5Stack ESP32) and every optional library any current example needs, once per machine. |
| `juno:compile` | `process-classes` | Runs Juno's compiler for `<mainClass>` and writes the generated `.S` assembly, `Shim.cpp` runtime, and `.ino` wrapper. |
| `juno:verify` | `verify` | Runs `juno:compile`, then compiles the generated sketch with `arduino-cli compile` — no board required. |
| `juno:upload` | (none — run explicitly) | Runs `juno:compile` and `juno:verify`, resolves the board's serial port, and flashes it with `arduino-cli upload`. |
| `juno:boards` | (none — run explicitly) | Lists the connected boards and serial ports (address, protocol, board name, FQBN). Read-only. |
| `juno:monitor` | (none — run explicitly) | Resolves the board's serial port and opens an interactive `arduino-cli monitor` session; with `-Djuno.syslog=<port>`, receives the board's syslog messages over the network instead. |

`compile`, `verify`, and `upload` each (re)build the sketch first, so running any one of them
always reflects the current Java source — there is no separate "just compile" step to remember.

## Configuration

Add the plugin to a module's `pom.xml`. There is no default program: name the one to compile on each
invocation with `-Djuno.main=...`, or, in a module with a single program, fix it with `<mainClass>`:

```xml
<plugin>
    <groupId>io.github.jabrena</groupId>
    <artifactId>juno-maven-plugin</artifactId>
    <version>${project.version}</version>
    <executions>
        <execution>
            <goals>
                <goal>env</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

This is how [`juno-examples/pom.xml`](https://github.com/jabrena/juno/blob/main/juno-examples/pom.xml) is set up: it holds
many programs, so every `compile`, `verify` or `upload` names one, for example
`-Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink`. Without it the goal fails and says how to pass it.

| Property | Goals | Default | Purpose |
|---|---|---|---|
| `juno.main` | `compile`, `verify`, `upload` | *(required)* | Fully qualified entry-point class. |
| `juno.outputDirectory` | `compile`, `verify`, `upload` | `${project.build.directory}/juno` | Where generated sketch directories are written. |
| `juno.gcLog` | `compile`, `verify`, `upload` | `false` | Emit one `Serial` line per garbage collection (see [docs/FEATURES.md](../features)). |
| `juno.board` | `compile`, `verify`, `upload`, `monitor` | *(required without a single-board `@Board` declaration)* | Which board to build for (`arduino-uno-r4-wifi`, `arduino-uno-q`, `arduino-uno-r3`, `arduino-uno-r3-wifi-rev2`, `arduino-nano-esp32`, `m5stack-cardputer`). |
| `juno.arduinoCli` | `verify`, `upload`, `boards`, `monitor`, `install-deps` | `arduino-cli` | Executable name or path. |
| `juno.fqbn` | `compile`, `verify`, `upload`, `monitor` | derived from `@Board` | Overrides the target FQBN. |
| `juno.port` | `upload`, `monitor` | auto-detected | Serial port, when more than one matching board is connected. |
| `juno.baudRate` | `monitor` | `115200` | Must match the program's `Serial.begin(...)` rate (see [docs/SERIAL.md](../serial)). |
| `juno.syslog` | `monitor` | *(unset: serial)* | UDP port to receive RFC 5424 syslog on, e.g. `5514`; no board or serial port is needed (see [Remote logging with syslog](../wifi#remote-logging-with-syslog)). |

Every property can be set in `<configuration>` or passed with `-D` on the command line; `-D`
always wins for a given run.

## Typical workflow

Run these four commands in order. The examples use the `Blink` program and the UNO R4 WiFi; for another board, change
`-Djuno.board` and `-Djuno.main`.

Before the first run, install the cores and libraries once per machine with `./mvnw -f juno-examples/pom.xml juno:install-deps`
(it installs the supported Arduino cores, including M5Stack ESP32, and every optional library an example needs).

### 1. List the boards

```bash
./mvnw -f juno-examples/pom.xml juno:boards
```

Lists the connected boards and serial ports (address, protocol, board name, FQBN). It is read-only and changes nothing.
Use it to confirm the board is detected and to find the `<PORT>` for `-Djuno.port`.

The board ids you pass as `-Djuno.board` are:

| Board | `-Djuno.board` |
|---|---|
| Arduino UNO R3 | `arduino-uno-r3` |
| Arduino UNO R3 WiFi Rev2 | `arduino-uno-r3-wifi-rev2` |
| Arduino UNO R4 WiFi | `arduino-uno-r4-wifi` |
| Arduino UNO Q | `arduino-uno-q` |
| Arduino Nano ESP32 | `arduino-nano-esp32` |
| M5Stack Cardputer | `m5stack-cardputer` |

### 2. Compile and check with arduino-cli (no board needed)

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink \
  -Djuno.board=arduino-uno-r4-wifi
```

Generates the sketch and compiles it with `arduino-cli`. It does not touch hardware.

### 3. Flash the board

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink \
  -Djuno.board=arduino-uno-r4-wifi
```

Flashes the sketch. `upload` auto-detects the port when exactly one matching board is connected; otherwise pass
`-Djuno.port=<PORT>` with the port from step 2.

### 4. Watch the Serial output

```bash
./mvnw -f juno-examples/pom.xml juno:monitor \
  -Djuno.board=arduino-uno-r4-wifi
```

Opens the serial monitor at 115200 baud (`-Djuno.baudRate` to change it). Always use `juno:monitor` rather than a raw
`arduino-cli monitor` call: it resolves the same port logic as `upload` and defaults to the baud rate every
`juno-examples` program uses.

### Watch a board over the network

A program that logs with [`Syslog`](../wifi#remote-logging-with-syslog) can be followed without a USB cable, for
example a robot driving across the floor:

```bash
./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.syslog=5514
```

The goal listens on UDP port 5514 on every interface, so broadcast messages arrive too, and prints one line per message
with its arrival time, severity, host and app name until Ctrl+C. It needs no `-Djuno.board` and opens no serial port.
The computer's firewall must allow incoming UDP on that port; on macOS, allow Java when asked.

## Juno flags

Four `juno.*` properties change the runtime that the program is built with. They work like the JVM's `-Xmx`, `-Xss`
and thread options: they change what the program can hold, never what it means. Set them in `<configuration>` or pass
them with `-D`:

| Flag | Default | What it sets |
|---|---|---|
| `juno.Xmx` | 8 KB on the 32-bit boards; 1 KB on the UNO R3 and UNO WiFi Rev2 (at most 3 KB there) | The size of the garbage-collected arena, where every `new`, array, string and object lives. |
| `juno.Xss` | 2048 bytes on the UNO R4 WiFi; 4096 on the UNO Q | The stack of each extra task. |
| `juno.gcLog` | `false` | When `true`, the garbage collector prints one `Serial` line per collection with the arena bytes used before and after. |

Sizes take the JVM's syntax: bytes, or a number followed by `k`, `m` or `g`. The arena size must be at least 512 bytes
and a multiple of 8, and the task stack size must be at least 512 bytes and a multiple of 8. Each board also has
a maximum for both sizes; a larger value fails the build and names the maximum.

```bash
./mvnw compile juno:upload -Djuno.Xmx=11k -Djuno.gcLog=true
./mvnw compile juno:upload -Djuno.Xss=1536
```

Increase `juno.Xmx` when the program keeps large data sets in RAM. The compiler's startup estimate suggests a size
when the default is too small (`JUNO-RISK-002`). Use `juno.gcLog` with `juno:monitor` to see how full the arena gets.
The values that the examples need are noted in their Javadoc; for example, a few Wi-Fi examples on the UNO R3 build with
`-Djuno.Xmx=512`.

## Runtime-risk inspection

Every `compile` (standalone CLI or `juno-maven-plugin`'s `compile`/`verify`/`upload` goals)
analyzes the optimized, closed-world IR and prints the findings automatically, before running or
uploading the program. The resulting `CompilationReport.runtimeRisks()` data is also available
programmatically as immutable records with stable diagnostic codes.

The first analysis slice reports:

| Code | Meaning |
|------|---------|
| `JUNO-RISK-001` | Arena allocation occurs directly or transitively inside a control-flow loop. |
| `JUNO-RISK-002` | The conservative startup allocation estimate exceeds the fixed arena capacity. |
| `JUNO-RISK-003` | A recursive call cycle makes generated call depth and stack usage unbounded. |
| `JUNO-RISK-004` | Array accesses exist for which Juno cannot emit a bounds check. |
| `JUNO-RISK-005` | Integer/long division or remainder (including `Math.floorDiv`/`floorMod`) may receive a zero divisor and panic. |
| `JUNO-RISK-006` | A dereference uses a value proven null at compile time. |
| `JUNO-RISK-007` | A `StringBuilder` is created with a capacity of 32 or more (or a non-constant one); `toString()` panics once content reaches the slot size. |
| `JUNO-RISK-008` | `Thread.startVirtualThread` or `StructuredTaskScope.fork` sits in a control-flow loop; only three threads can be active beside the caller. |
| `JUNO-RISK-009` | More than three virtual threads or subtasks are started with no `join` in between. |
| `JUNO-RISK-010` | A thread body (found where a lambda or object is passed to `startVirtualThread`/`fork` in the same method) starts a thread or opens a task scope, competing for the same four slots. |
| `JUNO-RISK-011` | A thread body reaches a recursive cycle, or its estimated stack exceeds the smallest thread stack (2 KiB on the UNO R4 WiFi). |
| `JUNO-RISK-012` | (info) A `static final` constant-initialized array stays in the arena instead of flash, because a read passes it on, stores it, writes it or selects it across a branch. |

The 8 KiB arena capacity and the count of bounds-checked array accesses (checked at run time, or proved safe
at compile time) are exact compiler facts. Arena,
static-RAM, and stack figures are conservative source-level estimates: alignment is overestimated,
branch feasibility is not modeled, and the downstream C++ compiler may optimize stack locals. The
Arduino linker's final memory report is authoritative. Risk findings warn; they do not currently
reject compilation.

Two board examples make the distinction observable:

- `RuntimeRiskSafePulse` allocates once, has generated bounds checks, and should report no findings;
  it blinks alternating 120/360 ms pulses indefinitely.
- `RuntimeRiskArenaExhaustion` is intentionally unsafe. It allocates 1 KiB on every loop iteration; its
  `JUNO-RISK-001` warning predicts a pre-collection worst case before upload. With the conservative
  garbage collector, whether it actually exhausts the arena now depends on reachability: if each
  iteration's block is dropped before the next (nothing on the stack still points to it), the collector
  reclaims it and the program keeps running instead of stopping; if every block is kept reachable
  (e.g. stored into a live array), reclamation is impossible and it still stops — now on whichever
  iteration first can't fit alongside everything still reachable, rather than always the ninth.

## Troubleshooting

- **"Missing required Juno entry point":** set `<mainClass>` in the plugin configuration or pass
  `-Djuno.main=<fully-qualified-class-name>`.
- **`upload`/`monitor` can't find a port:** run `./mvnw juno:boards` and pass the port
  explicitly with `-Djuno.port=...` when more than one matching board is attached.
- **Uploaded sketch targets the wrong board variant:** pass `-Djuno.fqbn=...` to override the FQBN
  Juno derived from the entry point's `@Board` annotation; the plugin logs a warning whenever an
  override changes the effective target.
- **"@Board declares multiple boards ...; pass the target board explicitly":** the entry point's
  `@Board` lists more than one board (e.g. `@Board({ArduinoUnoQBoard.class, ArduinoUnoR4WiFiBoard.class})`);
  add `-Djuno.board=<id>` naming one of them, as printed in the error.
