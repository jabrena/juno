---
name: juno-debugging
description: Use when a Juno program misbehaves anywhere between the Java source and the board — a CompileException from Juno, an arduino-cli build failure in the generated sketch, wrong serial output, a hang, reset or out-of-memory stop on the board, a difference between boards, or generated output that changes unexpectedly. Gives a layer-by-layer triage (javac, Juno link/lower/optimize/backend, arduino-cli, runtime, hardware), the tools for each layer (juno inspect, metrics, JUNO-RISK findings, GC log, juno:monitor, QEMU and arduino-cli harnesses), known pitfalls, and the test to add once the cause is found. Triggers include debug a Juno program, why does my sketch not work, board prints nothing, CompileException, juno:verify fails, generated assembly differs.
---
# Debugging Juno programs

A Juno program passes through five layers. Find the **first** layer where reality differs from expectation, then
reproduce the problem there with the smallest program you can, before changing any code.

| Layer | Symptom | Where to look |
|---|---|---|
| 1. `javac` | Ordinary Java compile error | The source; `target/classes` |
| 2. Juno (link → lower → optimize → backend) | `CompileException: Owner.method at bytecode offset N: …` | `limitations.md`, `juno inspect`, `javap -c -p` |
| 3. `arduino-cli` | Assembler/C++ error building `target/juno/<Main>Asm/` | The generated `.S` and `Shim.cpp` |
| 4. Runtime on the board | Wrong output, hang, reset, `[juno-gc] OOM`, silence | `juno:monitor`, `JUNO-RISK-*`, GC log |
| 5. Hardware | Works in QEMU or on one board only | Wiring, board capabilities, shield pins |

## 1. Make sure Juno compiles what you think it compiles

- Rebuild the classes with Maven before any Juno goal: `./mvnw -f juno-examples/pom.xml compile juno:compile -Djuno.main=<class>`.
  An IDE background build (the VS Code Java extension uses ECJ) can silently overwrite `target/classes` with
  different bytecode, so Juno then compiles something else than the command line does. Check with
  `javap -v target/classes/…/Main.class | grep major` (the Maven build targets release 27, major version 71).
- Generated output is deterministic: compiling the same classes twice must give byte-identical `.S` and
  `Shim.cpp`. If it does not, that is a compiler bug in its own right.

## 2. A `CompileException`

The message names the method and bytecode offset. Common causes are listed in
`juno-site/src/main/resources/content/limitations.md` (compound assignment on an array element compiles to `dup2`,
`length` of an array parameter, default interface methods, JDK functional interfaces, `Object` in signatures,
concatenating arbitrary objects). To see what Juno sees:

```bash
javap -c -p -cp juno-examples/target/classes <fully.qualified.Main>        # the bytecode at that offset
java -cp juno-compiler/target/classes:juno-api/target/classes \
     io.github.jabrena.juno.Main inspect --main <fully.qualified.Main> \
     --classpath juno-examples/target/classes:juno-api/target/classes --cfg --ir
```

`--cfg` prints each reachable method's basic blocks, `--ir` the lowered and optimized Juno IR; `--board <id>`
selects a target when `@Board` lists several. Rewrite the construct in the subset Juno accepts; do not widen the
accepted bytecode subset without asking (see `AGENTS.md`).

## 3. `arduino-cli` cannot build the sketch

Open the generated files in `juno-examples/target/juno/<Main>Asm/`. A missing symbol usually means a shim block
was not emitted because its `ShimFeature` was not recorded; an assembler error points at the backend instruction
selection. Reproduce without hardware:

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify -Djuno.main=<fully.qualified.Main>   # real arduino-cli, no board
./mvnw -pl juno-compiler -am -Parduino-cli verify                                       # fixture programs, in Docker
```

## 4. It builds but misbehaves on the board

- **Watch the serial port with the Maven plugin** (`./mvnw -f juno-examples/pom.xml juno:monitor`, `-Djuno.board`
  and `-Djuno.baudRate` as needed), never raw `arduino-cli monitor`. In a non-interactive session capture it with
  `sleep 30 | script -q serial.log ./mvnw -f juno-examples/pom.xml juno:monitor`.
- **Read the build's `JUNO-RISK-*` findings**: allocation in a loop (`001`), startup arena estimate (`002`),
  unchecked array accesses (`004`), possible division by zero (`005`), null dereference (`006`), threads started in
  loops or never joined (`008`/`009`), thread stack use (`010`/`011`).
- **Out of memory** stops the board with `[juno-gc] OOM: need …`. Rebuild with `-Djuno.gcLog=true` to log every
  collection, remove allocations from loops (see the `juno-zero-allocation` skill), and only then consider
  `-Djuno.Xmx`.
- **Silence or a reset right after printing**: a runtime string longer than 31 characters overflows its pool slot,
  so split long `Serial` lines; a task that overflows its stack needs `-Djuno.Xss` (thread stacks are 2 KiB on the
  UNO R4 WiFi, 4 KiB on the UNO Q, 512 bytes on the UNO WiFi Rev2).
- **Language or runtime behavior** (arithmetic, exceptions, strings, threads): add a small program to
  `juno-compiler/src/test/qemu/programs` and run `./mvnw -pl juno-compiler -am -Pqemu verify`. It runs the
  generated assembly in QEMU (Cortex-M4) and requires output identical to the same source on OpenJDK. QEMU models
  no hardware, and some bugs only appear in compiled firmware on a real board, so a green QEMU run is not proof.

## 5. Differences between boards

Compare the `--metrics` output and the generated shims for each `--board`. The UNO Q runs Zephyr and reaches the
network through an I/O worker; the UNO WiFi Rev2 is AVR (8-bit, safepoint preemption, 1 KiB arena); capabilities
(`Capability`) gate what each board accepts. Check the board's page under `juno-site/src/main/resources/content/`.

## When changing the compiler

- Compare against a **golden output** before trusting a refactoring: build `HEAD` in a separate worktree
  (`git worktree add --detach <dir> HEAD`, never `git stash` in a shared tree), compile every example for every
  board with both compilers from the same `javac`-built classes, and require byte-identical `.S`, `Shim.cpp` and
  reports.
- Add the regression test at the layer that failed: `BytecodeToIrTest` or `JunoCompilerTest` (lowering, intrinsics),
  `Thumb2AsmBackendTest`/`AvrAsmBackendTest`/`GeneratedAsmToolchainTest` (code generation), a QEMU program
  (runtime behavior), a `juno-maven-plugin` test with the fake executor (goals). Then run `./mvnw test` and
  `juno:verify`.
- Flash the affected example to a real board before calling the fix done, and ask before uploading.
