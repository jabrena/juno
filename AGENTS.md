# Agent Quickstart Guide

## Your role

You are a senior Java engineer specializing in compiler and toolchain development for embedded systems.

- Juno is an ahead-of-time compiler that lowers a small, checked subset of JVM bytecode to Arduino
  C++ or GNU Cortex-M4 assembly for the UNO R4 WiFi (Renesas RA4M1). Treat correctness of the
  classfile → linker → backend pipeline, and byte-for-byte reproducibility of generated sketches,
  as the top priorities.
- The project is deliberately closed-world and minimal: no JVM interpreter, no dynamic class
  loading, no general objects/arrays yet (see `docs/roadmap.md`). Prefer extending the existing
  intrinsic-lowering pattern over adding new language machinery.
- When a change touches what Java source can express, verify it compiles through the full chain:
  `javac` → `JunoCompiler` → generated `.ino` → real `arduino-cli` compile against
  `arduino:renesas_uno:unor4wifi` — not just `mvn test`.

## Tech stack

- **Language:** Java, `maven.compiler.release=25` (bytecode subset Juno accepts is far narrower —
  see `docs/FEATURES.md`). Build/dev JDK is pinned to 25 (GraalVM CE) via
  `.sdkmanrc` and CI (`.github/workflows/maven.yaml`).
- **Build:** Maven 3.9.16 via the `./mvnw` wrapper (`.mvn/wrapper/maven-wrapper.properties`).
- **Test framework:** JUnit (Jupiter) 6.1.3.
- **No runtime frameworks** — `juno` is a standalone CLI (`io.github.jabrena.juno.Main`), packaged
  as an executable jar via `maven-jar-plugin`; `juno-maven-plugin` is a conventional Maven plugin.
- **External toolchain:** Arduino CLI with the `arduino:renesas_uno` core, used to actually
  compile/upload generated sketches to real UNO R4 hardware; not a Maven dependency.

## File structure

This is a three-module Maven build: `juno` is the compiler, bundling the Java-facing hardware API
and `@Board` annotation types it recognizes as intrinsics; `juno-maven-plugin` integrates the
compiler and external Arduino CLI with Maven; and `juno-examples` contains example programs written
against `juno`'s `api` and `annotations` packages.

- `juno/src/main/java/io/github/jabrena/juno/api/` – WRITE here: the Java-facing hardware API
  (`Delay`, `Clock`, `LedMatrix`, `api.io.Gpio`, `api.io.DigitalOutput`, `api.io.hid.Mouse`,
  `api.io.usb.Serial`, …).
  Every `native` method here must have a matching entry in `juno`'s `intrinsic/IntrinsicRegistry.java`
  and `backend/ArduinoCppBackend#intrinsicExpression`, keyed by this package's fully-qualified
  class/method names as read from `.class` bytecode — not by a compile-time reference, so renaming
  or moving a class here means updating those registries too.
- `juno/src/main/java/io/github/jabrena/juno/annotations/` – WRITE here: `Board`, `ArduinoBoard`,
  `ArduinoUnoR4WiFi` — the entry-point-facing types a Juno program uses to select a compilation
  target. The compiler reads `@Board` directly from `.class` bytecode (see
  `classfile/ClassFileReader#BOARD_ANNOTATION_DESCRIPTOR` and `board/Board#apiClassName`) and the
  two must stay in lockstep with this package's fully-qualified name.
- `juno/src/main/java/io/github/jabrena/juno/classfile/` – WRITE here: `.class` parsing and
  constant pool resolution.
- `juno/src/main/java/io/github/jabrena/juno/bytecode/` – WRITE here: the admitted opcode
  subset (`BytecodeDecoder`). Extending this expands what Java syntax compiles.
- `juno/src/main/java/io/github/jabrena/juno/linker/` – WRITE here: closed-world reachability,
  `Intrinsics` registry, `Descriptor` type checks.
- `juno/src/main/java/io/github/jabrena/juno/backend/` – WRITE here: `ArduinoCppBackend`, the
  C++ emitter and intrinsic lowering (`intrinsicExpression`), plus `CppNames`.
- `juno/src/test/java/io/github/jabrena/juno/` – WRITE here: compiler unit tests and the
  generated-C++ syntax check (`GeneratedCppSyntaxTest`, requires `clang++`/`g++` locally; skips
  otherwise). These fixtures import `juno`'s own `api`/`annotations` classes via
  `CompilerTestSupport`, which resolves the compiler's own classpath relative to `juno`'s working
  directory (`target/classes`, which now holds `api`, `annotations`, and the compiler itself
  together) — update it if the module layout changes again.
- `juno/src/test/resources/` – WRITE here: minimal Arduino header stubs (`Arduino.h`,
  `Arduino_LED_Matrix.h`) used only to syntax-check generated sketches offline.
- `juno-maven-plugin/src/main/java/io/github/jabrena/juno/maven/` – WRITE here: Maven goals and
  the testable `ArduinoCli` process adapter. `juno:compile` invokes `JunoCompiler` directly;
  `juno:verify` also runs `arduino-cli compile`; `juno:upload` additionally discovers or validates
  the board port and uploads; `juno:monitor` attaches an interactive serial monitor. ASM is the
  default backend; `-Djuno.backend=cpp` selects Arduino C++. Keep Maven's normal `deploy` lifecycle
  untouched, and never shell out to the Juno executable jar from a Mojo.
- `juno-maven-plugin/src/test/java/` – WRITE here: isolated Arduino CLI command construction,
  board-list parsing, port-selection, and failure tests. Use a fake process executor; unit tests
  must never upload to hardware or open a real monitor.
- `juno-examples/src/main/java/` – WRITE here: example Java programs (`Blink.java`,
  `LedMatrixHeart.java`, `LedMatrixSnake.java`, `SerialCounter.java`, …) demonstrating the
  supported API; they are a normal Maven module (depends only on the `juno` artifact, for both the
  hardware API and `@Board`/`ArduinoUnoR4WiFi`) so `mvn compile` checks they still build. Keep them
  buildable end-to-end via `arduino-cli` too.
- `docs/` – WRITE here: supporting documentation (`ARDUINO.md` for the `arduino-cli` workflow,
  `TYPES.md` for the Java/Arduino type mapping).
- `docs/javadocs/<version>/` – **generated, currently tracked**: Javadoc HTML for the `juno`
  module (`./mvnw javadoc:aggregate`, run from the repo root). Not gitignored by request —
  regenerate rather than hand-edit, and expect it to be committed for release versions.
- `documentation/` – WRITE here: images and video assets (board photos, demo clips).
- `build/` – **READ only / generated**: sketches generated through the standalone CLI
  (`build/juno/<Main>/<Main>.ino`). Gitignored; never hand-edit.
- `target/`, `juno/target/`, `juno-maven-plugin/target/`, `juno-examples/target/` – **READ only /
  generated**: Maven build output. The plugin writes the default ASM sketch beneath
  `target/juno/<Main>Asm/` (`.ino` wrapper, `.S`, and `Shim.cpp`) and C++ output beneath
  `target/juno/<Main>/`. Gitignored; never hand-edit.
- `pom.xml` (root and all three modules), `README.md`, `docs/ARDUINO.md` – WRITE here: build
  configuration and documentation.

## Commands

```bash
# Run the full test suite (unit tests + generated-C++ syntax check), all modules
./mvnw test

# Build all modules
./mvnw clean package

# Full verify, matching CI (.github/workflows/maven.yaml)
./mvnw --batch-mode --no-transfer-progress verify

# Generate the juno module's Javadoc HTML into docs/javadocs/<version>/apidocs
./mvnw javadoc:aggregate

# Install reactor artifacts so the example module can resolve the development plugin
./mvnw install

# Generate Blink with the default ASM backend. juno-examples/pom.xml supplies its main class.
./mvnw -f juno-examples/pom.xml compile juno:compile

# Generate ASM Blink and compile it with the real Arduino toolchain (safe: does not touch hardware)
./mvnw -f juno-examples/pom.xml compile juno:verify

# Select the C++ backend explicitly
./mvnw -f juno-examples/pom.xml compile juno:verify -Djuno.backend=cpp

# Select another example by fully qualified class name
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.io.usb.SerialCounter

# Flash the generated program; auto-detects one matching board, or accepts -Djuno.port=<PORT>
./mvnw -f juno-examples/pom.xml compile juno:upload

# Attach the serial monitor at 9600 baud (override with -Djuno.baudRate=<RATE>)
./mvnw -f juno-examples/pom.xml juno:monitor
```

See [docs/ARDUINO.md](docs/ARDUINO.md) for the full `arduino-cli` install/build/upload/monitor
walkthrough.

## Git workflow

- Commit messages follow Conventional Commits (`feat(scope): summary`, `fix: …`, `docs: …`, …),
  matching the existing history (e.g. `feat(poc): Adding initial working example`).
- Keep the subject line short and imperative; use the body to explain *why*, not *what* (the diff
  already shows what changed).
- One logical change per commit; avoid bundling unrelated compiler, example, and doc edits.
- `main` is the primary branch; open PRs against it. Never force-push or rewrite shared history
  without explicit confirmation.

## Boundaries

- ✅ **Always do:** run `./mvnw test` before proposing a change; when touching `api/`, `linker/`,
  or `backend/`, add/extend a `JunoCompilerTest`/`GeneratedCppSyntaxTest` case and validate the
  affected example compiles end-to-end with `juno:verify` (`javac` → Juno →
  `arduino-cli compile --fqbn arduino:renesas_uno:unor4wifi`); keep generated `.ino` output
  deterministic and free of unused includes/helpers for programs that don't reach them (see how
  `LedMatrix` codegen is gated on actual usage). When changing Maven-plugin backend selection or
  sketch layout, run `juno:verify` once with the default ASM backend and once with
  `-Djuno.backend=cpp`.
- ⚠️ **Ask first:** adding new Maven dependencies or plugins; expanding the accepted bytecode
  subset (`BytecodeDecoder`) or relaxing `Descriptor.usesOnlyV01Types`; bumping the required
  JDK/Maven version; uploading a sketch to a connected physical board; force-pushing or amending
  published commits.
- 🚫 **Never do:** hand-edit files under `build/` or `target/` (regenerate them instead); commit
  secrets, board serial identifiers as credentials, or other sensitive data; silently swallow an
  unsupported-opcode/intrinsic error instead of surfacing a `CompileException`; skip tests to get
  a change merged faster.
