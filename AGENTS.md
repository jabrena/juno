# Agent Quickstart Guide

## Your role

You are a senior Java engineer specializing in compiler and toolchain development for embedded systems, including ARM Thumb-2 assembly generation, for boards like Arduino One R4 Wifi and Arduino One Q.

## Tech stack

- **Language:** Java, `maven.compiler.release=27`. Build/dev JDK is pinned
  to 27 via `.sdkmanrc` and CI (`.github/workflows/maven.yaml`).
- **Build:** Maven via the `./mvnw` wrapper (`.mvn/wrapper/maven-wrapper.properties`).
- **Test framework:** JUnit (Jupiter). `juno-compiler` also uses Testcontainers (test scope) for the opt-in `arduino-cli` and QEMU tests.
- **Architecture rules:** ArchUnit (test scope). `ApiArchitectureTest` keeps `juno-api` free of compiler and
  third-party code; `ExamplesArchitectureTest` keeps `juno-examples` on `juno-api` and the JDK only.
- **External toolchain:** Arduino CLI with the `arduino:renesas_uno` (UNO R4 WiFi) and `arduino:zephyr`
  (UNO Q) cores, used to actually compile/upload generated sketches to real hardware; not a Maven dependency.

## File structure

- `juno-api/` – the programming model programs compile against (`src/main/java/io/github/jabrena/juno/`: `api`,
  `annotations`); it has no dependency on the compiler.
- `juno-compiler/` – the compiler (`src/main/java/io/github/jabrena/juno/`: `classfile`, `bytecode`, `linker`,
  `backend`, …) plus its unit tests; depends on `juno-api`. A new `native` API method is declared in `juno-api`
  and needs entries in `IntrinsicRegistry` and `IntrinsicLowering` here, and the backend never branches on a
  specific board.
- `juno-maven-plugin/` – Maven goals (`juno:compile`, `verify`, `upload`, `monitor`) and their tests; tests use a
  fake process executor and never touch hardware.
- `juno-examples/` – example programs buildable with `arduino-cli`, plus game, `arduino-cli` and QEMU tests.
- `juno-site/` – documentation site: edit prose in `src/main/resources/content/`, never hand-edit `docs/`.
- `documentation/` – images and video assets.

## Commands

```bash
# Run the full test suite (unit tests + offline ASM/shim toolchain verification), all modules
./mvnw test

# Build all modules
./mvnw clean package

# Full verify, matching CI (.github/workflows/maven.yaml)
./mvnw --batch-mode --no-transfer-progress verify

# Generate the juno-api and juno-compiler modules' Javadoc HTML into docs/javadocs/<version>/apidocs
./mvnw javadoc:aggregate

# Install reactor artifacts so the example module can resolve the development plugin
./mvnw install

# Generate Blink. juno-examples/pom.xml supplies its main class.
./mvnw -f juno-examples/pom.xml compile juno:compile

# Generate Blink and compile it with the real Arduino toolchain (safe: does not touch hardware)
./mvnw -f juno-examples/pom.xml compile juno:verify

# Select another example by fully qualified class name
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.io.serial.SerialCounter

# Compile the small API/shield fixtures (juno-compiler/src/test/arduino/programs) for each board with the real
# arduino-cli inside Docker (Testcontainers; needs Docker)
./mvnw -pl juno-compiler -am -Parduino-cli verify

# Run core-feature programs under QEMU (Cortex-M4) in Docker and compare their
# serial output with an OpenJDK run of the same source (Testcontainers; needs Docker; models no hardware)
./mvnw -pl juno-compiler -am -Pqemu verify

# Flash the generated program; auto-detects one matching board, or accepts -Djuno.port=<PORT>
./mvnw -f juno-examples/pom.xml compile juno:upload

# Attach the serial monitor at 9600 baud (override with -Djuno.baudRate=<RATE>)
./mvnw -f juno-examples/pom.xml juno:monitor

# Live-preview the documentation site while editing juno-site/src/main/resources/content/*.md
./mvnw -f juno-site/pom.xml quarkus:dev

# Regenerate docs/ completely: wipes it, rebuilds the site, then the Javadoc on top — in order,
# in one command (see README's Documentation site section)
./mvnw clean verify -Psite

# Preview the site locally at the web root (docs/ itself has /juno baked in for GitHub Pages
# and 404s served plain — this overrides the prefix back to / and generates into
# juno-site/target/ instead, never docs/; see README's Previewing the site locally section)
./mvnw -f juno-site/pom.xml clean package quarkus:run -Dquarkus.http.root-path=/
jwebserver -d "$(pwd)/juno-site/target/roq" -p 8000   # then open http://127.0.0.1:8000/
```

See the [Arduino CLI Workflow](https://jabrena.github.io/juno/arduino) for the full
`arduino-cli` install/build/upload/monitor walkthrough.

## Git workflow

- Commit messages follow Conventional Commits (`type(scope): summary`), matching the existing history
  (e.g. `feat(poc): Adding initial working example`). Types:
  - `feat` – a new feature (compiler capability, API, example).
  - `fix` – a bug fix.
  - `docs` – documentation only.
  - `style` – formatting, whitespace, no code change.
  - `refactor` – code change that neither fixes a bug nor adds a feature.
  - `perf` – a performance improvement.
  - `test` – adding or correcting tests.
  - `build` – build system or dependency changes (Maven, `pom.xml`).
  - `ci` – CI configuration (`.github/workflows`).
  - `chore` – maintenance that touches no source or tests.
  - `revert` – reverts a previous commit.
  Mark a breaking change with `!` after the type/scope (`feat(api)!: …`) or a `BREAKING CHANGE:` footer.
- Keep the subject line short and imperative; use the body to explain *why*, not *what* (the diff
  already shows what changed).
- One logical change per commit; avoid bundling unrelated compiler, example, and doc edits.
- `main` is the primary branch; open PRs against it. Never force-push or rewrite shared history
  without explicit confirmation.

## Boundaries

- ✅ **Always do:** run `./mvnw test` before proposing a change; when touching `api/`, `linker/`,
  or `backend/`, add/extend a `JunoCompilerTest`/`GeneratedAsmToolchainTest` case and validate the
  affected example compiles end-to-end with `juno:verify` (`javac` → Juno →
  `arduino-cli compile --fqbn arduino:renesas_uno:unor4wifi`); keep generated assembly/shim output
  deterministic, and keep shim includes/helpers gated on actual usage where the backend already
  does so (`Mouse.h`/`WiFiS3.h`/`WiFiSSLClient.h`, HTTP/JSON/StringBuilder/runtime-string helpers —
  note `Arduino_LED_Matrix.h` and the Serial helpers are emitted unconditionally, not gated).
- ⚠️ **Ask first:** adding new Maven dependencies or plugins; expanding the accepted bytecode
  subset (`BytecodeDecoder`) or relaxing `Descriptor.usesOnlyV01Types`; bumping the required
  JDK/Maven version; uploading a sketch to a connected physical board; force-pushing or amending
  published commits.
- 🚫 **Never do:** hand-edit files under `build/`, `target/`, or `docs/` (regenerate them
  instead — edit `juno-site/src/main/resources/content/` for documentation prose); commit
  secrets, board serial identifiers as credentials, or other sensitive data; silently swallow an
  unsupported-opcode/intrinsic error instead of surfacing a `CompileException`; skip tests to get
  a change merged faster.
