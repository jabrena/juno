## Context

`QemuRunIT` currently compiles each source under `juno-compiler/src/test/qemu/programs`, runs its generated
Thumb-2 code under Cortex-M4 QEMU, and compares serial output with an OpenJDK run of the same source.
`SimavrRunIT` reuses the AVR-compatible subset. The shared corpus contains primitive-focused programs
(`PrimitiveTypes`, `IntMath`, `LongMath`, and `FloatMath`), but its location and test discovery express the ARM
harness rather than the semantic purpose.

The pilot must demonstrate the full conformance model while being small enough to diagnose: JLS references, catalog
validation, focused source programs, shared OpenJDK baseline, three architecture runners, CI reports, and retained
failure evidence. Xtensa is the missing architecture runner. An ESP32-S3 emulator boots a flash image through its
normal boot chain, unlike the existing bare-metal ARM harness; this is the principal technical risk.

## Goals / Non-Goals

**Goals:**

- Establish a catalogued primitive-semantics slice referencing the Java SE 27 JLS.
- Reuse the existing primitive test evidence by adding a focused semantic corpus; retain the legacy QEMU programs
  until their mixed semantic categories are migrated in later changes.
- Compare the exact source output on OpenJDK, Thumb-2, AVR, and an ESP32-S3/Nano ESP32 Xtensa execution path.
- Make failures repeatable and diagnosable from CI artifacts.

**Non-Goals:**

- Migrate the other existing QEMU programs or claim whole-JLS / whole-JDK conformance.
- Include arrays, string concatenation, `Math`, `java.math`, device APIs, concurrency, or physical-board execution in
  the primitive category merely because they occur in legacy source files.
- Support every ESP32 board variant or emulate board peripherals and network behavior.
- Add a Maven dependency or expand the accepted bytecode subset without separate approval.

## Decisions

### 1. Make the corpus semantic, runners architectural

Place the selected source programs under
`juno-compiler/src/test/resources/conformance/programs/primitives` and the catalog at
`juno-compiler/src/test/resources/conformance/catalog.xml`. They are test inputs compiled by the runners, not JUnit
test classes, so Maven test resources express their role accurately. Keep backend harnesses and backend-specific
integration tests under their own directories. QEMU, simavr, and Xtensa runners consume the same selected corpus
instead of owning duplicate sources.

The focused corpus is not a mechanical rename: split the primitive portions of the legacy `PrimitiveTypes`, `IntMath`,
`LongMath`, and `FloatMath` evidence into programs for integral arithmetic/conversions, `long`, floating-point
arithmetic, and floating-point conversions. The legacy files remain in the existing runner for backwards-compatible
coverage; assertions outside primitive semantics remain there until their later semantic category exists.

**Alternative considered:** moving each file unchanged. Rejected because those files mix arrays, strings, and JDK
`Math` calls with primitive behavior, which would make the pilot catalog misleading and failures hard to localize.

### 2. Use the Java SE 27 JLS authority and an XML catalog

The catalog is `juno-compiler/src/test/resources/conformance/catalog.xml`, validated by the dependency-free JDK XSD at
`juno-compiler/src/test/resources/conformance/catalog.xsd`. It contains an explicit inventory of the
source fixtures as well as semantic-area entries. Each semantic entry references inventory identifiers, the Java
release, applicable JLS clauses, and per-architecture expectations. Primitive entries reference clauses from JLS
Chapters 4, 5, and 15 as applicable. The catalog describes the supported Juno subset and does not treat JDK APIs or
JVM implementation details as JLS claims.

The pilot explicitly covers Java SE 27 literal forms (§§3.10.1–3.10.4); integral, floating-point, and boolean
types and values (§§4.2.1–4.2.5); primitive conversion and numeric-promotion rules (§§5.1.2–5.1.4, §5.2, §5.5,
and §§5.6.1–5.6.2); and primitive expression evaluation and operators (§15.7, §§15.15.1–15.15.3, §15.16,
§§15.17.1–15.17.3, §15.18.2, §15.19, §§15.20–15.25). It intentionally excludes string concatenation (§15.18.1),
arrays (Chapter 10), and JDK-library behavior such as `Math` and `java.math`.

Parse the catalog with the JDK XML API, configured not to resolve external entities or external resources. This
keeps the catalog dependency-free and avoids adding an unapproved Maven dependency. Load the catalog and source
fixtures through the test classpath. During Maven test execution, validate that every `.java` fixture in the resource
directory appears in the catalog inventory, every inventory item resolves to one fixture, and every inventory item is
referenced by at least one semantic area.

### 3. Compare complete serial output with the same OpenJDK baseline

The OpenJDK-side `Serial` test double remains test infrastructure. Each conformance program terminates after a
deterministic serial transcript; the shared `ConformanceDigitalTwin` executes the source on OpenJDK, and every backend
runner compares its transcript byte-for-byte with that result, plus the runner's explicit exit marker where that
harness uses one. A mismatch writes the expected and actual transcripts beside the generated backend outputs.

**Alternative considered:** handwritten expected-output files. Rejected for the semantic corpus because OpenJDK is
the comparison authority and avoids duplicating expected values. Device-only tests may retain handwritten output
outside this capability.

### 4. Require one runner per architecture for this slice

The existing Thumb-2/QEMU and AVR/simavr runners remain the implementation basis. Add an Xtensa runner that targets
the Nano ESP32's ESP32-S3 and uses a supported ESP32-S3 emulator. Primitive sources declare the Nano ESP32 board so
the runner can select them. The pilot requires one ESP32-S3 board/core path; M5Stack-specific behavior is deferred.

The Xtensa runner builds a bootable ESP32-S3 flash image containing Juno-generated assembly and shim code, captures
UART output, and terminates on the suite exit marker. It must not use a physical board, network service, or device
API.

The selected path is Espressif's QEMU (`esp-develop-9.2.2-20260417`, pinned by SHA-256) and the pinned
`arduino:esp32@2.0.18-arduino.5` core in one Docker image (`src/test/xtensa`). The core builds the sketch for
`arduino:esp32:nano_nora` with `build.cdc_on_boot=0`, so `Serial` is UART0, and `merge_flash.sh` places bootloader,
partition table, and application in a 16 MiB image. QEMU emulates the board's 8 MiB octal PSRAM, which the core's
boot code probes. The image replaces only the variant's `variant.cpp`: the board's double-tap recovery pulses the RGB
LED through LEDC, which QEMU does not model. The harness sketch brackets the program with `[juno-begin]` and
`[juno-exit]`, and the runner compares the text between them with Arduino's `\r\n` line ends read as `\n`.

**Alternative considered:** treating Arduino CLI compilation as Xtensa execution evidence. Rejected: compilation
cannot expose calling-convention, runtime, or generated-code semantic regressions.

### 5. Publish backend reports after execution

The Thumb-2, AVR, and Xtensa runners execute in isolated CI jobs. After test execution, each job uploads its
Failsafe JUnit XML with `if: always()` so report evidence survives both success and failure. The workflow publishes
backend-specific raw artifacts: `thumb2-conformance-junit`, `avr-conformance-junit`, and
`xtensa-conformance-junit`. A downstream `Publish conformance reports` job runs after the QEMU and simavr jobs,
downloads the available backend artifacts, writes a GitHub step summary with the discovered suites and counts, and
publishes the consolidated `conformance-junit-reports` artifact. This makes report processing visible as a distinct
pipeline task without rerunning either backend.

Pull requests use the `pull_request` workflow trigger, while direct push validation is limited to `main`; this keeps
each feature-branch revision to one CI execution instead of duplicating it through both triggers.

A report artifact can contain one or more Failsafe XML files, whose standalone entries identify the conformance
program and board: the emulator profiles configure Failsafe to name each entry by its display name, and each backend
job runs `.github/scripts/ConformanceReportCheck.java` (a JBang script, run with the job's JDK), which fails when an
entry names neither. CI verification must confirm that the QEMU and simavr jobs produce JUnit XML, that the downstream
report job collects both artifacts, and that the consolidated artifact remains downloadable after successful and
failed executions.

On failure, the affected job additionally uploads generated assembly, shim, emulator command/log, and OpenJDK
expected/actual output as a separate `thumb2-conformance-failure`, `avr-conformance-failure`, or
`xtensa-conformance-failure` artifact; every run keeps its harness command and output in an `emulator.log` beside the
generated sources, so the JUnit artifacts carry only reports. A consolidated cross-backend dashboard is deferred until multiple semantic categories make
it useful.

## Risks / Trade-offs

- **ESP32-S3 emulation requires an ESP-IDF-compatible bootable flash image and toolchain** → First implement a
  single-program spike; do not mark Xtensa required until that program matches OpenJDK deterministically.
- **The emulator harness can diverge from the Arduino core** → limit the pilot to pure primitives and serial output;
  retain real Arduino CLI compilation as its separate integration evidence.
- **Floating-point output can vary by formatting or ABI** → compare only deterministic integral/boolean renderings of
  primitive results and preserve existing edge-case coverage in focused programs.
- **A malformed or externally-referencing XML catalog can make validation unreliable** → use securely configured JDK
  XML parsing and fail validation with the catalog location and parser error.
- **Legacy source has mixed concerns** → split it by semantic category rather than weakening the category boundary.

## Migration Plan

1. Add the XML catalog and focused resource corpus while adapting QEMU and simavr to consume it; their output
   comparisons remain unchanged for the selected behavior.
2. Complete the single-program Xtensa spike, then generalize it into the Xtensa primitive runner.
3. Enable the three-backend primitive CI jobs and upload diagnostics.
4. Roll back by restoring the legacy source locations and disabling the new profile/job; no production or board
   artifact format changes are involved.

## Open Questions

- Is a versioned XSD needed after the primitives pilot, or are suite-level relationship checks sufficient for the
  initial XML catalog?
