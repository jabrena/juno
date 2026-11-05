## Why

The primitives category of issue #213 is complete: its catalog, runners, and CI evidence exist, and every
primitive program matches OpenJDK on Thumb-2, AVR, and Xtensa. The next category, `controlflow/`, covers the
statements that decide which code runs: blocks and labels, `if`, `switch` statements and expressions, the loops,
`break`, `continue`, and `return`. Today none of these is pinned by a catalogued program. Their behavior is only
implied by legacy QEMU programs such as `Optimizations`, `IntMath`, and `ArraysAndRecords`, which mix control flow
with other concerns, so a regression in a jump, a fall-through, or a switch encoding could merge unnoticed. The
optimizer (CFG build, block merging, dead-block elimination) and the backend's `tableswitch`/`lookupswitch`
selection make this area a high-risk one.

## What Changes

- Add a `controlflow` semantic-area set to the conformance catalog, citing the Java SE 27 JLS Chapter 14 clauses
  and the switch-expression clauses of Chapter 15 that the subset supports.
- Add focused, device-independent Java fixtures under `juno-compiler/src/test/resources/conformance/programs/controlflow`,
  each comparing its serial transcript with OpenJDK on Thumb-2, AVR, and Xtensa.
- Cover the boundaries that a code generator tends to get wrong: sparse and negative `switch` labels, `default`
  in the middle, fall-through, nested labeled `break`/`continue`, `return` from inside loops and switches, and
  `yield` from nested blocks.
- Include pattern matching (`instanceof` and `switch` patterns, record patterns, exhaustiveness, pattern-variable
  flow scoping) in this area, since each one changes which statements run.
- Declare explicitly which Chapter 14 areas belong to other categories (`exceptions/`, `concurrency/`,
  `strings/`, `arrays/`) or are compile-time only, so the catalog does not overclaim.

## Capabilities

### New Capabilities

- `controlflow-semantic-conformance`: A catalogued, deterministic OpenJDK-versus-Juno conformance corpus for Java
  statement-level control flow across the three emulator backends.

### Modified Capabilities

None. The runner, catalog schema, and CI wiring established by `primitives-conformance-pilot` are reused unchanged;
only catalog entries and fixtures are added.

## Impact

- `juno-compiler/src/test/resources/conformance/catalog.xml` (new program and area entries) and
  `programs/controlflow/` (new fixtures).
- Possibly a catalog-level backend status change if a construct is found unsupported on a backend; any such case is
  recorded as `UNSUPPORTED` with a reason, not dropped silently.
- No board-side public API, supported bytecode subset, Maven dependency, or CI job is added. The existing
  `QemuRunIT`, `SimavrRunIT`, and `XtensaRunIT` runners pick up the new programs by discovery.
- Compiler changes are out of scope: if a fixture exposes a Juno bug, that bug is fixed in its own change and the
  fixture stays in the catalog as the regression test.

## Source and Derivation

- **Requirements source:** GitHub issue [#213](https://github.com/jabrena/juno/issues/213), category
  `controlflow/` (unchecked), and the JLS Chapter 14 review recorded in `design.md`.
- **Derived artifact:** this OpenSpec change, created 2026-11-04.
- **Direction:** issue → OpenSpec. This change does not synchronize back to the issue.
