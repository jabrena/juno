## ADDED Requirements

### Requirement: Every board owns a memory profile
Each `Board` SHALL declare a `BoardMemoryProfile` giving its internal SRAM, default and maximum arena, default and
maximum carrier stack, the memory authority that decides fit below the maximum (linker, runtime heap, or loader
pool), and optional PSRAM size. The profile SHALL be validated at construction: sizes are multiples of 8, the arena
default lies between the 512-byte minimum and the maximum, the arena maximum is below internal SRAM, and the stack
default lies between the 512-byte minimum and the stack maximum. Memory limits SHALL NOT be read from `ArduinoCore`.

#### Scenario: Every board has a consistent profile
- **WHEN** the profile of each `Board` value is inspected
- **THEN** every invariant holds and every value is a multiple of 8

#### Scenario: Boards sharing a core do not share a profile by assumption
- **WHEN** two boards are built with the same `ArduinoCore`
- **THEN** each board's profile is declared on its own `Board` constant, and any shared values are listed explicitly
  in the profile test with their rationale

### Requirement: Unset sizes resolve to the board defaults
The compiler SHALL resolve an unset `juno.Xmx` to the selected board's default arena and an unset `juno.Xss` to its
default carrier stack, and SHALL record the source of each effective value as board default. The UNO R4 WiFi default
arena SHALL remain 8192 bytes, the UNO R3 and UNO WiFi Rev2 defaults SHALL remain 1024 bytes, and until a measured
default change is approved every other board SHALL keep 8192 bytes.

#### Scenario: Default arena per board
- **WHEN** a program is compiled for `<board>` without `juno.Xmx`
- **THEN** the generated runtime declares `juno_arena[<default>]` and the report says "board default"
- **AND** `<board>`/`<default>` include `arduino-uno-r4-wifi`/8192, `arduino-uno-r3`/1024 and `arduino-uno-r3-wifi-rev2`/1024

#### Scenario: Default carrier stack per board
- **WHEN** a program with a virtual thread is compiled for `<board>` without `juno.Xss`
- **THEN** the generated runtime uses that board's default stack (512 on AVR, 2048 on the UNO R4 WiFi, 4096 on the UNO Q and Pico 2 W, 8192 on the ESP32 boards)

### Requirement: Explicit sizes are honored and distinguishable from defaults
An explicit `juno.Xmx` or `juno.Xss` within the board's limits SHALL be used as given and reported as an override,
including when it equals the board default or the former global 8192-byte default.

#### Scenario: Explicit value equal to the old default on a board that accepts it
- **WHEN** a program is compiled for `arduino-uno-r4-wifi` with `-Djuno.Xmx=8k`
- **THEN** the arena is 8192 bytes and the report says the size came from `juno.Xmx`

#### Scenario: Explicit value equal to the old default on a board that does not accept it
- **WHEN** a program is compiled for `arduino-uno-r3-wifi-rev2` with `-Djuno.Xmx=8k`
- **THEN** compilation fails naming the UNO WiFi Rev2, the requested 8192 bytes and the 3072-byte maximum
- **AND** the arena is not silently replaced by the board default

#### Scenario: Small explicit arena on a constrained board
- **WHEN** a program is compiled for `arduino-uno-r3` with `-Djuno.Xmx=512`
- **THEN** the arena is 512 bytes

### Requirement: Overrides are validated against the board on every architecture
The compiler SHALL reject a `juno.Xmx` above the board's maximum arena or a `juno.Xss` above its maximum stack, on
every architecture, with a `CompileException` whose message names the board, the requested size, the maximum, the
internal SRAM, the reserved bytes and the default. The message SHALL contain the phrase "juno.Xmx can be at most"
(or "juno.Xss can be at most"), SHALL state that the arena never uses PSRAM, and SHALL say that below the maximum the
board's runtime heap (or loader pool) decides fit when that is the board's memory authority. A board's maximum SHALL
accept every configuration known to work on hardware.

#### Scenario: Oversized arena on a non-AVR board
- **WHEN** a program is compiled for `arduino-uno-r4-wifi` with `-Djuno.Xmx=1m`
- **THEN** compilation fails before code generation with a message containing "UNO R4 WiFi", "1048576",
  "juno.Xmx can be at most", the internal SRAM and the default 8192

#### Scenario: Known-good configurations stay accepted
- **WHEN** programs are compiled with `arduino-uno-r4-wifi` at 11264 bytes, `arduino-uno-q` at 92k,
  `m5stack-cardputer` at 12k and `arduino-nano-esp32` at 24k
- **THEN** none of them is rejected by profile validation

#### Scenario: Oversized stack on every board
- **WHEN** a program is compiled for each `Board` value with `juno.Xss` 8 bytes above that board's maximum stack
- **THEN** compilation fails naming the board, the requested stack and the maximum, on AVR and non-AVR boards alike

#### Scenario: Stack at the maximum is accepted
- **WHEN** a program is compiled for each `Board` value with `juno.Xss` equal to that board's maximum stack
- **THEN** it is not rejected by profile validation

#### Scenario: Heap-allocated carrier stacks
- **WHEN** an oversized `juno.Xss` is rejected for a dual-carrier ESP32 or Pico 2 W board
- **THEN** the message also states that each running thread slot takes that stack from the internal heap

#### Scenario: Stacks in use stay accepted
- **WHEN** a program is compiled for `arduino-uno-r4-wifi` with `-Djuno.Xss=1536` or `-Djuno.Xss=2k`
- **THEN** it is not rejected by profile validation

### Requirement: Every supported board is in scope
Profile, resolution, report, characterization and internal-SRAM checks SHALL cover every `Board` value. The
arduino-cli memory integration tests SHALL run every board regardless of the configured board scope, and the CI
Java compile matrix SHALL list exactly the ids of `Board.values()`.

#### Scenario: CI matrix guard
- **WHEN** a board is added to `Board` without a CI `arduino-java` matrix entry, or a matrix entry names no board
- **THEN** the guard test fails naming the missing or extra board id

#### Scenario: ESP32-CAM covered
- **WHEN** the CI `arduino-java` matrix and the arduino-cli memory integration tests run
- **THEN** `ai-thinker-esp32-cam` is compiled and linked alongside the other seven boards

### Requirement: Every compiler stage uses the resolved configuration
Every compiler stage SHALL consume only the configuration resolved for the selected board, never the unresolved
request or a global default: backends, runtime shims, core thread ports, the runtime risk analyzer, the configuration
analyzer, the compilation report and the `inspect` command.

#### Scenario: Inspect on an AVR board
- **WHEN** `inspect` analyses a program targeting `arduino-uno-r3` without `juno.Xmx`
- **THEN** the report's arena capacity is 1024 bytes, not 8192

#### Scenario: Thread stack risk uses the board's stack
- **WHEN** a thread body's estimated stack exceeds the stack of the selected board
- **THEN** JUNO-RISK-011 names that board and its stack size, and is not raised merely because the body exceeds 2048 bytes on a board with a larger stack

#### Scenario: Arena suggestion respects the board maximum
- **WHEN** the startup arena estimate exceeds the board's maximum arena
- **THEN** JUNO-CONFIG-002 does not suggest a size above the maximum and states that the startup estimate cannot fit on this board

### Requirement: Builds report the memory configuration
Every compilation report printed by the Maven plugin and the CLI SHALL include a "Runtime configuration" block naming
the board, the effective arena with its source, the maximum arena, the internal SRAM, the effective carrier stack
with its source and maximum, the carrier count, and the PSRAM size with the statement that the arena does not use it
(or "none"). The block SHALL be printed even when there are no findings, and the existing risk lines SHALL keep their
format.

#### Scenario: Report on a dual-carrier board
- **WHEN** a threaded program is compiled for `arduino-nano-esp32`
- **THEN** the report contains "Runtime configuration (Nano ESP32):", an "Arena:" line with "board default", a
  "Carrier stack:" line with "x 2 carriers", and a PSRAM line saying it is not used by the arena

#### Scenario: Report with an override
- **WHEN** a program is compiled with `-Djuno.Xmx=12k`
- **THEN** the Arena line shows 12288 bytes, "juno.Xmx=12k", the board default and the maximum

### Requirement: The arena stays in internal SRAM
The Java arena, Java object graphs, thread stacks and collector metadata SHALL be placed in internal SRAM. Generated
sources SHALL NOT carry PSRAM placement for them (`EXT_RAM_BSS_ATTR`, `EXT_RAM_ATTR`, `ps_malloc`,
`MALLOC_CAP_SPIRAM`), and no default or maximum SHALL be derived from PSRAM size.

#### Scenario: No PSRAM placement in generated sources
- **WHEN** any example is compiled for any board
- **THEN** the generated assembly and shim contain none of the PSRAM placement markers outside the camera frame-buffer helper

#### Scenario: Linked arena address on Xtensa boards
- **WHEN** the arduino-cli integration test links a program for `arduino-nano-esp32`, `ai-thinker-esp32-cam` and `m5stack-cardputer`
- **THEN** the `juno_arena` symbol address lies in the chip's internal DRAM range

### Requirement: Measured memory inventory backs every profile
Each of the eight boards' profiles SHALL be backed by an inventory row recording internal SRAM, the static RAM of a minimal and
a heavy example as reported by `arduino-cli`, the run-time free internal heap where the authority is the runtime
heap, stacks times carriers, the link-and-boot ceilings of the arena and of the carrier stack, the safety margin, the
core version and the date.
A default other than the current one SHALL change only with such a row and a hardware check through `juno:upload`
and `juno:monitor`.

#### Scenario: Run-time heap probe under the GC log
- **WHEN** a program is built with `-Djuno.gcLog=true` for an ESP32 or RP2350 board
- **THEN** the runtime prints free and minimum free internal heap at boot, after the first thread start and after the first Wi-Fi connection

#### Scenario: Core upgrade grows the static reserve
- **WHEN** the arduino-cli integration test links the minimal program for a board and its static RAM exceeds the recorded baseline by more than the profile's margin
- **THEN** the test fails naming the board, the baseline and current static RAM, and the recorded and installed core versions

#### Scenario: Core upgrade within the margin
- **WHEN** the installed core version differs from the recorded one but the minimal program's static RAM stays within the margin
- **THEN** the test passes

#### Scenario: Probe absent by default
- **WHEN** the same program is built without `juno.gcLog`
- **THEN** the generated shim is byte-identical to a build made before the probe existed

### Requirement: Documentation separates arena, stacks, reserve and PSRAM
The memory-model documentation SHALL explain that arena capacity is not total RAM and SHALL distinguish internal SRAM,
the Java arena, main and carrier stacks, runtime and library reserves, and PSRAM. It SHALL list every supported board's
profile, document `juno.Xmx`, `juno.Xss`, `juno.carriers` and `juno.gcLog` with their validation rules, and SHALL NOT
document the nonexistent `juno.maxThreads`. A test SHALL fail when the documented profile table disagrees with
`Board.values()`.

#### Scenario: Documentation table drift
- **WHEN** a board's profile in code changes but the documented table does not
- **THEN** the site test fails naming the board

#### Scenario: Removed flag
- **WHEN** the site content, skills and plugin Javadoc are searched for `juno.maxThreads`
- **THEN** there are no matches
