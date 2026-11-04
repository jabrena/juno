## 1. Catalog and corpus

- [x] 1.1 Add a `controlflow` scope and areas to `catalog.xml` with the JLS clauses from `design.md` (schema version 2: covered sections, plus skipped sections with a reason).
- [x] 1.2 Add `programs/controlflow/BranchesAndBlocks.java`: `if`/`else if`, dangling `else`, nested blocks and sibling-scoped names, empty statement, labeled block.
- [x] 1.3 Add `programs/controlflow/Loops.java`: `while` (zero iterations), `do` (body runs once), basic `for` with comma-separated init/update and an empty header, enhanced `for` over a non-empty and an empty array.
- [x] 1.4 Add `programs/controlflow/JumpsAndLabels.java`: unlabeled and labeled `break`/`continue` through nested loops, `continue` inside a `switch` inside a loop, `return` from inside loops and switches.
- [x] 1.5 Add `programs/controlflow/SwitchStatement.java`: dense and sparse labels, negative labels, `Integer.MIN_VALUE`/`MAX_VALUE`, `default` first/middle/last, fall-through, no matching label, `char`/`byte`/`short` selectors.
- [x] 1.6 Add `programs/controlflow/SwitchExpression.java`: arrow and colon forms, `yield` from nested blocks, switch expression used as an operand.
- [x] 1.7 `AssertDisabled.java`: written, then removed. javac compiles `assert` to a class literal in `<clinit>`, which Juno rejects in user code (`ClojureSupport.validateClassLiteral`). JLS §14.10 is recorded as a `Gap:` in the catalog.
- [-] 1.7a `PatternMatching.java`: not written. The decoder rejects `instanceof` (opcode 0xC1), so the fixture cannot compile. Moved to a compiler change, outside this OpenSpec.
- [-] 1.7b `PatternSwitch.java`: not written, for the same reason, plus `invokedynamic` `typeSwitch` is not lowered.
- [x] 1.7c Decision: pattern switches need either direct type tests or `SwitchBootstraps` lowering. Neither exists today, so the pattern areas stay `Gap:` in the catalog with the specific reason.
- [x] 1.8 Each program is device-independent (serial transcript only) and declares `@Board` for Nano ESP32, Thumb-2, and AVR.
- [ ] 1.9 Re-read JLS §§14.11, 14.14, 14.15–14.17, and 14.21 on the Oracle page against each fixture; record the section in the fixture's catalog area. (Not done: the fixtures were written from the section summaries.)

## 2. Execution

- [x] 2.1 Run the new programs under `QemuRunIT` (Thumb-2): 107/107 pass, including the five control-flow programs on UNO R4 WiFi and UNO Q.
- [x] 2.1 Run the new programs under `SimavrRunIT` (AVR): 45/45 pass, including the five control-flow programs.
- [x] 2.1 Run the new programs under `XtensaRunIT` (Nano ESP32, published amd64 image `sha-339cf67a…` via `-Djuno.xtensaImage`): 12/12 pass, including the five control-flow programs.
- [x] 2.2 The one program that did not compile (`AssertDisabled`) was removed and its JLS section recorded as a gap, following the compiler's limit rather than changing the semantics under test.
- [ ] 2.3 Confirm the dense-switch fixture fits the AVR arena. (Not checked separately: the AVR run passed, so it fits at the current size.)

## 3. Verification

- [x] 3.1 `./mvnw -pl juno-compiler test -Dtest='Conformance*Test'`: 16/16 pass (catalog validation, classification rules, workspace).
- [ ] 3.1b Full `./mvnw test` before proposing the change. (Not run yet.)
- [x] 3.2 The three runners were run locally: Thumb-2 under Docker, AVR under simavr, Xtensa with the published amd64 image.
- [ ] 3.3 Push a branch and confirm the three CI backend jobs and the consolidated `conformance-junit-reports` artifact.

## 4. Closeout

- [ ] 4.1 Tick the `controlflow/` box in issue #213 only after CI passes; leave the issue open for the remaining categories.
- [ ] 4.2 Archive this change with the `close-spec` workflow once its tasks are complete.
