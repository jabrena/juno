## ADDED Requirements

### Requirement: Primitive semantic XML catalog

The conformance suite SHALL maintain `juno-compiler/src/test/resources/conformance/catalog.xml` and its
`juno-compiler/src/test/resources/conformance/catalog.xsd` schema for the
primitive-semantics pilot. The XML catalog SHALL contain an inventory of all Java source fixtures under
`juno-compiler/src/test/resources/conformance/programs` and semantic-area entries that reference that inventory.
Each semantic-area entry SHALL identify the Java SE 27 release, one or more applicable JLS clauses, one or more
focused conformance programs, and an expectation for Thumb-2, AVR, and Xtensa execution. The catalog parser SHALL
validate the document against the XSD and use the JDK XML API without resolving external entities or external
resources.

#### Scenario: Catalog entry references integral overflow semantics

- **WHEN** the catalog describes integer overflow or integral division semantics
- **THEN** it identifies the relevant Java SE 27 JLS clause or clauses and at least one focused program that
  exercises the behavior

#### Scenario: Catalog validation finds unmapped source

- **WHEN** a Java fixture exists under `src/test/resources/conformance/programs` without a catalog inventory entry
- **THEN** catalog validation fails and identifies the unmapped program

#### Scenario: Catalog inventory item has no semantic area

- **WHEN** a catalog inventory item is not referenced by any semantic-area entry
- **THEN** catalog validation fails and identifies the unclassified program

#### Scenario: Catalog inventory item has no source fixture

- **WHEN** a catalog inventory item does not resolve to a Java fixture under `src/test/resources/conformance/programs`
- **THEN** catalog validation fails and identifies the missing fixture

#### Scenario: Catalog XML is malformed or references an external resource

- **WHEN** `catalog.xml` is not well-formed or attempts to resolve an external entity or resource
- **THEN** catalog loading fails without accessing the external resource and identifies the catalog error

#### Scenario: Catalog validation finds an empty area

- **WHEN** a primitive semantic area has no focused conformance program
- **THEN** catalog validation fails and identifies the empty area

### Requirement: Explicit Java SE 27 primitive coverage

The XML catalog SHALL define the following primitive semantic areas, each with at least one focused source fixture
and the specified Java SE 27 JLS references:

| Semantic area | Required JLS references |
|---|---|
| Primitive literals | §§3.10.1–3.10.4 |
| Integral types and operations | §§4.2.1–4.2.2 |
| Floating-point types and operations | §§4.2.3–4.2.4 |
| Boolean type and values | §4.2.5 |
| Primitive conversions and numeric promotion | §§5.1.2–5.1.4, §5.2, §5.5, §§5.6.1–5.6.2 |
| Integral expressions and operators | §§15.15.1–15.15.3, §15.16, §§15.17.1–15.17.3, §15.18.2, §15.19, §§15.20–15.22.1 |
| Floating-point expressions and operators | §§15.15.1–15.15.2, §15.16, §§15.17.1–15.17.3, §15.18.2, §§15.20–15.21 |
| Boolean and conditional expressions | §15.7, §§15.21–15.25 |

The pilot SHALL NOT represent string concatenation (§15.18.1), arrays (JLS Chapter 10), or JDK-library behavior as
primitive-semantics coverage.

#### Scenario: Each mandatory primitive area is catalogued

- **WHEN** catalog validation loads the primitive pilot
- **THEN** every mandatory semantic area is present, has the required JLS references, and identifies at least one
  focused source fixture

#### Scenario: Division by zero is covered as an integral-expression case

- **WHEN** the integral-expression area exercises integer or `long` division or remainder by zero
- **THEN** it identifies JLS §15.17.2 and compares the Juno result with the OpenJDK result

#### Scenario: A non-primitive assertion is encountered in a legacy source

- **WHEN** a legacy assertion concerns strings, arrays, `Math`, or `java.math`
- **THEN** the primitive catalog does not use that assertion to satisfy a mandatory primitive semantic area

### Requirement: Focused primitive conformance corpus

The suite SHALL provide deterministic, device-independent Java source fixtures under
`juno-compiler/src/test/resources/conformance/programs/primitives` for integral arithmetic and conversions, `long`
arithmetic, floating-point arithmetic, and floating-point conversions. These fixtures SHALL be derived from the
existing `PrimitiveTypes`, `IntMath`, `LongMath`, and `FloatMath` QEMU evidence, while excluding non-primitive
concerns into their own future categories.

#### Scenario: A focused program has no device dependency

- **WHEN** a primitive conformance program is compiled for a supported architecture
- **THEN** it requires only the serial-output test interface and no device, network, or physical-board API

#### Scenario: Legacy mixed behavior is separated

- **WHEN** legacy primitive evidence contains an array, string-concatenation, or JDK `Math` assertion
- **THEN** that assertion is not represented as primitive-semantics coverage in this pilot

### Requirement: OpenJDK equivalence on required architectures

For every focused primitive program required on an architecture, the suite SHALL compile the same source for
OpenJDK and Juno and SHALL require its generated-code serial transcript to equal the OpenJDK transcript.

#### Scenario: Thumb-2 transcript matches OpenJDK

- **WHEN** a required primitive program runs under the Thumb-2 runner
- **THEN** the runner passes only when its serial transcript equals the OpenJDK transcript

#### Scenario: AVR transcript matches OpenJDK

- **WHEN** a required primitive program runs under the AVR runner
- **THEN** the runner passes only when its serial transcript equals the OpenJDK transcript

#### Scenario: Mismatch fails with diagnostic context

- **WHEN** any required architecture produces a different serial transcript
- **THEN** its conformance test fails and records the expected and actual output for diagnosis

### Requirement: CI conformance evidence

CI SHALL execute the primitive conformance slice in isolated Thumb-2, AVR, and Xtensa backend jobs. After test
execution, each job SHALL upload its Failsafe JUnit XML with an unconditional artifact step so the report is retained
after either success or failure. CI SHALL publish three distinct report artifacts named `qemu-conformance-junit`,
`simavr-conformance-junit`, and `xtensa-conformance-junit`. Each artifact's parameterized test cases SHALL identify
the conformance program and target board.

When a comparison fails, CI SHALL additionally retain the generated assembly, shim, expected output, actual output,
and emulator log for the failed execution.

#### Scenario: Successful CI execution

- **WHEN** all required backend transcripts match their OpenJDK baselines
- **THEN** CI publishes all three backend JUnit report artifacts and marks the primitive conformance slice successful

#### Scenario: Failed CI execution

- **WHEN** a required backend transcript differs from its OpenJDK baseline
- **THEN** CI fails the job, retains that backend's JUnit report artifact, and publishes its failure evidence

#### Scenario: A backend job stops before a successful comparison

- **WHEN** the Thumb-2, AVR, or Xtensa job fails before all comparison test cases complete
- **THEN** its JUnit report artifact upload still runs after test execution
