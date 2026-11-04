## Why

Juno already compares a shared set of Java programs with OpenJDK under Thumb-2 QEMU and AVR simavr, but the
coverage is implicit in a QEMU-named directory and has no machine-checkable relationship to the Java semantics it
protects.  A primitives-only pilot establishes that relationship, adds the missing Xtensa execution path, and proves
the conformance workflow before it is applied to concurrency, objects, or JDK-library subsets.

## What Changes

- Add a Java SE 27 JLS-referenced XML catalog for the primitive-semantics pilot.
- Relocate and split the reusable primitive evidence from the current QEMU corpus into focused, device-independent
  Java source fixtures under test resources.
- Make the same selected programs compare their OpenJDK serial output with generated code on Thumb-2, AVR, and one
  ESP32-S3/Nano ESP32 Xtensa target.
- Add the Xtensa emulator runner, deterministic completion protocol, and failure diagnostics needed for that
  comparison.
- Run the primitive conformance slice in three isolated CI backend jobs and retain their machine-readable reports and
  failure evidence after execution.

## Capabilities

### New Capabilities

- `primitive-semantic-conformance`: A catalogued, deterministic OpenJDK-versus-Juno conformance corpus for Java
  primitive semantics across supported emulator backends.
- `xtensa-primitive-conformance-execution`: Execution of generated Nano ESP32 primitive-conformance programs under
  an ESP32-S3 emulator.

### Modified Capabilities

None.

## Impact

- `juno-compiler` test source layout, QEMU and simavr integration tests, Maven test profiles, and CI workflow.
- A new emulator image and runner for the Nano ESP32/Xtensa path.
- No board-side public API, supported bytecode subset, Maven dependency, or physical-board deployment is changed by
  this proposal.

## Source and Derivation

- **Requirements source:** GitHub issue [#213](https://github.com/jabrena/juno/issues/213), including its Java SE 27
  JLS baseline, supplemented by the maintainer-approved primitives-pilot scope in this conversation.
- **Format decision:** the issue's illustrative YAML catalog is superseded for this change by the maintainer's
  2026-11-04 decision to use XML.
- **Fixture-location decision:** the maintainer's 2026-11-04 decision places the XML catalog and Java source fixtures
  in `juno-compiler/src/test/resources/conformance`.
- **Derived artifact:** this OpenSpec change, created 2026-11-04.
- **Direction:** issue and maintainer-approved scope → OpenSpec. This change does not automatically synchronize back
  to the issue.
