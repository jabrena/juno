## Context

The conformance infrastructure already compiles every source below `conformance/programs` with OpenJDK, then runs
the same source on the Thumb-2/QEMU, AVR/simavr, and Xtensa/Espressif-QEMU backends selected by `@Board`. This
change adds declaration-level semantics to that corpus without changing a compiler subsystem.

## Scope and JLS classification

| Category | JLS clauses | Fixture behavior |
|---|---|---|
| Records | §§8.10, 8.10.1–8.10.4.2 | component fields/accessors, declared instance methods, normal canonical constructor assignments, compact canonical constructor parameter transformation |
| Sealed types | §§8.1.1.2, 8.1.6, 9.1.1.4, 9.1.4 | class and interface `permits` clauses, `final` permitted subclasses, `invokevirtual` and `invokeinterface` dispatch |

The records fixture deliberately excludes generated `equals`, `hashCode`, and `toString`: javac realizes those methods
through the `ObjectMethods` `invokedynamic` bootstrap, which is not a record-declaration test and is not part of
Juno's currently supported `invokedynamic` subset. Pattern switches over sealed types remain owned by the
`controlflow` category and are still a known gap because they need `instanceof` and `typeSwitch` lowering.

## Decisions

- **One fixture per category.** Each source stays focused enough that a transcript mismatch identifies records or
  sealed hierarchy handling directly. The pre-existing `ArraysAndRecords` remains a legacy mixed-concern QEMU
  program and is not migrated by this change.
- **All three backends are required.** Both fixture shapes use object allocation, fields, constructors, and ordinary
  dispatch that each backend already claims to support. A backend-specific failure is a conformance failure, not a
  reason to lower a catalog status.
- **Explicit permits clauses.** The sealed fixture uses explicit `permits` lists so the class-file
  `PermittedSubclasses` attribute and the source-level direct-subtype contract are both present. It includes a
  sealed class as well as a sealed interface.
- **No compiler changes.** If a fixture exposes unsupported bytecode or a semantic mismatch, retain the fixture and
  create a separate compiler fix change. This change records the test corpus only.

## Risks

- A record constructor form can expose differences in field initialization order; using both normal and compact
  canonical constructors makes that output observable.
- A sealed declaration can be parsed yet reach a dispatch error only after linking its permitted leaf classes; the
  fixture invokes through both the interface and the sealed class hierarchy.
- Generated object methods and sealed pattern-switch exhaustiveness would expand the supported bytecode subset, so
  they remain explicitly outside this change.
