## 1. Catalog and fixtures

- [ ] 1.1 Add the `concurrency` scope with JLS §§8.3.1.4, 8.4.3.6, 14.19, and 17.1–17.7, each covered or skipped with a reason.
- [ ] 1.2 Add `MonitorsAndWaitSets`, `ThreadLifecycle`, `VisibilityAndAtomics`, and `WordTearing` under `programs/concurrency/`, each with the four-board `@Board` annotation.
- [ ] 1.3 Add `LongFieldPublication` under `programs/concurrency/`, with `@Board` restricted to the UNO R4 WiFi and the UNO Q.
- [ ] 1.4 Add the concurrency areas, with `REQUIRED` on every backend except `LongFieldPublication` (`UNSUPPORTED` on AVR and Xtensa). Confirm the linker diagnostic for an `Iterable` enhanced `for` and record it in the scope comment.
- [ ] 1.5 Add the `collections` scope with JLS §14.14.2, add `FixedCapacityList` under `programs/collections/`, and add its area.
- [ ] 1.6 Check each cited JLS section number against the JLS 27 HTML, since the catalog's citations must match the specification.
- [ ] 1.7 Extend `ConformanceCatalogTest` with the new scopes and the required-area list.

## 2. Verification

- [ ] 2.1 Run strict OpenSpec validation (`openspec validate concurrency-collections-conformance --strict`) and the focused `ConformanceCatalogTest`.
- [ ] 2.2 Run each new fixture under OpenJDK to fix its expected transcript, then under Thumb-2/QEMU, AVR/simavr, and Xtensa/Espressif QEMU, twice each, comparing with OpenJDK.
- [ ] 2.3 For any backend that fails a `REQUIRED` area, fix the cause or move the status to `UNSUPPORTED` with a limit documented in `java-subset.md` in the same commit. Do not leave a failing `REQUIRED` area.
- [ ] 2.4 Push the stacked branch, open its PR against `feat/exceptions-conformance`, and confirm CI before updating the issue category.

## 3. Follow-up (not in this change)

- [ ] 3.1 Decide whether Juno should implement the `Iterable` enhanced `for`. This is compiler work and needs the user's approval before any implementation.
- [ ] 3.2 Decide whether the legacy coordination-class programs (`BlockingPrimitives`, `CyclicBarrier*`, `PhaserPhases`, `ReadWriteLockCounter`, `TimedAndFairLocks`, `ConditionBoundedBuffer`) move into the catalog or stay in `src/test/qemu`.
