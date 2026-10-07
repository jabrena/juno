# StringConcatFactory support handoff

## Goal and current state

The active change adds bounded support for javac's
`StringConcatFactory.makeConcatWithConstants` `invokedynamic` protocol so ordinary Java such as:

```java
Serial.println("active: " + active);
```

can compile through Juno. The main compiler implementation is present and the normal compiler,
offline toolchain, Maven reactor, PMD, and real UNO R4 Arduino compilation checks have passed.

One runtime edge case is still being driven through QEMU: concatenating eight floating-point
operands can wrap Juno's eight-slot runtime-string pool because the current concat float/double
helpers call `juno_string_value_of_double` internally. The QEMU oracle now contains a regression
case for this. Replace that nested slot allocation with direct append formatting before considering
the feature complete.

## Implemented compiler flow

- `StringConcatResolver` recognizes only the exact
  `StringConcatFactory.makeConcatWithConstants` bootstrap, validates the descriptor, and decodes
  `\u0001` dynamic arguments plus `\u0002` bootstrap constants.
- `InvokeDynamicSupport` dispatches reachable `invokedynamic` sites between string concatenation and
  the existing `LambdaMetafactory` path. This was extracted from `Linker` after PMD reported that
  the extra branch pushed `Linker` above its cyclomatic-complexity limit.
- `StringConcatLowering` pops typed JVM operands and emits compiler-internal concat intrinsics.
- The backend lowers those intrinsics to bounded runtime helpers in the generated C++ shim.
- Supported operands are runtime `String` references and all primitive types:
  `boolean`, `byte`, `char`, `short`, `int`, `long`, `float`, and `double`.
- A null runtime string becomes `"null"`.
- The result uses the existing eight rotating 32-byte string slots. The maximum payload is 31 UTF-8
  bytes; appending beyond it calls `juno_panic()` rather than truncating.
- Arbitrary-object conversion and other bootstrap protocols remain unsupported.

Key new files:

- `juno/src/main/java/io/github/jabrena/juno/linker/InvokeDynamicSupport.java`
- `juno/src/main/java/io/github/jabrena/juno/linker/StringConcatResolver.java`
- `juno/src/main/java/io/github/jabrena/juno/linker/StringConcatSite.java`
- `juno/src/main/java/io/github/jabrena/juno/lowering/StringConcatLowering.java`

## Tests and examples changed

- `JunoCompilerTest` now covers:
  - string, boolean, and integer concatenation;
  - char, long, float, double, and nullable string concatenation;
  - `\u0002` recipe constants;
  - continued rejection of an unsupported `ObjectMethods` bootstrap.
- `GeneratedAsmToolchainTest` assembles a concat program and syntax-checks its generated C++ shim.
- `juno-examples/src/test/qemu/programs/StringConcat.java` compares Juno output byte-for-byte with
  the JVM. It includes the pending eight-double slot-wrap regression.
- `Variables` now demonstrates `Serial.println("active: " + active)`.
- `UnsupportedFeature` was repurposed as a record whose generated `toString()` uses the unsupported
  `ObjectMethods` bootstrap.
- README and the source documentation under `juno-site/src/main/resources/content/` describe the new
  supported subset and its 31-byte result limit. Do not edit generated `docs/` directly.

## QEMU delay review and changes

The Docker delay was image provisioning, not QEMU execution. Two causes were found:

1. `ImageFromDockerfile("juno-qemu", true)` deleted the named image after every Maven JVM.
2. The image installed `libstdc++-arm-none-eabi-newlib`, although the harness and generated shim do
   not use the C++ standard library. This package accounts for a large part of the image/download.

Changes already made:

- The persistent image is now named `juno-qemu:bookworm-v1` with `deleteOnExit=false`.
- `libstdc++-arm-none-eabi-newlib` was removed from the Dockerfile.
- C++ sources are still compiled with `arm-none-eabi-g++`, but final linking uses
  `arm-none-eabi-gcc`, avoiding the implicit `-lstdc++` dependency.

The first optimized run still downloads QEMU/GCC/newlib once. A second invocation must be timed to
confirm Docker reuses the retained image/layers without another long `apt-get` installation.

## Verification completed

Passed:

```bash
./mvnw -pl juno \
  -Dtest=JunoCompilerTest#lowersStringConcatFactoryForStringsBooleansAndIntegers test

./mvnw -pl juno \
  -Dtest=JunoCompilerTest#lowersStringConcatFactoryForWideAndFloatingPointValues test

./mvnw -pl juno \
  -Dtest=GeneratedAsmToolchainTest#assemblesAStringConcatProgramAndCompilesItsShim test

./mvnw -pl juno test
./mvnw test
./mvnw install -DskipTests

./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.Variables
```

The real Arduino check targeted `arduino:renesas_uno:unor4wifi` and passed with no runtime-risk
findings. `UnsupportedFeature` was also manually checked and fails as intended at its generated
record `toString()` bootstrap.

The first `./mvnw install -DskipTests` attempt failed PMD only because `Linker` reached total
cyclomatic complexity 70. Extracting `InvokeDynamicSupport` fixed it; the subsequent install passed.

## Work still required

1. Let the current optimized QEMU profile finish, or rerun:

   ```bash
   ./mvnw -f juno-examples/pom.xml -Pqemu verify
   ```

2. Confirm the eight-double oracle fails with the current nested-slot implementation.
3. Change `juno_string_concat_append_float`/`_double` in `ShimLibraries.runtimeStringHelpers()` to
   format directly into the active concat buffer. Do not call `juno_string_value_of_double`, because
   that advances the rotating slot cursor. Preserve the existing bounded decimal formatting policy.
4. Rerun QEMU and confirm JVM-equivalent output, then immediately rerun the profile once more to
   demonstrate that the retained image avoids the provisioning delay.
5. Run the final required gates after the runtime fix:

   ```bash
   ./mvnw test
   ./mvnw clean verify
   ./mvnw install -DskipTests
   ./mvnw -f juno-examples/pom.xml compile juno:verify \
     -Djuno.main=io.github.jabrena.juno.Variables
   ./mvnw -f juno-examples/pom.xml -Pqemu verify
   git diff --check
   ```

6. Inspect `git status` carefully. The `Variables` file already contained user edits before this
   feature (`public class` and removal of its private constructor); preserve them.

No files are staged or committed for this change yet.