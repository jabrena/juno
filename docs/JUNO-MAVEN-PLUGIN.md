# juno-maven-plugin

[`juno-maven-plugin`](../juno-maven-plugin) is the Maven integration for Juno. It turns compiling
a Java entry point into a sketch, verifying it with `arduino-cli`, uploading it, and opening the
serial monitor into ordinary Maven goals, so a full edit/flash/observe cycle needs no hand-written
`arduino-cli` invocations. See [docs/ARDUINO.md](ARDUINO.md) for installing `arduino-cli` itself.

## Goals

| Goal | Default phase | What it does |
|---|---|---|
| `juno:env` | `generate-sources` | Reads a git-ignored `.env` file and exports its entries into the Maven JVM's real process environment. |
| `juno:compile` | `process-classes` | Runs Juno's compiler for `<mainClass>` and writes the generated `.S` assembly, `Shim.cpp` runtime, and `.ino` wrapper. |
| `juno:verify` | `verify` | Runs `juno:compile`, then compiles the generated sketch with `arduino-cli compile` — no board required. |
| `juno:upload` | (none — run explicitly) | Runs `juno:compile` and `juno:verify`, resolves the board's serial port, and flashes it with `arduino-cli upload`. |
| `juno:monitor` | (none — run explicitly) | Resolves the board's serial port and opens an interactive `arduino-cli monitor` session. |

`compile`, `verify`, and `upload` each (re)build the sketch first, so running any one of them
always reflects the current Java source — there is no separate "just compile" step to remember.

## Configuration

Add the plugin to a module's `pom.xml` and set the entry-point class once, or override it per
invocation with `-Djuno.main=...`:

```xml
<plugin>
    <groupId>io.github.jabrena</groupId>
    <artifactId>juno-maven-plugin</artifactId>
    <version>${project.version}</version>
    <configuration>
        <mainClass>${juno.main}</mainClass>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>env</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

This is exactly how [`juno-examples/pom.xml`](../juno-examples/pom.xml) is set up, with
`juno.main` defaulting to `io.github.jabrena.juno.api.Blink` as a Maven property so any example
can be selected without editing the POM.

| Property | Goals | Default | Purpose |
|---|---|---|---|
| `juno.main` | `compile`, `verify`, `upload` | *(required)* | Fully qualified entry-point class. |
| `juno.outputDirectory` | `compile`, `verify`, `upload` | `${project.build.directory}/juno` | Where generated sketch directories are written. |
| `juno.gcLog` | `compile`, `verify`, `upload` | `false` | Emit one `Serial` line per garbage collection (see [docs/FEATURES.md](FEATURES.md)). |
| `juno.arduinoCli` | `verify`, `upload`, `monitor` | `arduino-cli` | Executable name or path. |
| `juno.fqbn` | `compile`, `verify`, `upload`, `monitor` | derived from `@Board` | Overrides the target FQBN. |
| `juno.port` | `upload`, `monitor` | auto-detected | Serial port, when more than one matching board is connected. |
| `juno.baudRate` | `monitor` | `115200` | Must match the program's `Serial.begin(...)` rate (see [docs/SERIAL.md](SERIAL.md)). |
| `juno.envFile` | `env` | `${project.basedir}/.env` | The `KEY=VALUE` file to export. |

Every property can be set in `<configuration>` or passed with `-D` on the command line; `-D`
always wins for a given run.

## Typical workflow

```bash
# Compile and check with arduino-cli, no board required
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.Blink

# Flash it to a connected board
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.Blink

# Watch its Serial output
./mvnw -f juno-examples/pom.xml juno:monitor
```

`upload` and `monitor` auto-detect the port when exactly one matching board is connected;
otherwise pass `-Djuno.port=<PORT>` (find it with `arduino-cli board list`). Always use
`juno:monitor` to watch board output rather than a raw `arduino-cli monitor` call — it resolves
the same port logic as `upload` and defaults to the baud rate every `juno-examples` program uses.

## Supplying Wi-Fi credentials with `juno:env`

`juno:env` is a Maven-level alternative to exporting shell environment variables by hand for
programs that call `System.getenv(...)` (Wi-Fi SSID/password, hosts, paths — see
[docs/INTERNET.md](INTERNET.md)). It reads `.env` (`KEY=VALUE` per line, git-ignored, never
committed) from the module's base directory and exports each entry into the real process
environment before `juno:compile` runs in the same JVM:

```text
JUNO_WIFI_SSID=your-network-name
JUNO_WIFI_PASSWORD=your-network-password
```

A missing `.env` file is not an error — nothing is exported, and modules that don't need it are
unaffected. See [docs/INTERNET.md](INTERNET.md#alternative-a-env-file-via-juno-maven-plugin) for
the full explanation, including the `--add-opens` JVM flags this goal needs (already configured in
[`.mvn/jvm.config`](../.mvn/jvm.config)).

## Troubleshooting

- **"Missing required Juno entry point":** set `<mainClass>` in the plugin configuration or pass
  `-Djuno.main=<fully-qualified-class-name>`.
- **`upload`/`monitor` can't find a port:** run `arduino-cli board list` and pass the port
  explicitly with `-Djuno.port=...` when more than one matching board is attached.
- **Uploaded sketch targets the wrong board variant:** pass `-Djuno.fqbn=...` to override the FQBN
  Juno derived from the entry point's `@Board` annotation; the plugin logs a warning whenever an
  override changes the effective target.
- **`.env` values aren't visible to `System.getenv` at compile time:** confirm the module's build
  actually runs `juno:env` (an `<execution>` with goal `env`) before `process-classes`, and that
  `.mvn/jvm.config`'s `--add-opens` flags are in effect (they are, by default, in this repository).
