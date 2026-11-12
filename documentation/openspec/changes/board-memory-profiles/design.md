## Context

The garbage-collected arena is a static `.bss` array (`runtime/common/allocator.cpp:1`), so it is already in internal
SRAM on every core. Its size comes from `RuntimeConfig.arenaBytes` (default `RuntimeLimits.ARENA_CAPACITY_BYTES` =
8192), adjusted by `RuntimeConfig.forBoard` only for AVR cores. Carrier stacks come from `ArduinoCore`, hard-coded per
core in each `*CoreRuntime.threadPort`.

Where the rest of RAM goes differs by board, and that decides which mechanism detects an over-sized arena. The panel
verified this in the runtime sources:

| Board (core) | Stacks | What has the final say on fit |
|---|---|---|
| UNO R3, UNO WiFi Rev2 (AVR) | static `juno_stacks[MAX_THREADS-1][STACK]` (`avr/thread-port.cpp:22`) | linker (`Global variables use …`) plus main stack |
| UNO R4 WiFi (Renesas) | static carrier and switcher stacks (`renesas/thread-port.cpp:23,25`) | linker; WiFiS3 and String malloc at run time |
| UNO Q (Zephyr) | static carrier (`zephyr/thread-port.cpp:26`) and two 4 KiB I/O workers (`zephyr/io-worker.cpp:10-11`) | LLEXT loader pool at sketch load: code, statics and arena share it |
| Nano ESP32, ESP32-CAM, Cardputer (ESP32) | 1 carrier: static; 2 carriers (default): per-slot FreeRTOS tasks from the heap (`esp32/thread-port-dual.cpp:114`), up to 16 slots | runtime heap left after `.bss` (Wi-Fi, TLS, BLE, camera DMA, slot stacks) |
| Pico 2 W (RP2350) | static 16 KiB main stack; per-slot tasks from the heap (`rp2350/thread-port-dual.cpp:166`) | runtime heap (CYW43, lwIP, BTstack, slot stacks) |

PSRAM is used only by the camera frame buffer (`esp32/camera.cpp:32`, `CAMERA_FB_IN_PSRAM`). No `EXT_RAM_*_ATTR`,
`ps_malloc` or `MALLOC_CAP_SPIRAM` appears in the runtime.

## Goals / Non-Goals

**Goals:**
- One source of truth per board for arena and stack defaults and limits, consumed by every compiler stage.
- Explicit overrides always distinguishable from defaults, validated on every architecture, with diagnostics a Java
  developer can act on.
- A build report that always states the memory configuration used.
- Defaults that come from measurements, not from nominal RAM.
- The arena provably stays in internal SRAM.

**Non-Goals:**
- A PSRAM-backed arena, Java heap, stacks or collector metadata.
- Run-time auto-sizing from transient free memory.
- Capability-dependent reserve findings (Wi-Fi + BLE + TLS budgets). They duplicate the linker, need re-measuring per
  core version, and break the rule that the linker stays authoritative. Revisit only with measured data.
- A new `juno-api` method or intrinsic, or a new Maven plugin parameter.
- Capping or reusing ESP32/Pico slot stacks under a heap budget (follow-up).

## Decisions

### D1. The profile belongs to `Board`, not `ArduinoCore`
SRAM is a property of the chip and module. The issue forbids assuming boards that share a core share a budget, and a
second board on an existing core would otherwise inherit the wrong profile. Each `Board` constant takes a
`BoardMemoryProfile` constructor argument. `ArduinoCore` keeps the architecture, display name and carrier count (an
execution-model property) and loses `defaultArenaBytes`, `maxArenaBytes`, `defaultThreadStackBytes` and
`maxThreadStackBytes`. The 6 `*CoreRuntime.threadPort` implementations read the stack from the resolved configuration.

### D2. Profile shape
```java
public record BoardMemoryProfile(int internalSramBytes, int defaultArenaBytes, int maxArenaBytes,
                                 int defaultStackBytes, int maxStackBytes, MemoryAuthority authority,
                                 OptionalInt psramBytes) {
    // compact constructor: sizes multiples of 8; MIN_ARENA <= default <= max < internalSram;
    // MIN_STACK <= defaultStack <= maxStack
    public int reservedBytes() { return internalSramBytes - maxArenaBytes; }   // derived, never stored

    ResolvedRuntimeConfig resolve(RuntimeConfig requested, Board board) { ... }  // owns validation and messages
}

public enum MemoryAuthority { LINKER, RUNTIME_HEAP, LOADER_POOL }
```
- `reservedBytes()` is derived from the maximum rather than stored, so the two cannot disagree (architecture review).
  The embedded review asked for separate static and heap reserves. `MemoryAuthority` keeps that distinction where it
  matters (which failure mode a too-large arena produces, and what the diagnostic and docs say) without inventing
  numbers the inventory has not measured.
- `psramBytes` is metadata only. It has one reader, the report line "PSRAM: 8 MiB, not used by the arena", which is
  what acceptance criterion 5 and the docs need. No code path may size anything from it.

### D3. Requested versus resolved configuration
```java
public record RuntimeConfig(OptionalInt arenaBytes, OptionalInt threadStackBytes, OptionalInt carrierCountOverride) {
    public static final RuntimeConfig DEFAULT = new RuntimeConfig(OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty());
    public ResolvedRuntimeConfig resolveFor(Board board) { return board.memoryProfile().resolve(this, board); }
}

public record ResolvedRuntimeConfig(Board board, int arenaBytes, Source arenaSource,
                                    int threadStackBytes, Source stackSource, int carrierCount) {
    public enum Source { BOARD_DEFAULT, OVERRIDE }
}
```
- An empty `OptionalInt` means "unset". This fixes the `arenaBytes == DEFAULT.arenaBytes` ambiguity. `Source` follows
  presence: an explicit value equal to the default is reported as an override.
- Backends, shims, `*CoreRuntime`, `RuntimeRiskAnalyzer`, `ConfigAnalyzer` and `CompilationReport` accept only
  `ResolvedRuntimeConfig`, so the type system rules out code that bypasses resolution (the `InspectCommand` and
  backend-test `DEFAULT` paths).
- `RuntimeConfig.of(Optional<String>…)` keeps its signature, so the plugin and `XtensaRunIT` compile unchanged.
  `RuntimeConfig(int, OptionalInt)` stays as a convenience constructor. `forBoard` is renamed `resolveFor`; it is
  internal to the reactor, so it gets no deprecated alias.
- Absence is modelled with `OptionalInt`, so the change needs no `@Nullable`.

### D4. Maxima are hard link/boot ceilings, not per-program budgets
A maximum is the largest arena with which a **minimal** program (Blink) links and boots on that board. That makes
every rejection a guaranteed failure avoided, and leaves anything below the ceiling to the linker or the run-time
probe. A heavy program's limit (for example the R4 Mailbox's 11008 bytes) is evidence of usage, not a maximum, and
must stay accepted. Exceeding the maximum is a hard `CompileException` with no force flag: the ceiling cannot be
exceeded on real hardware anyway.

Before measurements exist, every non-AVR maximum is set to a value no program can link above (internal SRAM minus
the minimal-Blink static footprint, from `arduino-cli` output). This fixes `-Djuno.Xmx=1g` without rejecting any
configuration that works today. Working configurations that must stay accepted: R4 11008 and 11264, UNO Q 12288,
32k and 92k, Cardputer 12k, Nano ESP32 16k and 24k. The AVR values stay: R3 1024/1024 arena and 512/512 stack, Rev2
1024/3072 arena and 512/1024 stack.

For the UNO Q (`LOADER_POOL`), code competes with the arena, so no fixed ceiling is safe. In addition to the hard
ceiling, a JUNO-RISK warning fires when the estimated static RAM approaches the ceiling, naming the loader pool.

### D5. Measurement uses a shim-only probe gated by `juno.gcLog`
No new intrinsic or API method. When `juno.gcLog` is on, the generated shim also prints, at boot and after the
first thread start and the first Wi-Fi connect:
- ESP32 cores: `heap_caps_get_free_size(MALLOC_CAP_INTERNAL)`, `heap_caps_get_minimum_free_size(MALLOC_CAP_INTERNAL)`,
  `heap_caps_get_largest_free_block(MALLOC_CAP_INTERNAL)`, and a panic if `esp_ptr_external_ram(juno_arena)`.
- RP2350: `rp2040.getFreeHeap()`.
- AVR, R4: nothing extra (the linker is authoritative).
- UNO Q: the loader pool's total, free and minimum-free bytes, read through the heap-statistics function the
  `arduino:zephyr` core exports to sketches (Q1, resolved: the statistics are readable). This turns the Q from
  boot/no-boot trial and error into a measured headroom per program. It gives the inventory a real Q maximum (the
  pool size minus a minimal program's code and statics), and gives the `LOADER_POOL` warning in D4 an actual pool
  figure to compare against.
The probe is emitted only under the flag, so builds without `juno.gcLog` stay byte-identical. Every line is kept
under the 31-character runtime-string limit.

### D6. Default changes are evidence-driven and isolated
The R4 default stays 8192. Any other board's new default is one commit per board, with:
- the inventory row that justifies it;
- golden-output updates;
- a hardware upload plus `juno:monitor` check.

The ESP32 boards are the candidates, because CI already forces 24k for thread-heavy programs. A larger `.bss` arena
competes with the dual-carrier slot stacks and Wi-Fi for the internal heap, so a new ESP32 default must be checked
with Wi-Fi up and both carriers running. The ESP32-CAM additionally needs a camera capture, and may keep a smaller
default than the Nano ESP32. When a default changes, the explicit `-Djuno.Xmx=24k` in CI is dropped so the new
default is what gets exercised. QEMU GC-pressure tests (`GcChurn`, `GeneratedAsmToolchainTest:1964,2008`) pin
`-Djuno.Xmx=8k` so a larger default does not weaken them.

### D7. Report and diagnostics wording
The existing risk lines stay unchanged. A separate formatter, shared by the CLI and the plugin, adds this block:
```
Runtime configuration (UNO R4 WiFi):
  Arena: 8192 bytes (board default; max 30720 of 32768 internal SRAM)
  Carrier stack: 2048 bytes (board default; max 4096) x 1 carrier
  PSRAM: none
```
Override: `Arena: 12288 bytes (juno.Xmx=12k; board default 8192, max …)`.

Over the maximum, the message keeps the substring that tests and users already match:
`The UNO R4 WiFi has too little RAM for a 1048576-byte arena (juno.Xmx=1m); juno.Xmx can be at most N bytes there (32768 bytes internal SRAM, R reserved for the core, stacks and libraries; default 8192). The arena never uses PSRAM.`

For `RUNTIME_HEAP` and `LOADER_POOL` boards, a sentence is appended saying that the build checks only the ceiling and
that the board's run-time heap (or loader pool) decides below it.

JUNO-RISK-011 reads "exceeds the 512-byte carrier stack of the UNO R3". JUNO-CONFIG-002 caps its suggestion at the
maximum and, above it, says the program's startup estimate cannot fit on this board.

### D8a. Every board has a maximum `juno.Xss`, enforced on every architecture
The stack limit is part of the profile, and the compiler validates it on every board, as it does the arena. Today
only the two AVR cores have one. Where the stack lives decides what the maximum guards:
- **Static stacks (AVR, R4, Q, ESP32 with one carrier):** carrier and switcher stacks are `.bss`. The maximum is the
  largest stack with which a minimal threaded program links and boots.
- **Heap stacks (ESP32 and Pico 2 W with two carriers):** each slot's FreeRTOS task takes `juno.Xss` from the internal
  heap, and up to `MAX_DUAL_CARRIER_THREADS` (16) slots can be created. The maximum is the largest stack with which a
  program starting the board's maximum concurrent threads, with Wi-Fi up, still boots and runs. For these boards the
  diagnostic adds that each running thread slot takes this much internal heap.

Before measurements, each provisional maximum is four times the board's default stack. The AVR values stay as they
are: R3 512, Rev2 1024. This rejects absurd values without rejecting any configuration in use. The known-good values
are R4 1536 and `2k`, both at or below the R4 default. Task 5 replaces the provisional maxima with measured ones. The
512-byte global minimum stays; a per-board minimum is out of scope.

### D8b. Every check covers all eight boards
Board coverage must not depend on which boards CI happens to list.
- Profile, resolution, report, characterization and PSRAM-marker tests iterate over `Board.values()`.
- The arduino-cli memory integration tests run every board regardless of `juno.arduinoCliBoardScope`. The
  `representative` scope still applies to the language-example compile matrix only.
- The CI `arduino-java` matrix gains the AI Thinker ESP32-CAM (`ai-thinker-esp32-cam`). Its `esp32:esp32` core is
  already installed in the arduino-cli image.
- A guard test fails when the board ids in the CI `arduino-java` matrix differ from `Board.values()`, so a new board
  cannot be left out. This partly realizes the deferred "board mapping guard test" idea.
- The inventory (task 5) has one hardware row per board. Emulators cover only part of the set: QEMU Thumb-2, the
  Xtensa image (ESP32-S3 only) and simavr (AVR). They do not stand in for the hardware check.

### D8c. Core versions are recorded and drift-checked, not pinned
Each inventory row records:
- the Arduino core version it was measured on;
- the date;
- the minimal program's static RAM, from the "Global variables use X bytes" line of `arduino-cli`.

Profiles are not pinned to a core version. Pinning would fail a test on every release of any of the 8 cores
(the arduino-cli image installs the latest unless a build argument pins one) and force hardware re-measurement,
usually for nothing.

Instead, the arduino-cli integration test links the minimal program for every board and compares its static RAM
with the recorded baseline. It fails when the growth exceeds the profile's margin, and the failure names the board,
the baseline and current values, and both core versions. A real core change therefore forces re-measurement and an
irrelevant one does not.

The check cannot see run-time heap changes: Wi-Fi buffers on ESP32/Pico, or the UNO Q loader pool. Those are
re-checked with the `juno.gcLog` probe (D5) on hardware whenever a core is deliberately upgraded, a step the
documentation lists in an upgrade checklist.

### D8. `juno.maxThreads` is removed from documentation, not implemented
It exists nowhere in code and Maven silently ignores it. The AVR slot count stays `RuntimeLimits.MAX_THREADS`, and
dual carriers use `MAX_DUAL_CARRIER_THREADS`. The docs describe `juno.carriers` instead.

## Risks / Trade-offs

- **Rejecting configurations that link today.** Mitigation: before measurements, maxima are SRAM-derived ceilings;
  after, they are measured with a minimal program; the working configurations listed in D4 are regression tests.
- **AVR explicit `-Djuno.Xmx=8k` now fails.** Mitigation: a clear diagnostic and a migration note in the docs and
  release notes. No repository example uses it.
- **A larger ESP32 default links but panics at run time** (no heap left for slot stacks, Wi-Fi or camera). Mitigation:
  D5 probe; the default changes only after a hardware check with Wi-Fi and 2 carriers; `RUNTIME_HEAP` wording in
  diagnostics.
- **The UNO Q ceiling gives false confidence.** Mitigation: the `LOADER_POOL` warning, and docs that say code counts
  as RAM there.
- **Profiles drift with `arduino-cli` core upgrades.** Mitigation: each row records the core version; a CI drift check
  on the minimal program's static RAM (D8c); an upgrade checklist for run-time heap re-checks; the docs table
  test-guarded against `Board.values()`.
- **ESP32-CAM is not in the CI arduino-cli matrix.** Decided: add it, and guard the matrix against `Board.values()`
  (D8b). The cost is one more matrix job.
- **A per-board Xss maximum rejects a stack someone uses today.** Mitigation: the provisional maxima are four times
  the default (D8a), and the measured ones are taken with a minimal program.

## Migration Plan

1. The refactor (tasks 1–2) is byte-identical apart from the AVR explicit-8k rejection.
2. The report block (task 3) changes the plugin and CLI output only; `MainTest` and the formatter goldens are
   updated.
3. Default changes (task 5) ship as separate commits, with the old value documented as `-Djuno.Xmx=8k` for anyone who
   needs the previous behavior.
4. Rollback is by reverting the per-board commit; profiles are plain constructor arguments.

## Open Questions

None remain. Resolved: Q3 → record the core version and drift-check the minimal static RAM in CI (D8c). Q1 → the UNO Q shim reads the loader-pool statistics (D5). Q2 → every board is in scope (D8b). Q4 → the Xss maximum is per board and enforced everywhere (D8a).

## Appendix A: Provisional maxima (measured 2026-11-12, before hardware inventory)

How these were measured:
- Program: `Blink` built with `juno:verify` at each board's current default arena. This compiles and links only;
  nothing was uploaded.
- "Static RAM" is the `arduino-cli` "Global variables use" figure, and includes the arena.
- "Free after link" is what `arduino-cli` reports as left for locals.
- Provisional maximum arena = default arena + free after link, rounded down to a multiple of 8. That is the largest
  arena the linker could still place for this minimal program: a hard ceiling (D4), not a recommendation.
- The AVR boards keep their stricter current values, because their stacks and core also need that room.
- Provisional maximum stack = four times the default (D8a), except on AVR.

| Board | Core (version) | Linker RAM region | Static RAM (Blink) | Free after link | Default arena | Provisional max arena | Default / provisional max stack | Authority |
|---|---|---|---|---|---|---|---|---|
| UNO R3 | arduino:avr 1.8.8 | 2048 | 1556 | 492 | 1024 | **1024** (kept; link ceiling 1512) | 512 / **512** (kept) | LINKER |
| UNO WiFi Rev2 | arduino:megaavr 1.8.8 | 6144 | 1508 | 4636 | 1024 | **3072** (kept; link ceiling 5656) | 512 / **1024** (kept) | LINKER |
| UNO R4 WiFi | arduino:renesas_uno 1.6.0 | 32768 | 15220 | 17548 | 8192 | **25736** | 2048 / **8192** | LINKER |
| UNO Q | arduino:zephyr 1.0.0 | 262144 | 43404 | 218740 | 8192 | **226928** | 4096 / **16384** | LOADER_POOL |
| Nano ESP32 | arduino:esp32 2.0.18-arduino.5 | 327680 | 39032 | 288648 | 8192 | **296840** | 8192 / **32768** | RUNTIME_HEAP |
| AI Thinker ESP32-CAM | esp32:esp32 3.3.12 | 327680 | 30652 | 297028 | 8192 | **305216** | 8192 / **32768** | RUNTIME_HEAP |
| M5Stack Cardputer | m5stack:esp32 3.3.10 | 327680 | 62460 | 265220 | 8192 | **273408** | 8192 / **32768** | RUNTIME_HEAP |
| Pico 2 W | rp2040:rp2040 6.3.2 | 524288 | 96300 | 427988 | 8192 | **436176** | 4096 / **16384** | RUNTIME_HEAP |

Caveats:
- **The linker RAM region is not the chip's SRAM.** On the ESP32 boards it is the static DRAM segment. Wi-Fi, TLS,
  BLE, camera DMA and the dual-carrier slot stacks take their memory from the run-time heap carved out of that same
  segment, so a program near these ceilings links and then fails at boot. Task 5 replaces these figures with measured
  run-time ceilings, which will be far lower on the `RUNTIME_HEAP` boards.
- **On the UNO Q the sketch's code also comes from the loader pool**, so the usable arena shrinks with program size.
  The loader-pool probe (D5) measures it.
- **Every known-good configuration fits under these maxima:** R4 11008 and 11264, Q 92k, Cardputer 12k, Nano ESP32
  16k and 24k.
- The installed `arduino:zephyr` is 1.0.0 while 1.1.0 is available. Task 5 re-measures on the version CI installs.
