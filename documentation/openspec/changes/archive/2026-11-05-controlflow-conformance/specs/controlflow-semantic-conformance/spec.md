## ADDED Requirements

### Requirement: Control-flow areas in the conformance catalog

`juno-compiler/src/test/resources/conformance/catalog.xml` SHALL define the control-flow semantic areas listed in
the table below. Each area SHALL cite the Java SE 27 JLS clauses in that table, reference at least one focused
program under `programs/controlflow`, and declare an expectation for Thumb-2, AVR, and Xtensa. Catalog validation
SHALL fail under the same rules that apply to the primitives pilot (unmapped source, unclassified program, empty
area, missing fixture, malformed or external-resource XML).

| Semantic area | Required JLS references |
|---|---|
| Blocks, scope, and labels | §§14.2, 14.4, 14.5, 14.6, 14.7 |
| Conditional statements | §§14.9.1, 14.9.2 |
| Loops | §§14.12, 14.13, 14.14.1, 14.14.2 (arrays only) |
| Jump statements | §§14.1, 14.15, 14.16, 14.17 |
| Switch statements | §§14.11.1.2, 14.11.2, 14.11.3 (integral and `char`/`byte`/`short` selectors) |
| Switch expressions and `yield` | §§14.21, 15.28 (integral selectors) |
| Disabled assertions | §14.10 (assertions disabled only) |
| Pattern matching | §§14.11.1, 14.30, 15.20.2, 6.3.2 (pattern-variable scope) |

#### Scenario: Each control-flow area is catalogued with its JLS references

- **WHEN** catalog validation loads the control-flow areas
- **THEN** every area in the table is present with its required JLS references and at least one focused program

#### Scenario: A jump statement is catalogued

- **WHEN** a catalog entry describes `break`, `continue`, or `return`
- **THEN** it identifies JLS §14.15, §14.16, or §14.17 and at least one program that uses the statement through a
  nested loop or switch

### Requirement: Control-flow areas not claimed by this suite

The control-flow catalog SHALL NOT claim coverage for the following, which belong to other categories or to the
compiler's static checks: `throw`, `try`/`catch`/`finally`, and `try`-with-resources (JLS §§14.18, 14.20;
`exceptions/`); `synchronized` (§14.19; `concurrency/`); `String` and `enum` selectors (§14.11; `strings/` and
`collections/` or `inheritance/`); the `Iterable` form of the enhanced `for` (§14.14.2); local classes (§14.3); and
unreachable-statement checks (§14.22), which javac enforces at compile time. Switch expressions and pattern matching
are in this area: switch expressions (§§14.21, 15.28), `instanceof` type and record patterns (§15.20.2), pattern
switch labels, guards, `case null`, and exhaustiveness over sealed types (§§14.11.1.1, 14.30), and pattern-variable
flow scoping (§6.3.2).

#### Scenario: A pattern switch is catalogued

- **WHEN** a controlflow fixture contains a pattern label, a guard, or `case null`
- **THEN** the catalog cites JLS §14.11.1 and §14.30 and the fixture is compared with OpenJDK like any other
  control-flow program

#### Scenario: A try-finally is encountered in a controlflow fixture

- **WHEN** a controlflow fixture uses `try`/`finally` to test a jump
- **THEN** the jump belongs to the `exceptions/` category and the fixture is moved there

### Requirement: Focused control-flow corpus

The suite SHALL provide deterministic, device-independent Java fixtures under
`juno-compiler/src/test/resources/conformance/programs/controlflow` that cover, at minimum: dense and sparse
`switch` labels including negative values and `Integer.MIN_VALUE`/`MAX_VALUE`; `default` in the first, middle, and
last position; fall-through; a `switch` with no matching label; labeled and unlabeled `break` and `continue` through
nested loops and switches; `return` from inside loops and switches; `do` bodies that run once before the test;
`for` headers with comma-separated updates and an empty condition; enhanced `for` over empty and non-empty arrays;
`yield` from a nested block inside a switch expression; a disabled `assert` that produces no output; `instanceof`
type and record patterns; pattern switch labels with a guard and `case null`; exhaustive pattern switches over a
sealed hierarchy; and a pattern variable that stays in scope after an `if` that exits early.

#### Scenario: A fixture has no device dependency

- **WHEN** a control-flow fixture is compiled for a supported architecture
- **THEN** it requires only the serial-output test interface and no device, network, or physical-board API

#### Scenario: Switch boundary labels are exercised

- **WHEN** the switch-statement fixture is compiled
- **THEN** it contains both a dense label range and a sparse label set, so both `tableswitch` and `lookupswitch`
  encodings are generated and compared

### Requirement: OpenJDK equivalence on required architectures

For every control-flow program required on an architecture, the suite SHALL compile the same source for OpenJDK and
Juno and SHALL require the generated-code serial transcript to equal the OpenJDK transcript.

#### Scenario: Thumb-2 transcript matches OpenJDK

- **WHEN** a required control-flow program runs under the Thumb-2 runner
- **THEN** the runner passes only when its serial transcript equals the OpenJDK transcript

#### Scenario: Xtensa transcript matches OpenJDK

- **WHEN** a required control-flow program runs under the Xtensa runner
- **THEN** the runner passes only when its serial transcript equals the OpenJDK transcript

#### Scenario: AVR transcript matches OpenJDK, or a size limit is recorded

- **WHEN** a required control-flow program runs under the AVR runner
- **THEN** the runner passes only when its serial transcript equals the OpenJDK transcript
- **AND** a program that cannot fit the AVR arena is recorded as a size limit with its reason, not as a semantic
  mismatch

#### Scenario: Mismatch fails with diagnostic context

- **WHEN** any required architecture produces a different serial transcript
- **THEN** its conformance test fails and records the expected and actual output for diagnosis

### Requirement: Unsupported constructs are declared, not dropped

If a control-flow construct is unsupported on a backend, its area SHALL declare that backend `UNSUPPORTED` with a
reason, and the fixture SHALL remain in the catalog as a regression test for the backends that support it. A
backend status SHALL NOT be lowered silently to make a run pass.

#### Scenario: A construct fails on one backend

- **WHEN** a control-flow fixture fails on one backend because of a backend limit
- **THEN** the catalog records that backend as `UNSUPPORTED` with a reason, and the other backends keep `REQUIRED`

### Requirement: CI conformance evidence

CI SHALL execute the control-flow slice in the existing isolated Thumb-2, AVR, and Xtensa backend jobs, without a new
job. The existing unconditional JUnit XML upload, the failure-only evidence artifact (generated assembly, shim,
expected and actual output, emulator log), and the consolidated `conformance-junit-reports` artifact SHALL cover
the control-flow programs. Each parameterized entry SHALL identify the conformance program and target board.

#### Scenario: Successful CI execution

- **WHEN** all required backend transcripts match their OpenJDK baselines
- **THEN** CI publishes all three backend JUnit report artifacts with the control-flow entries and marks the slice
  successful

#### Scenario: Failed CI execution

- **WHEN** a required backend transcript differs from its OpenJDK baseline
- **THEN** CI fails that job, retains its JUnit report, and publishes the failure evidence for the failed program

### Requirement: Every JLS section in scope is classified

Each category SHALL list every JLS section it speaks about in a `<scope>` of the catalog. Every such section SHALL be
either covered by a program of an area or skipped with a reason that begins with `Gap:`, `Other category:`, or
`Out of scope:`. A section SHALL NOT be both covered and skipped, and an area SHALL NOT cite a section outside its
category's scope. The catalog SHALL report covered and skipped counts per scope, so the share still missing is
visible.

#### Scenario: A section in scope is not mentioned by any area

- **WHEN** a scope lists a JLS section that no area covers or skips
- **THEN** catalog validation fails and names the unclassified section

#### Scenario: A skipped section has no reason

- **WHEN** an area marks a JLS section `skipped="true"` without a `reason`
- **THEN** catalog loading fails and names the section

#### Scenario: Coverage is reported

- **WHEN** the catalog is validated
- **THEN** each scope reports its covered count, its total, and its skipped counts split by reason label
