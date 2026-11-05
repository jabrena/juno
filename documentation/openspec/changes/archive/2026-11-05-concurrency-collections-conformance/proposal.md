## Why

Issue #213 leaves two semantic categories, `concurrency/` and `collections/`, without catalogued coverage. The
concurrency candidates are legacy QEMU programs that mostly target only the UNO R4 WiFi and the UNO Q, so the AVR and
Xtensa runners have no catalogued thread, monitor, or memory-visibility check. The `collections/` category has no
candidates at all: Juno implements no `java.util` collection framework, and the `Iterable` form of the enhanced `for`
(JLS §14.14.2) has no runtime. The category has to say what is supported and record what is not.

## What Changes

- Add a `concurrency` scope to the conformance catalog covering JLS §§8.3.1.4, 8.4.3.6, 14.19, and 17.1–17.7, with each
  section either covered or skipped with a reason.
- Add five device-independent serial fixtures under `programs/concurrency/`: `MonitorsAndWaitSets`, `ThreadLifecycle`,
  `VisibilityAndAtomics`, `WordTearing`, and `LongFieldPublication`. The last one targets only the Arm boards, because
  the 64-bit atomicity claim in the Java subset is made for them.
- Add a `collections` scope covering JLS §14.14.2 for user-defined sequences over primitive arrays, with one fixture
  `FixedCapacityList` under `programs/collections/`.
- Record the collection gaps in the scope comment and in the design: the `Iterable` enhanced `for` and the `java.util`
  collection classes.
- Reuse the existing three backend runners and catalog validation; no compiler feature or public API changes.

## Source and Derivation

- **Requirements source:** GitHub issue [#213](https://github.com/jabrena/juno/issues/213), retrieved 2026-11-04 through
  authenticated read-only GitHub access; its `concurrency/` and `collections/` categories are the scope of this change.
- **Supplementary authority:** Java SE 27 JLS §§8.3.1.4, 8.4.3.6, 14.14.2, 14.19, and 17.1–17.7, and the Java subset
  page of this repository (`juno-site/src/main/resources/content/java-subset.md`) for what Juno claims on each board.
- **Direction:** issue → OpenSpec; no automatic synchronization back to the issue.

## Stacking

This change is stacked on `feat/exceptions-conformance` (PR #262), which is stacked on `feat/strings-conformance` (PR
#261), `feat/inheritance-generics-arrays-conformance` (PR #260), #259, and #258. Retarget after its direct parent merges.
