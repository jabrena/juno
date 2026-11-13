## ADDED Requirements

### Requirement: Board data is the capability authority

The compiler SHALL derive whether a target supports a capability from that target's per-board capability data. Capability-dependent ordinary API calls SHALL be associated with capabilities independently of concrete public facade owner names.

#### Scenario: Two facades expose the same capability-bearing service

- **WHEN** reachable ordinary getters on different board facades expose the same capability-bearing service
- **THEN** the compiler applies the same capability requirement without requiring one owner-specific rule per facade

#### Scenario: A portable ordinary call is reachable

- **WHEN** a reachable ordinary method has no capability requirement
- **THEN** the linker does not reject it solely because of the public facade that owns it

### Requirement: Every reachable capability use is validated

The linker SHALL validate every reachable intrinsic, capability-bearing ordinary method, interface target, inheritance use, and watchdog declaration that has a capability requirement before backend generation. An unsupported use SHALL fail with `CompileException` naming the required capability-facing API and an offending board.

#### Scenario: An unsupported ordinary getter is reachable

- **WHEN** a program reaches a capability-bearing facade getter and a declared board lacks the required capability
- **THEN** compilation fails before backend generation with the getter's capability/API and the unsupported board in the diagnostic

#### Scenario: An unsupported intrinsic is reachable

- **WHEN** a program reaches a capability-dependent intrinsic and a declared board lacks the required capability
- **THEN** compilation fails before backend generation with the intrinsic's capability/API and the unsupported board in the diagnostic

#### Scenario: Capability-dependent code is unreachable

- **WHEN** a capability-dependent method exists on the classpath but is not reachable from the entry point
- **THEN** that method does not cause capability validation to fail

### Requirement: Multi-board declarations remain portable

Every distinct board declared by an entry point's `@Board` annotation SHALL support every capability used by the reachable program. Selecting one declared board with `--board` or `-Djuno.board` SHALL select the generated target but SHALL NOT bypass validation of any other declared board.

#### Scenario: Every declared board supports the program

- **WHEN** an entry point declares several boards, every declared board supports all reachable capabilities, and one declared board is selected
- **THEN** compilation succeeds for the selected target

#### Scenario: A non-selected declared board lacks a capability

- **WHEN** an entry point declares a capable selected board and another board that lacks a reachable capability
- **THEN** compilation fails and names the incapable declared board

### Requirement: Target selection is explicit when annotation data is insufficient

The compiler SHALL obtain one generated target from either a single `@Board` value or an explicit requested board. An entry point with no `@Board` SHALL require `--board` or `-Djuno.board`; an entry point declaring several boards SHALL require an explicit selection; and an explicit selection for an annotated entry point SHALL be one of its declared boards.

#### Scenario: One board is declared

- **WHEN** an entry point declares exactly one supported board and no board option is supplied
- **THEN** the compiler selects that declared board

#### Scenario: No board source exists

- **WHEN** `juno:compile` or the standalone compiler receives an unannotated entry point and no board option
- **THEN** compilation fails with guidance to supply `-Djuno.board=<id>` or `--board=<id>` as appropriate

#### Scenario: Several boards are declared without a selection

- **WHEN** an entry point declares more than one board and no board option is supplied
- **THEN** compilation fails rather than selecting by annotation order

#### Scenario: The requested board is outside the declaration

- **WHEN** an annotated entry point receives a board option that is not among its declared boards
- **THEN** compilation fails and identifies the mismatch

### Requirement: Capability validation is independent of target naming

Capability validation SHALL use reachable `MCU` services and per-board capability data rather than inferring support from the selected board's name or a removed facade owner. Successful migrated programs SHALL retain their selected target and deterministic generated behavior.

#### Scenario: A multi-board program uses a portable MCU getter

- **WHEN** every declared board supports the getter or the getter has no capability requirement
- **THEN** the program compiles for each explicitly selected declared board

#### Scenario: An accepted program is migrated to MCU

- **WHEN** a program replaces a legacy facade getter with its equivalent `MCU` getter and satisfies the same capability rules
- **THEN** its target behavior and generated runtime semantics remain unchanged
