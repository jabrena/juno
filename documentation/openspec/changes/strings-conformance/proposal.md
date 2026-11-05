## Why

Issue #213 identifies string concatenation and `String.valueOf` as semantic areas, but the only existing candidate is
a legacy QEMU program. A focused catalogued fixture is needed to pin the `StringConcatFactory` path, literal handling,
null conversion, and supported primitive conversion on every backend.

## What Changes

- Add a `strings` scope and required concatenation/conversion area to the conformance catalog.
- Add `StringSemantics`, a device-independent serial fixture for literals, primitive and null concatenation, and the
  supported `String.valueOf(int)` and `String.valueOf(double)` intrinsics.
- Reuse the existing three backend runners and catalog validation; no compiler feature or public API changes.

## Source and Derivation

- **Requirements source:** GitHub issue [#213](https://github.com/jabrena/juno/issues/213), retrieved
  2026-11-04T17:25:43Z through authenticated read-only GitHub access; accessible comment count: 1.
- **Supplementary authority:** Java SE 27 JLS §§3.10.5–3.10.6, 5.1.11, 5.4, and 15.18.1.
- **Direction:** issue → OpenSpec; no automatic synchronization back to the issue.

## Stacking

This change is stacked on `feat/inheritance-generics-arrays-conformance` (PR #260), which is stacked on #259 and
#258. Retarget after its direct parent merges.
