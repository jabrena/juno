# Agent Quickstart Guide

## Your role

You are a senior Java engineer specializing in compiler and toolchain development for embedded systems.

- Juno is an ahead-of-time compiler that lowers a small, checked subset of JVM bytecode to Arduino
  C++ for the UNO R4 WiFi/Minima (Renesas RA4M1, Cortex-M4). Treat correctness of the
  classfile → linker → backend pipeline, and byte-for-byte reproducibility of generated `.ino`
  sketches, as the top priorities.
- The project is deliberately closed-world and minimal: no JVM interpreter, no dynamic class
  loading, no general objects/arrays yet (see `docs/roadmap.md`). Prefer extending the existing
  intrinsic-lowering pattern over adding new language machinery.
- When a change touches what Java source can express, verify it compiles through the full chain:
  `javac` → `JunoCompiler` → generated `.ino` → real `arduino-cli` compile against
  `arduino:renesas_uno:unor4wifi` (and ideally `:minima`) — not just `mvn test`.

## Tech stack

- **Language:** Java, `maven.compiler.release=17` (bytecode subset Juno accepts is far narrower —
  see `docs/FEATURES.md`). Build/dev JDK is pinned to 25 (GraalVM CE) via
  `.sdkmanrc` and CI (`.github/workflows/maven.yaml`).
- **Build:** Maven 3.9.14 via the `./mvnw` wrapper (`.mvn/wrapper/maven-wrapper.properties`).
- **Test framework:** JUnit 5 (Jupiter) 5.11.4.
- **No runtime frameworks** — this is a standalone CLI (`io.github.jabrena.juno.Main`), packaged
  as an executable jar via `maven-jar-plugin`.
- **External toolchain:** Arduino CLI with the `arduino:renesas_uno` core, used to actually
  compile/upload generated sketches to real UNO R4 hardware; not a Maven dependency.

## File structure

This is a multi-module Maven build: the `juno` module is the compiler, the `juno-examples` module
is example programs written against it.

- `juno/src/main/java/io/github/jabrena/juno/classfile/` – WRITE here: `.class` parsing and
  constant pool resolution.
- `juno/src/main/java/io/github/jabrena/juno/bytecode/` – WRITE here: the admitted opcode
  subset (`BytecodeDecoder`). Extending this expands what Java syntax compiles.
- `juno/src/main/java/io/github/jabrena/juno/linker/` – WRITE here: closed-world reachability,
  `Intrinsics` registry, `Descriptor` type checks.
- `juno/src/main/java/io/github/jabrena/juno/backend/` – WRITE here: `ArduinoCppBackend`, the
  C++ emitter and intrinsic lowering (`intrinsicExpression`), plus `CppNames`.
- `juno/src/main/java/io/github/jabrena/juno/api/` – WRITE here: the Java-facing hardware API
  (`Gpio`, `Delay`, `Clock`, `DigitalOutput`, `LedMatrix`, `Serial`, `Mouse`, …). Every `native` method here
  must have a matching entry in `linker/Intrinsics.java` and
  `backend/ArduinoCppBackend#intrinsicExpression`.
- `juno/src/test/java/io/github/jabrena/juno/` – WRITE here: compiler unit tests and the
  generated-C++ syntax check (`GeneratedCppSyntaxTest`, requires `clang++`/`g++` locally; skips
  otherwise).
- `juno/src/test/resources/` – WRITE here: minimal Arduino header stubs (`Arduino.h`,
  `Arduino_LED_Matrix.h`) used only to syntax-check generated sketches offline.
- `juno-examples/src/main/java/` – WRITE here: example Java programs (`Blink.java`,
  `LedMatrixHeart.java`, `LedMatrixSnake.java`, `SerialCounter.java`, …) demonstrating the
  supported API; they are a normal Maven module (depends on the `juno` module's `juno` artifact)
  so `mvn compile` checks they still build. Keep them buildable end-to-end via `arduino-cli` too.
- `docs/` – WRITE here: supporting documentation (`ARDUINO.md` for the `arduino-cli` workflow,
  `TYPES.md` for the Java/Arduino type mapping).
- `docs/javadocs/<version>/` – **generated, currently tracked**: Javadoc HTML for the `juno`
  module (`./mvnw -pl juno javadoc:javadoc`). Not gitignored by request — regenerate rather
  than hand-edit, and expect it to be committed for release versions.
- `documentation/` – WRITE here: images and video assets (board photos, demo clips).
- `build/` – **READ only / generated**: Juno-generated `.ino` sketches (`build/juno/<Main>/<Main>.ino`).
  Gitignored; never hand-edit.
- `target/`, `juno/target/`, `juno-examples/target/` – **READ only / generated**: Maven build output.
  Gitignored.
- `pom.xml` (root, `juno/`, `juno-examples/`), `README.md`, `docs/ARDUINO.md` – WRITE here: build
  configuration and documentation.

## Commands

```bash
# Run the full test suite (unit tests + generated-C++ syntax check), all modules
./mvnw test

# Build both modules (skip tests for a fast iteration loop)
./mvnw clean package -DskipTests

# Full verify, matching CI (.github/workflows/maven.yaml)
./mvnw --batch-mode --no-transfer-progress verify

# Generate the juno module's Javadoc HTML into docs/javadocs/<version>/apidocs
./mvnw -pl juno javadoc:javadoc

# Run Juno: link + emit the Arduino sketch for an example already built by `mvn package`
java -jar juno/target/juno-0.1.0-SNAPSHOT.jar compile \
  --main <Name> --classpath juno-examples/target/classes:juno/target/classes

# Compile the generated sketch against the real Arduino toolchain (must be installed separately)
arduino-cli compile --fqbn arduino:renesas_uno:unor4wifi build/juno/<Name>

# List connected boards and their serial port
arduino-cli board list

# Flash the sketch to a connected board (overwrites its current firmware)
arduino-cli upload --port <PORT> --fqbn arduino:renesas_uno:unor4wifi build/juno/<Name>
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
  affected example compiles end-to-end (`javac` → Juno → `arduino-cli compile`) for both
  `unor4wifi` and `minima` where the change is board-relevant; keep generated `.ino` output
  deterministic and free of unused includes/helpers for programs that don't reach them (see how
  `LedMatrix` codegen is gated on actual usage).
- ⚠️ **Ask first:** adding new Maven dependencies or plugins; expanding the accepted bytecode
  subset (`BytecodeDecoder`) or relaxing `Descriptor.usesOnlyV01Types`; bumping the required
  JDK/Maven version; uploading a sketch to a connected physical board; force-pushing or amending
  published commits.
- 🚫 **Never do:** hand-edit files under `build/` or `target/` (regenerate them instead); commit
  secrets, board serial identifiers as credentials, or other sensitive data; silently swallow an
  unsupported-opcode/intrinsic error instead of surfacing a `CompileException`; skip tests to get
  a change merged faster.
