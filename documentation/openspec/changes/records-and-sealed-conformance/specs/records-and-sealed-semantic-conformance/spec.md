# records-and-sealed-semantic-conformance Specification

## Purpose

Pins Juno's supported record and sealed-declaration behavior to Java SE 27 semantics. The catalog maps each area to
a small, deterministic serial-output fixture that must produce the same transcript under OpenJDK and every required
Juno emulator backend.

## ADDED Requirements

### Requirement: Records and sealed areas are catalogued

`juno-compiler/src/test/resources/conformance/catalog.xml` SHALL define `records` and `sealed` scopes and the
`records.components-members-and-constructors` and `sealed.classes-and-interfaces` areas. The records area SHALL cite
JLS §§8.10, 8.10.1–8.10.4.2; the sealed area SHALL cite §§8.1.1.2, 8.1.6, 9.1.1.4, and 9.1.4. Each area SHALL
reference at least one fixture and declare `REQUIRED` status for Thumb-2, AVR, and Xtensa.

#### Scenario: The catalog validates both declaration categories

- **WHEN** catalog validation loads the conformance resources
- **THEN** it finds both required areas, their JLS references, their fixtures, and all three backend declarations

### Requirement: Focused records fixture

The suite SHALL provide `programs/records/RecordClasses.java`. It SHALL use record components and their accessors,
a declared instance method, a normal canonical constructor, and a compact canonical constructor whose observable
results are printed through the serial interface. It SHALL not use generated `equals`, `hashCode`, or `toString`.

#### Scenario: Record constructor results match OpenJDK

- **WHEN** `RecordClasses` runs on a required backend
- **THEN** its component and method-result transcript equals the OpenJDK transcript

### Requirement: Focused sealed-types fixture

The suite SHALL provide `programs/sealed/SealedTypes.java`. It SHALL use an explicit `permits` clause on a sealed
interface and a sealed class, final permitted subclasses, and method calls through both an interface and a class
reference. Its result values SHALL be printed through the serial interface.

#### Scenario: A sealed hierarchy dispatches correctly

- **WHEN** `SealedTypes` runs on a required backend
- **THEN** the transcript of calls through the sealed interface and class hierarchy equals the OpenJDK transcript

### Requirement: Required backend equivalence

For every records or sealed fixture, the existing Thumb-2/QEMU, AVR/simavr, and Xtensa/Espressif-QEMU runners SHALL
compare the Juno serial transcript to the OpenJDK transcript and fail on a mismatch with their normal diagnostics.

#### Scenario: A required backend differs from OpenJDK

- **WHEN** either fixture produces a different serial transcript on a required backend
- **THEN** that backend's conformance job fails and retains its expected/actual diagnostic evidence
