## Why

Issue #213 lists exceptions and unwinding as a semantic area, but the only candidates are legacy QEMU programs.
`ArithmeticExceptions` depends on `java.math`, which is not a portable target for the AVR and Xtensa runners, and
`Unwinding` mixes several concerns in one file. A focused catalogued set is needed to pin the JVM behavior of throw,
catch matching, `finally`, and try-with-resources on every backend.

## What Changes

- Add an `exceptions` scope to the conformance catalog covering JLS chapter 11 and §§14.18 and 14.20, with each
  section either covered or skipped with a reason (`Out of scope:` for the compile-time rules javac enforces).
- Add four device-independent serial fixtures under `programs/exceptions/`:
  `ExceptionCauses`, `ThrowAndCatch`, `FinallyAndUnwinding`, and `TryWithResources`.
- Add four areas that reference them, each required on Thumb-2, AVR, and Xtensa.
- Reuse the existing three backend runners and catalog validation; no compiler feature or public API changes.

## Source and Derivation

- **Requirements source:** GitHub issue [#213](https://github.com/jabrena/juno/issues/213), retrieved
  2026-11-04 through authenticated read-only GitHub access; its `exceptions/` category is the scope of this change.
- **Supplementary authority:** Java SE 27 JLS chapter 11 (§§11.1–11.3), §14.18, and §§14.20–14.20.3.2.
- **Direction:** issue → OpenSpec; no automatic synchronization back to the issue.

## Stacking

This change is stacked on `feat/strings-conformance`, which is stacked on `feat/inheritance-generics-arrays-conformance`
(PR #260), #259, and #258. Retarget after its direct parent merges.
