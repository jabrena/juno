## Context

The primitives category established the pattern: a JLS-referenced XML catalog, focused fixtures, and three runners
that compare each program's serial transcript with OpenJDK. Control flow reuses all of it. The design question is
not infrastructure but scope: which parts of JLS Chapter 14 a conformance program must pin, and which belong
elsewhere.

Chapter 14 (Java SE 27) defines the statements. Its sections 14.23–14.29 are reserved and unused. The review below
uses the Oracle JLS 27 chapter for the section list and summaries. The page is large and the review read only its
first part in full, so the rules for §§14.15–14.30 are taken from the JLS as cited. Each area should be re-read
against the page when its fixtures are written.

## JLS 14 coverage review

Legend: **Cover** = a program in this change; **Other** = owned by another category; **Compile-time** = a
javac/Juno rejection, not a serial transcript, so outside this runtime suite.

| JLS | Construct | Decision | Reason |
|---|---|---|---|
| 14.1 | Normal and abrupt completion | Cover (cross-cutting) | Every jump test exercises it; a dedicated program combines `break`, `continue`, and `return` through nested statements. |
| 14.2 | Blocks, scope | Cover | Shadowing in nested blocks and the re-use of a name after its block ends. |
| 14.3 | Local classes | Other (`inheritance/`) | Needs class-level semantics. |
| 14.4 | Local variables, `var` | Cover (declarations only) | Initialization order inside loops (a fresh declaration per iteration). The `var` restrictions are compile-time. |
| 14.5 | Statements | Cover | Statement sequencing and the statement forms listed in this table. |
| 14.6 | The empty statement | Cover | `;` as a loop body. |
| 14.7 | Labeled statements | Cover | Labeled `break` out of nested loops and a labeled block. |
| 14.8 | Expression statements | Cover (implicit) | Used throughout; no dedicated program. |
| 14.9.1, 14.9.2 | `if`-`then`, `if`-`then`-`else` | Cover | Condition evaluation, `else if` chains, and the dangling `else` (binds to the nearest `if`). Short-circuit operators are primitives' area. |
| 14.10 | `assert` | Gap (compiler) | The JVM default is assertions disabled: no evaluation and no output. Juno must match the disabled behavior; enabled assertions are not in the runtime suite. |
| 14.11.1–14.11.3 | `switch` statement on `int`-compatible types | Cover | The core of the area: dense and sparse labels, negative labels, `MIN_VALUE`/`MAX_VALUE`, `default` first/middle/last, fall-through, no matching label, `break`. `char`/`byte`/`short` selectors. |
| 14.11.1.1 | Exhaustive switch blocks | Gap (compiler) | Exhaustive pattern switches over sealed hierarchies and record patterns, and the compile-time checks that make them exhaustive; the runtime fallback is `MatchException`. |
| 14.11.1.2 | Which label applies at run time | Cover | Covered by the dispatch programs; the ordering rule is observable through fall-through. |
| 14.11 | `String` selector | Other (`strings/`) | Lowered through `String.hashCode`, a JDK-library behavior. |
| 14.11 | `enum` selector | Other (`inheritance/` or `collections/`) | Depends on the enum runtime; decided when that category starts. |
| 14.11 | Pattern labels, guards (`when`), `case null` | Gap (compiler) | Type and record pattern labels, guards, and the `case null` and null-selector rules. Dispatch goes through `SwitchBootstraps`/`invokedynamic`, which Juno must support (see Risks). |
| 14.12 | `while`, abrupt completion | Cover | Zero iterations, `break`, `continue` re-evaluates the condition. |
| 14.13 | `do` | Cover | Body runs once before the first test; `continue` evaluates the condition. |
| 14.14.1 | Basic `for` | Cover | Init once; `continue` runs the update before the test; comma-separated init/update; missing condition; empty header. |
| 14.14.2 | Enhanced `for` over arrays | Cover (loop mechanics) | Array iteration, including a zero-length array. Array element types and bounds are `arrays/`. |
| 14.14.2 | Enhanced `for` over `Iterable` | Other (`collections/`) | Needs the `Iterable` runtime. |
| 14.15 | `break` | Cover | Unlabeled (innermost loop or switch), labeled. |
| 14.16 | `continue` | Cover | Unlabeled, labeled, inside `switch` within a loop. |
| 14.17 | `return` | Cover | `return` from inside loops, switches, and nested blocks; `void` and value returns. |
| 14.18 | `throw` | Other (`exceptions/`) | Throw semantics and unwinding belong to the exceptions category. |
| 14.19 | `synchronized` | Other (`concurrency/`) | Monitor semantics. |
| 14.20 | `try`, `catch`, `finally` | Other (`exceptions/`) | A `finally` that overrides a `break`/`return` is an exceptions-category case. |
| 14.21 | `yield` | Cover (with §15.28) | `yield` from nested blocks inside a switch expression. |
| 14.22 | Unreachable statements | Compile-time | The fixtures must compile under javac, which is the check. The rules still matter: `while (true)` with `break` is the runtime shape the CFG builder must handle. |
| 14.23–14.29 | Reserved | Not applicable | Unused in JLS 27. |
| 14.30 | Patterns: kinds, matching, properties | Gap (compiler) | Type and record patterns, nested record patterns, pattern variables and their definite assignment. |
| §15.20.2 | `instanceof` with type and record patterns | Gap (compiler) | `if (o instanceof String s)` and record deconstruction outside `switch`. |
| §6.3.2 (with §14.9) | Flow scoping of pattern variables | Gap (compiler) | `if (!(o instanceof String s)) return;` makes `s` in scope after the `if`; the same rule applies to `while` and `&&`/`||` conditions. |
| §15.28 | Switch expressions (for `yield`) | Cover | Value-producing switches on integral selectors, arrow and colon forms. Pattern switch expressions wait on the pattern gap above. |

## Decisions

- **Switch expressions and all pattern matching belong to this area.** `yield` is a Chapter 14 statement, and the
  switch expression is its only owner. Pattern matching (`instanceof` and `switch` patterns, record patterns,
  exhaustiveness over sealed types, pattern-variable flow scoping) is also here, because each of those changes which
  statements run. The `patterns/` category is not created; the issue's category list drops it.
- **`try`/`finally` interactions move to `exceptions/`.** The controlflow fixtures use `return`, `break`, and
  `continue` only outside `try`, so a failure in the exception path does not hide a control-flow failure.
- **Reachability is checked by javac, not by the transcript.** A fixture that would not compile is not a fixture.
  The runtime suite does not add a negative compile test.
- **`assert` is a gap.** javac compiles it to a class literal, which Juno rejects in user code, so the disabled form
  cannot run yet.
- **Backend status.** Every control-flow program declares `REQUIRED` for Thumb-2, AVR, and Xtensa. If a construct
  is unsupported on one backend, the area declares `UNSUPPORTED` with a reason in the design, and the fixture
  stays as a regression test for the supported backends. A status is never silently lowered.

## Catalog classification

The catalog (schema version 2) makes coverage measurable against a fixed list:

- Each category has a `<scope>` listing every JLS section it speaks about. Coverage is measured against these lists,
  so a section that no area mentions still counts as missing.
- Each `<area>` lists its JLS sections. A section is covered by the area's programs unless it carries
  `skipped="true"`, and then it must carry a `reason`.
- A reason starts with one of three labels, enforced by the schema: `Gap:` (the suite should cover it but does not
  yet), `Other category:` (another category owns it), or `Out of scope:` (not a runtime-suite concern, such as
  compile-time rules).
- The validator rejects: a section that is neither covered nor skipped; one claimed as both; an area citing a section
  outside its category's scope; and an area that covers sections without programs. Its summary reports covered and
  skipped counts per scope, split by label.

Areas belong to a category through the prefix of their id (`primitives.literals` → `primitives`).

## Risks

- **Switch encoding.** The backend chooses `tableswitch` or `lookupswitch` (`SwitchInstructionDecoder`). Boundary
  labels (dense, sparse, negative, extreme `int` values) exercise both encodings and the range checks between them.
- **Optimizer.** `BlockMerging`, `DeadBlockElimination`, and `BoundsCheckElimination` rewrite jump targets. The
  fixtures should be run with the optimizer on, which is the default; a case that breaks only with optimization is
  still a conformance failure.
- **Pattern switch dispatch.** javac compiles pattern switches to `invokedynamic` through `SwitchBootstraps.typeSwitch`
  (and `enumSwitch`). Juno's `invokedynamic` support today covers lambdas and string concatenation
  (`InvokeDynamicLowering`, `LambdaResolver`), not these bootstraps. Unless Juno lowers pattern switches to direct
  type tests, the pattern-switch fixture is `UNSUPPORTED` on the affected backends until that work exists. This is
  the largest open item in the change and must be settled in task 1.
- **Generated code size.** Dense switches on AVR (1 KiB arena) can grow the program. A fixture that fails on AVR
  for size is reported as a size limit, not as a semantic mismatch.
