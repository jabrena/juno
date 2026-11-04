## Why

Issue #213 now has catalogued primitives and control-flow slices, but `records/` and `sealed/` remain unchecked.
`ArraysAndRecords` is a legacy QEMU program that mixes arrays, strings, enums, and one record; it neither makes
record constructor/component behavior visible nor establishes that class files with `PermittedSubclasses` attributes
run equivalently on every supported backend. A regression in Juno's record class model or its handling of sealed
declarations could therefore reach hardware without a Java-versus-Juno comparison.

## What Changes

- Add `records` and `sealed` scopes and required semantic areas to the conformance catalog, with Java SE 27 JLS
  references and `REQUIRED` status for Thumb-2, AVR, and Xtensa.
- Add a focused `RecordClasses` fixture for component accessors, declared instance members, and normal and compact
  canonical constructors.
- Add a focused `SealedTypes` fixture for explicit `permits` clauses on interfaces and classes, final permitted
  subclasses, and virtual/interface method dispatch through the sealed hierarchy.
- Make catalog validation require both new areas and include them in coverage reporting.

## Capabilities

### New Capabilities

- `records-and-sealed-semantic-conformance`: Catalogued OpenJDK-versus-Juno conformance fixtures for record classes
  and sealed type hierarchies across the three existing emulator backends.

### Modified Capabilities

None. This change reuses the existing catalog schema, runners, backend matrix, and CI artifact publishing.

## Impact

- `juno-compiler/src/test/resources/conformance/catalog.xml`
- `juno-compiler/src/test/resources/conformance/programs/records/RecordClasses.java`
- `juno-compiler/src/test/resources/conformance/programs/sealed/SealedTypes.java`
- Catalog validation tests only; no public API, compiler capability, dependency, or CI workflow changes.

## Source and Derivation

- **Requirements source:** GitHub issue [#213](https://github.com/jabrena/juno/issues/213), retrieved
  2026-11-04T17:15:00Z through authenticated read-only GitHub access; accessible comment count: 0.
- **Supplementary authority:** Java Language Specification, Java SE 27, §§8.1.1.2, 8.1.6, 8.10, and 9.1.1.4/9.1.4.
- **Derived artifact:** this new OpenSpec change.
- **Direction:** issue → OpenSpec. This change does not synchronize changes back to the issue.

## Stacking

This change is stacked on `feat/controlflow-conformance` (PR #258) and must be retargeted to `main` only after that
parent branch merges.
