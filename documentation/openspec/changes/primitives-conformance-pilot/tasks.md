## 1. Catalog and corpus

- [x] 1.1 Define `catalog.xml` and `catalog.xsd` under `juno-compiler/src/test/resources/conformance`, and implement secure JDK XML parsing plus XSD validation for primitive catalog entries with Java SE 27 JLS references.
- [x] 1.2 Add catalog entries for literals; integral, floating-point, and boolean types; conversions and promotions; integral and floating-point operators; and boolean and conditional expressions, using the required Java SE 27 JLS references.
- [x] 1.3 Implement catalog validation for missing mandatory semantic areas or JLS references; empty areas; source fixtures missing from the XML inventory; inventory items without fixtures; and inventory items without a semantic-area reference.
- [x] 1.4 Add focused, device-independent source fixtures under `src/test/resources/conformance/programs/primitives`, derived from the primitive behavior in `PrimitiveTypes`, `IntMath`, `LongMath`, and `FloatMath`; retain the legacy programs for existing coverage until later categories migrate.
- [x] 1.5 Add Nano ESP32 board selection to the focused primitive programs and retain compatible Thumb-2 and AVR selection.

## 2. Shared conformance execution

- [ ] 2.1 Extract shared OpenJDK-baseline, program-discovery, transcript-comparison, and diagnostic logic without changing the selected Thumb-2 or AVR behavior.
- [x] 2.2 Adapt `QemuRunIT` to execute the catalogued primitive corpus under Thumb-2.
- [x] 2.3 Adapt `SimavrRunIT` to execute the catalogued primitive corpus under AVR.
- [x] 2.4 Add focused unit coverage for catalog validation and runner selection.

## 3. Xtensa execution spike and runner

- [x] 3.1 Prove one focused Nano ESP32 primitive program can be compiled, packaged as an ESP32-S3 bootable image, executed under the selected emulator, and matched with OpenJDK (`IntegralArithmetic` under Espressif QEMU `esp-develop-9.2.2-20260417`).
- [x] 3.2 Add the pinned Xtensa emulator image, runner, timeout, UART capture, and completion-marker handling.
- [x] 3.3 Generalize the verified spike to every required primitive catalog entry and add failure diagnostics (`XtensaRunIT`: 7/7 catalogued programs match OpenJDK in CI run 41823460085).

## 4. CI evidence and verification

- [x] 4.1 Add an isolated Xtensa primitive CI job and retain the existing isolated Thumb-2 and AVR primitive execution paths (`xtensa-image` and `xtensa` jobs; verified in CI run 41823460085).
- [x] 4.2 After each backend job executes its tests, upload its Failsafe XML with `if: always()` as `thumb2-conformance-junit`, `avr-conformance-junit`, or `xtensa-conformance-junit` (QEMU, simavr, and Xtensa are wired; each also carries a rendered `*-conformance.html`).
- [x] 4.2a Add a downstream `Publish conformance reports` job that runs after the Thumb-2/QEMU and AVR/simavr jobs, downloads their backend artifacts, writes a GitHub step summary, and publishes a consolidated `conformance-junit-reports` artifact.
- [ ] 4.2b Verify that the Thumb-2/QEMU and AVR/simavr jobs each produce at least one JUnit XML report and that the downstream report job collects both artifacts after successful and failed executions (successful path verified in CI run 41823460085 for all three backends, XML and HTML; a failed execution is not yet verified).
- [ ] 4.3 On backend failure, upload generated assembly, shim, expected/actual output, and emulator logs as a separate failure-only artifact (expected/actual and generated sources are wired for QEMU/simavr; separate log packaging remains pending).
- [ ] 4.4 Verify each JUnit report identifies its conformance program and target board in a standalone report entry (not only in a backend log stream).
- [x] 4.5 Run the affected Maven tests and all three primitive runners; confirm each required transcript equals its OpenJDK baseline (CI run 41823460085: Build, QEMU 97/97, simavr 40/40, Xtensa 7/7).
- [ ] 4.6 Link the primitive catalog from the site documentation and document its Java SE 27 scope.
