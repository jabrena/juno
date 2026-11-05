# string-semantic-conformance Specification

## Purpose
TBD - created by archiving change strings-conformance. Update Purpose after archive.
## Requirements
### Requirement: String semantics are catalogued

The catalog SHALL define `strings.concatenation-and-conversion`, citing JLS §§3.10.5, 3.10.6, 5.1.11, 5.4, and
15.18.1. It SHALL reference a focused fixture and declare Thumb-2, AVR, and Xtensa as `REQUIRED`.

#### Scenario: Catalog validation loads the strings area

- **WHEN** the conformance catalog is validated
- **THEN** it finds the strings scope, required area, fixture, JLS references, and all three backend claims

### Requirement: String concat and supported conversion match OpenJDK

`StringSemantics.java` SHALL produce deterministic serial output from string literals; boolean, integral, long,
float, double, char, and null concat operands; and `String.valueOf(int)` and `String.valueOf(double)`.

#### Scenario: A string fixture runs on a required backend

- **WHEN** `StringSemantics` runs under the Thumb-2, AVR, or Xtensa conformance runner
- **THEN** its serial transcript equals the OpenJDK transcript

