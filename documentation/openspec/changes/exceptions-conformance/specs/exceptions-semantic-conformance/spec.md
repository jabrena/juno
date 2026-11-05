# exceptions-semantic-conformance Specification

## ADDED Requirements

### Requirement: Exception semantics are catalogued

The catalog SHALL define an `exceptions` scope listing JLS §§11.1.1, 11.1.2, 11.3, 14.18, 14.20, 14.20.1, 14.20.2,
14.20.3, 14.20.3.1, and 14.20.3.2, and the compile-time sections 11.1.3, 11.2, 11.2.1, 11.2.2, and 11.2.3 classified as
`Out of scope:`. Each covered section SHALL be cited by an area that references a program and declares Thumb-2, AVR,
and Xtensa as `REQUIRED`.

#### Scenario: Catalog validation loads the exceptions areas

- **WHEN** the conformance catalog is validated
- **THEN** it finds the exceptions scope, every covered section classified by an area, the skipped compile-time sections
  with reasons, and all three backend claims

### Requirement: Throw, catch, finally, and try-with-resources match OpenJDK

The four `programs/exceptions/` fixtures SHALL produce deterministic serial output that does not depend on JDK
exception messages: integer division by zero and array index failures caught by their class and superclass; `throw`
with exact, superclass, and multi-catch matching, rethrow, and replacement; `finally` on normal, return, and throwing
paths, including unwinding across calls; and try-with-resources with reverse close order, extended `catch`/`finally`
forms, a null resource, and a close failure that does not replace the body's exception.

#### Scenario: An exception fixture runs on a required backend

- **WHEN** an exceptions fixture runs under the Thumb-2, AVR, or Xtensa conformance runner
- **THEN** its serial transcript equals the OpenJDK transcript
