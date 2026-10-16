# Developer commands

## Essential maven commands

```bash
# Analyze dependencies
./mvnw dependency:tree
./mvnw dependency:analyze
./mvnw dependency:resolve

./mvnw clean validate -U
./mvnw buildplan:list-plugin
./mvnw buildplan:list-phase
./mvnw help:all-profiles
./mvnw help:active-profiles
./mvnw license:third-party-report

# Clean the project
./mvnw clean

# Run unit tests
./mvnw clean test

# Run integration tests
./mvnw clean verify

# Clean and package in one command
./mvnw clean package

# Check for dependency updates
./mvnw versions:display-property-updates
./mvnw versions:display-dependency-updates
./mvnw versions:display-plugin-updates

# Generate project reports
./mvnw site
jwebserver -p 8005 -d "$(pwd)/target/site/"
```

## Submodules

This is a multi-module project. The following modules are declared in the root `pom.xml`.

| Module | Artifact ID | Packaging | Commands |
|--------|-------------|-----------|----------|
| `juno-api` | `juno-api` | `jar` | `./mvnw clean verify -pl juno-api`<br>`./mvnw clean install -pl juno-api` |
| `juno-compiler` | `juno-compiler` | `jar` | `./mvnw clean verify -pl juno-compiler -am`<br>`./mvnw clean install -pl juno-compiler -am`<br>`./mvnw clean verify -pl juno-compiler -am -P arduino-cli`<br>`./mvnw clean verify -pl juno-compiler -am -P qemu` |
| `juno-maven-plugin` | `juno-maven-plugin` | `maven-plugin` | `./mvnw clean verify -pl juno-maven-plugin`<br>`./mvnw clean install -pl juno-maven-plugin` |
| `juno-examples` | `juno-examples` | `jar` | `./mvnw clean verify -pl juno-examples`<br>`./mvnw clean verify -pl juno-examples -am -P arduino-cli` |
| `juno-site` | `juno-site` | `quarkus` | `./mvnw clean verify -pl juno-site`<br>`./mvnw clean verify -pl juno-site -P site` |

## Maven Profiles

The following profiles are declared in this project. Activate them with `-P <profileId>`.

| Profile ID | Command | Activation |
|------------|---------|------------|
| `cyclomatic-complexity` (root `pom.xml`) | `./mvnw clean verify -P cyclomatic-complexity` | default (activeByDefault) |
| `jacoco` (root and `juno-compiler/pom.xml`) | `./mvnw clean verify -P jacoco` | manual |
| `site` (root `pom.xml`, `juno-site/pom.xml`) | `./mvnw clean verify -P site` | manual |
| `arduino-cli` (`juno-compiler/pom.xml`, `juno-examples/pom.xml`) | `./mvnw -pl juno-compiler -am -Parduino-cli verify` | manual (needs Docker) |
| `qemu` (`juno-compiler/pom.xml`) | `./mvnw -pl juno-compiler -am -Pqemu verify` | manual (needs Docker) |

The Docker profiles run end-to-end tests with Testcontainers:

- `arduino-cli` in `juno-compiler` compiles the small API and shield fixtures in `juno-compiler/src/test/arduino/programs`
  for each board they declare (UNO R4 WiFi and UNO Q) with the real `arduino-cli`.
- `arduino-cli` in `juno-examples` compiles the Java, Kotlin and Scala language examples in
  `juno-examples/src/main/{java,kotlin,scala}/.../language` for each board their `@Board` annotation declares (UNO R4 WiFi
  when none) with the real `arduino-cli`. To run only these, pass
  `-Dit.test=LanguageExamplesArduinoCliIT -Dfailsafe.failIfNoSpecifiedTests=false`, which skips the fixture tests.
- `qemu` runs the language fixtures in `juno-compiler/src/test/qemu/programs` on QEMU (Cortex-M4) and compares their serial
  output with a JVM run of the same source.

The `arduino-cli` profile is declared in both modules, so `-P arduino-cli` from the root or with `-am` runs both suites.
The `juno-examples` suite is the one that builds the example programs; the in-process `LanguageExamplesCompilationTest`
runs by default with `./mvnw test` and needs no Docker. It compiles every Java, Kotlin and Scala entry point through
Juno, once per board, and `ExamplesArchitectureTest` keeps all three languages on the Juno API and the JDK.

The Scala examples (`juno-examples/src/main/scala/.../language/scala`) are compiled to Java 27 bytecode
(`-java-output-version:27`), the same release as the Java examples. The pinned Scala 3.10.0 is needed: Scala 3.8.1 rejects
27 as an output version. Each example is a `final class` with a companion `object` whose `main` is `@static`, which gives the
entry point the static method Juno looks for. Scala code that calls into the Scala runtime (`Array(...)`, `for`, `do`,
`s"..."`, case classes, `StringOps`) does not compile for the board, so the examples use `while` loops and `java.*` types.

To generate one Scala example for a board, pass its class name:

```bash
./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.language.scala.HelloWorld -Djuno.board=arduino-uno-r4-wifi
```

## Plugin Goals Reference

The following sections list useful goals for each plugin configured in this project's pom.xml.

### maven-compiler-plugin

| Goal | Purpose |
|------|---------|
| `./mvnw compiler:compile` | Compile main source files |
| `./mvnw compiler:testCompile` | Compile test source files |

### maven-surefire-plugin

| Goal | Purpose |
|------|---------|
| `./mvnw surefire:test` | Run unit tests |
| `./mvnw surefire:help` | Display help information |

### maven-failsafe-plugin

| Goal | Purpose |
|------|---------|
| `./mvnw failsafe:integration-test` | Run integration tests |
| `./mvnw failsafe:verify` | Verify integration test results |

### maven-jar-plugin

| Goal | Purpose |
|------|---------|
| `./mvnw jar:jar` | Build the JAR for the project |
| `./mvnw jar:test-jar` | Build a JAR of the test classes |

### maven-javadoc-plugin

| Goal | Purpose |
|------|---------|
| `./mvnw javadoc:javadoc` | Generate Javadoc HTML documentation |
| `./mvnw javadoc:aggregate` | Generate aggregated Javadoc for all modules |
| `./mvnw javadoc:test-javadoc` | Generate Javadoc for test sources |
| `./mvnw javadoc:jar` | Bundle Javadoc into a JAR |

### maven-plugin-plugin

| Goal | Purpose |
|------|---------|
| `./mvnw plugin:descriptor` | Generate the Maven plugin descriptor |
| `./mvnw plugin:help` | Display help information |
| `./mvnw plugin:report` | Generate the plugin documentation report |

### maven-pmd-plugin

| Goal | Purpose |
|------|---------|
| `./mvnw pmd:check` | Run PMD analysis and fail on violations |
| `./mvnw pmd:pmd` | Generate a PMD report |
| `./mvnw pmd:cpd-check` | Run copy-paste detection and fail on duplicates |

### maven-jxr-plugin

| Goal | Purpose |
|------|---------|
| `./mvnw jxr:jxr` | Generate cross-referenced HTML source for the project |
| `./mvnw jxr:test-jxr` | Generate cross-referenced HTML source for test code |

### maven-clean-plugin

| Goal | Purpose |
|------|---------|
| `./mvnw clean:clean` | Delete the build output directory |

### maven-resources-plugin

| Goal | Purpose |
|------|---------|
| `./mvnw resources:resources` | Copy main resources to the output directory |
| `./mvnw resources:testResources` | Copy test resources to the output directory |

### quarkus-maven-plugin

| Goal | Purpose |
|------|---------|
| `./mvnw quarkus:dev` | Run in development mode with live reload |
| `./mvnw quarkus:build` | Build the application |
| `./mvnw quarkus:run` | Run the built application |

### juno-maven-plugin

| Goal | Purpose |
|------|---------|
| `./mvnw -f juno-examples/pom.xml compile juno:compile -Djuno.board=arduino-uno-r4-wifi` | Generate the Arduino sketch (`.S`, shim, `.ino`) with Juno |
| `./mvnw -f juno-examples/pom.xml compile juno:verify -Djuno.board=arduino-uno-r4-wifi` | Generate the sketch and compile it with `arduino-cli` |
| `./mvnw -f juno-examples/pom.xml compile juno:upload -Djuno.board=arduino-uno-r4-wifi` | Generate, verify and upload to the board |
| `./mvnw -f juno-examples/pom.xml juno:boards` | List connected boards and serial ports (read-only) |
| `./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-uno-r4-wifi` | Attach the serial monitor (`-Djuno.board=<id>`, `-Djuno.port=<port>`) |
