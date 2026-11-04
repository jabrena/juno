## Why

Issue #213 still has no catalogued coverage for inheritance, generics, or arrays. Legacy QEMU programs mix these
concerns with records, lambdas, optimizations, and wide-number behavior, so they cannot localize a regression in
dispatch, generic erasure, or array runtime semantics. The three categories are a single requested conformance
tranche: each adds one focused, device-independent fixture to the already-established cross-backend corpus.

## What Changes

- Add `inheritance`, `generics`, and `arrays` scopes and required semantic areas to the catalog.
- Add one focused fixture per category for virtual/interface dispatch; generic classes/methods after erasure; and
  array creation, access, length, stores, multidimensional arrays, and bounds exceptions.
- Require all three existing emulator backends and make catalog validation reject a missing area.

## Impact

- `juno-compiler/src/test/resources/conformance/catalog.xml` and three new fixtures beneath `programs/`.
- Catalog validation tests only; no new compiler capability, public API, dependency, or CI job.

## Source and Derivation

- **Requirements source:** GitHub issue [#213](https://github.com/jabrena/juno/issues/213), retrieved
  2026-11-04T17:25:43Z through authenticated read-only GitHub access; accessible comment count: 1.
- **Supplementary authority:** Java SE 27 JLS §§4.4–4.6, 8.1.2, 8.1.4–8.1.5, 8.4.4, 8.4.8.1, 10.1–10.7, and
  15.10.1–15.10.3.
- **Derived artifact:** this new OpenSpec change.
- **Direction:** issue → OpenSpec. This change does not synchronize back to the issue.

## Stacking

This change is stacked on `feat/records-and-sealed-conformance` (PR #259), itself stacked on
`feat/controlflow-conformance` (PR #258). Retarget only after its parent has merged.
