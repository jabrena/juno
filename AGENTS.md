# Agent Quickstart Guide

## Your role

You are a senior Java engineer specializing in compiler and toolchain development for embedded systems.

- Juno is an ahead-of-time compiler that lowers a small, checked subset of JVM bytecode to GNU
  Cortex-M4 assembly for the UNO R4 WiFi (Renesas RA4M1). Treat correctness of the
  classfile → linker → backend pipeline, and byte-for-byte reproducibility of generated sketches,
  as the top priorities.
- The project is deliberately closed-world and minimal: no JVM interpreter, no dynamic class
  loading, no general objects/arrays yet (see `docs/roadmap.md`). Prefer extending the existing
  intrinsic-lowering pattern over adding new language machinery.
- When a change touches what Java source can express, verify it compiles through the full chain:
  `javac` → `JunoCompiler` → generated `.S`/`Shim.cpp`/`.ino` → real `arduino-cli` compile against
  `arduino:renesas_uno:unor4wifi` — not just `mvn test`.

## Tech stack

- **Language:** Java, `maven.compiler.release=25` (bytecode subset Juno accepts is far narrower —
  see the [Feature Inventory](https://jabrena.github.io/juno/features)). Build/dev JDK is pinned
  to 25 (GraalVM CE) via `.sdkmanrc` and CI (`.github/workflows/maven.yaml`).
- **Build:** Maven 3.9.16 via the `./mvnw` wrapper (`.mvn/wrapper/maven-wrapper.properties`).
- **Test framework:** JUnit (Jupiter) 6.1.3. `juno-examples` also uses Testcontainers 2.0.5 (test scope) for the
  opt-in `arduino-cli` compile test.
- **No runtime frameworks in the compiler/plugin/examples modules** — `juno` is a standalone CLI
  (`io.github.jabrena.juno.Main`), packaged as an executable jar via `maven-jar-plugin`;
  `juno-maven-plugin` is a conventional Maven plugin. The one deliberate exception is `juno-site`,
  a Quarkus/[Roq](https://iamroq.dev) static site generator used only to build the documentation
  site at build time — it has no runtime footprint on the board or in the other modules.
- **External toolchain:** Arduino CLI with the `arduino:renesas_uno` core, used to actually
  compile/upload generated sketches to real UNO R4 hardware; not a Maven dependency.

## File structure

This is a four-module Maven build: `juno` is the compiler, bundling the Java-facing hardware API
and `@Board` annotation types it recognizes as intrinsics; `juno-maven-plugin` integrates the
compiler and external Arduino CLI with Maven; `juno-examples` contains example programs written
against `juno`'s `api` and `annotations` packages; and `juno-site` is the Roq/Quarkus static site
generator that builds the documentation site published to `docs/`.

- `juno/src/main/java/io/github/jabrena/juno/api/` – WRITE here: the Java-facing hardware API
  (`Delay`, `Clock`, `LedMatrix`, `api.io.Gpio`, `api.io.DigitalOutput`, `api.io.hid.Mouse`,
  `api.io.usb.Serial`, …).
  Every `native` method here must have a matching entry in `juno`'s `intrinsic/IntrinsicRegistry.java`
  and `backend/IntrinsicLowering.java` (the intrinsic → shim-call table), keyed by this package's fully-qualified
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
- `juno/src/main/java/io/github/jabrena/juno/backend/` – WRITE here: `Thumb2AsmBackend`, the
  sole code-generation backend (GNU ARM Thumb-2 assembly, shared by every board, plus its `extern "C"` C++ runtime shim).
  It drives codegen and delegates to package-private collaborators: `IntrinsicLowering` (intrinsic
  lowering as a table of shim-call specs), `RuntimeShim` (the shim, gated on the `ShimFeature`s the
  lowerings record) with its `ShimLibraries`/`NetworkShimLibraries` helper sources, `CoreRuntime`
  (sealed; one implementation per `board/ArduinoCore` — the delay/yield glue that differs between the
  Renesas and Zephyr cores), `ProgramLayout`, `AsmEmitter`, and the `Int`/`Wide` arithmetic and
  `Array` lowerings. Never branch on a specific board inside the backend: per-core differences go in
  a `CoreRuntime`, optional peripherals are `board/Capability` values the `Linker` checks once.
- `juno/src/test/java/io/github/jabrena/juno/` – WRITE here: compiler unit tests and offline
  toolchain verification for the generated assembly/shim (`GeneratedAsmToolchainTest`; assembles
  `.S` files with a bundled `arm-none-eabi-gcc` when one can be found and syntax-checks the shim
  with `clang++`/`g++`, skipping otherwise). These fixtures import `juno`'s own `api`/`annotations`
  classes via `CompilerTestSupport`, which resolves the compiler's own classpath relative to
  `juno`'s working directory (`target/classes`, which now holds `api`, `annotations`, and the
  compiler itself together) — update it if the module layout changes again.
- `juno/src/test/resources/` – WRITE here: minimal Arduino header stubs (`Arduino.h`,
  `Arduino_LED_Matrix.h`, `Mouse.h`, `WiFiS3.h`, `WiFiSSLClient.h`) used only to syntax-check the
  generated runtime shim offline.
- `juno-maven-plugin/src/main/java/io/github/jabrena/juno/maven/` – WRITE here: Maven goals and
  the testable `ArduinoCli` process adapter. `juno:compile` invokes `JunoCompiler` directly;
  `juno:verify` also runs `arduino-cli compile`; `juno:upload` additionally discovers or validates
  the board port and uploads; `juno:monitor` attaches an interactive serial monitor. Keep Maven's
  normal `deploy` lifecycle untouched, and never shell out to the Juno executable jar from a Mojo.
- `juno-maven-plugin/src/test/java/` – WRITE here: isolated Arduino CLI command construction,
  board-list parsing, port-selection, and failure tests. Use a fake process executor; unit tests
  must never upload to hardware or open a real monitor.
- `juno-examples/src/main/java/` – WRITE here: example Java programs (`Blink.java`,
  `LedMatrixHeart.java`, `LedMatrixSnake.java`, `SerialCounter.java`, …) demonstrating the
  supported API; they are a normal Maven module (depends only on the `juno` artifact, for both the
  hardware API and `@Board`/`ArduinoUnoR4WiFi`) so `mvn compile` checks they still build. Keep them
  buildable end-to-end via `arduino-cli` too. TFT shield games live in `io/github/jabrena/juno/games`
  (single-class games directly, multi-class games in their own subpackage); `api/tft` keeps only the
  non-game `TftTouchPaint` demo.
- `juno-examples/src/test/java/` – WRITE here: tests that play the TFT games on an emulated shield.
  `io/github/jabrena/juno/api/{Clock,Delay,Random}` and `api/io/{Gpio,usb/Serial}` are test doubles
  that shadow the `juno` artifact's native classes on the test classpath (`Gpio` emulates the
  ILI9341 bus and touch panel); `api/tft/GameScreenshotTest` compares each game's screen with
  `docs/images/games/*.png` (regenerate with `-Djuno.updateScreenshots=true`), and the
  `games/*GamesTest` classes check game rules and computer players. `api/tft/ArduinoCliCompileIT`
  (tag `arduino-cli`, opt-in via `-Parduino-cli`) compiles every game with the real `arduino-cli`
  in a Testcontainers container built from `juno-examples/src/test/docker/arduino-cli/Dockerfile`.
  `api/tft/ArduinoCliCompileIT` only compiles. `qemu/QemuRunIT` (tag `qemu`, opt-in via `-Pqemu`) actually
  *runs* generated code: programs in `juno-examples/src/test/qemu/programs` plus `ExceptionUnwinding` are built
  against a bare-metal harness (`src/test/qemu`: stub `Arduino.h`, startup, `run.sh`, Dockerfile) and executed in
  QEMU `mps2-an386`; output must equal the JVM's, with `src/test/qemu/oracle/.../Serial` shadowing the native
  `Serial`. Known compiler bugs are listed in its `KNOWN_GAPS` instead of failing the suite.
  See the [Games guide](https://jabrena.github.io/juno/games)
  (`juno-site/src/main/resources/content/games.md`).
- `juno-site/src/main/resources/content/` – WRITE here: the documentation site's prose, one
  Markdown (or `.html` for the home page) file per page, with YAML frontmatter
  (`title`/`description`/`layout`). This is the source of truth for what gets published to
  `docs/` — never hand-edit `docs/` directly.
- `juno-site/src/main/resources/{data,web,templates,public}/` – WRITE here: `data/menu.yml` (the
  sidebar navigation) and `data/authors.yml`; `web/_custom.css` (the Juno teal/copper theme
  override on top of the `quarkus-roq-theme-default` Tailwind theme); `templates/partials/` (theme
  partial overrides, e.g. the grouped `sidebar-menu.html`); `public/images/` (site assets,
  including the game screenshots and CPU-played gifs — copy new binary assets in here, don't
  reference `docs/` from the site).
- `docs/` – **generated, regenerate rather than hand-edit**: the published documentation site
  (built by `juno-site`) plus `docs/javadocs/<version>/` (`./mvnw javadoc:aggregate`, run from the
  repo root). `docs/` is rebuilt from scratch on every regenerate — see `juno-site`'s Commands
  entry below — so nothing under it is hand-maintained, only committed for GitHub Pages to serve.
- `documentation/` – WRITE here: images and video assets (board photos, demo clips).
- `build/` – **READ only / generated**: sketches generated through the standalone CLI
  (`build/juno/<Main>/<Main>.S`, `<Main>Shim.cpp`, `<Main>.ino`). Gitignored; never hand-edit.
- `target/`, `juno/target/`, `juno-maven-plugin/target/`, `juno-examples/target/` – **READ only /
  generated**: Maven build output. The plugin writes the sketch beneath `target/juno/<Main>Asm/`
  (`.ino` wrapper, `.S`, and `Shim.cpp`). Gitignored; never hand-edit.
- `pom.xml` (root and all four modules), `README.md` – WRITE here: build configuration and
  top-level documentation.

## Commands

```bash
# Run the full test suite (unit tests + offline ASM/shim toolchain verification), all modules
./mvnw test

# Build all modules
./mvnw clean package

# Full verify, matching CI (.github/workflows/maven.yaml)
./mvnw --batch-mode --no-transfer-progress verify

# Generate the juno module's Javadoc HTML into docs/javadocs/<version>/apidocs
./mvnw javadoc:aggregate

# Install reactor artifacts so the example module can resolve the development plugin
./mvnw install

# Generate Blink. juno-examples/pom.xml supplies its main class.
./mvnw -f juno-examples/pom.xml compile juno:compile

# Generate Blink and compile it with the real Arduino toolchain (safe: does not touch hardware)
./mvnw -f juno-examples/pom.xml compile juno:verify

# Select another example by fully qualified class name
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.io.usb.SerialCounter

# Compile every TFT game with the real arduino-cli inside Docker (Testcontainers; needs Docker)
./mvnw -f juno-examples/pom.xml -Parduino-cli verify

# Run core-feature programs and the exception example under QEMU (Cortex-M4) in Docker and compare their
# serial output with a JVM run of the same source (Testcontainers; needs Docker; models no hardware)
./mvnw -f juno-examples/pom.xml -Pqemu verify

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

- Commit messages follow Conventional Commits (`feat(scope): summary`, `fix: …`, `docs: …`, …),
  matching the existing history (e.g. `feat(poc): Adding initial working example`).
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
