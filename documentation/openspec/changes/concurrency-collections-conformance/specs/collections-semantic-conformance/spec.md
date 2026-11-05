# collections-semantic-conformance Specification

## ADDED Requirements

### Requirement: Collection iteration is catalogued

The catalog SHALL define a `collections` scope listing JLS §14.14.2. The section SHALL be covered by an area that
references `FixedCapacityList` and declares Thumb-2, AVR, and Xtensa with a status. The scope comment SHALL record that
the `Iterable` form of §14.14.2 and the `java.util` collection classes are not supported.

#### Scenario: Catalog validation loads the collections area

- **WHEN** the conformance catalog is validated
- **THEN** it finds the collections scope, the §14.14.2 section classified by an area, and all three backend claims

### Requirement: User-defined sequences over arrays match OpenJDK

`programs/collections/FixedCapacityList` SHALL implement a fixed-capacity sequence over a caller-allocated primitive
`int` array, with add, get, set, remove-at-index, `indexOf`, and `clear`. It SHALL iterate its contents with an enhanced
`for` over an array, SHALL catch an out-of-range access in its own class, and SHALL allocate its storage once, not in
its loop. Its serial transcript SHALL equal the OpenJDK transcript on every backend the area declares `REQUIRED`.

#### Scenario: The fixture reports sizes and values, not allocation

- **WHEN** the fixture runs under a conformance runner
- **THEN** it prints values and sizes only, and its transcript equals the OpenJDK transcript

#### Scenario: An out-of-range access is caught by the sequence's own class

- **WHEN** `get` or `set` is called with an index outside `0..size-1`
- **THEN** the sequence's class catches it and prints a fixed label, and no JDK exception message is printed

### Requirement: Unsupported collection forms are recorded, not claimed

The change SHALL NOT claim the `Iterable` form of §14.14.2 or any `java.util` collection class (`List`, `ArrayList`,
`Map`, `Set`, `Deque`, or their iterators). The design records the `Iterable` form as an open gap that needs a decision
before any compiler work.

#### Scenario: The Iterable form is not covered by a fixture

- **WHEN** the fixtures under `programs/collections/` are reviewed
- **THEN** none of them iterates an `Iterable` or a `java.util` collection class
