# Creating and consuming a Juno API

This is a developer-facing guide to Juno's Java-facing API classes (`Delay`, `Gpio`, `HttpClient`,
`Smtp`, ...): what they actually are, how to *consume* one from an example program, and the
checklist for *creating* a new one. It uses `HttpClient` as the running example throughout,
because it is small, already has both a plain-Java-adjacent sibling (`HttpsClient`) and a
non-trivial argument shape (mixed `String`/`byte[]`/`int[]` parameters), and is documented in full
end-user detail in [the Internet access guide](INTERNET.md).

## What an API class actually is

There is no JVM on the board, so a method like `HttpClient.get(...)` never runs as real Java.
Every operation Juno recognizes is declared as a `public static native` method:

```java
// juno/src/main/java/io/github/jabrena/juno/api/io/net/http/HttpClient.java
public static native int get(String host, int port, String path,
        byte[] responseBuffer, int responseBufferLength,
        byte[] headersBuffer, int headersBufferLength,
        int[] statusAndHeadersLength);
```

`native` here is not JNI — it is simply a method with no bytecode body, which `javac` accepts
without complaint. Juno's own compiler resolves the call at link time by matching the method's
exact `(owner, name, descriptor)` triple against a fixed table (`IntrinsicRegistry`); anything not
in that table is a compile error ("Juno does not support this yet"), never a runtime failure.

Consuming code (`juno-examples`) calls `HttpClient.get(...)` exactly like any other static method.
Compiling it with plain `javac` (as `juno-examples`'s own Maven build does, on every commit) works
fine and produces a real `.class` file — it is only *Juno's own* compiler that treats the call
specially.

## Consuming an API: `HttpClient` walkthrough

The general shape, common to every Juno API that touches hardware or network state:

1. Declare caller-owned buffers up front, outside any loop that runs more than once — see
   [below](#buffers-are-caller-owned-forever) for why.
2. Call the operation, passing buffer/length pairs.
3. Check the returned `int` against that method's documented result codes before trusting the
   buffer's contents.

```java
byte[] response = new byte[512];
int responseBytes = HttpClient.get("api.example.com", 80, "/status", response, response.length);
if (responseBytes < 0) {
    Serial.println("HTTP connection failed");
} else {
    // response[0..responseBytes) is the body.
}
```

Every `String` argument (`host`, `path`, request bodies, JSON paths, ...) must be a **compile-time
constant**: a string literal, a `private static final` compile-time-constant expression, or a
direct `System.getenv("LITERAL_NAME")` call. Juno resolves that last form itself, at compile time,
by calling the real `System.getenv` in its own JVM process (`BytecodeToIr.isCompileTimeGetenv`/
`lowerCompileTimeGetenv`) — the variable name must be a literal, and compilation fails with a clear
error if the variable is unset. None of this happens on the board; there is no environment there,
and the resolved value is baked into the generated firmware as an ordinary string constant.

### Supplying compile-time credentials with `System.getenv`

Every API that needs a host, path, username, or similar value not fit for committing to source
reads it the same way — a direct `System.getenv("LITERAL_NAME")` call, resolved by Juno itself at
compile time as described above:

```java
Wifi.begin(
        System.getenv("JUNO_WIFI_SSID"),
        System.getenv("JUNO_WIFI_PASSWORD"));
```

Set the variable in the environment that runs `juno compile`:

```bash
export JUNO_WIFI_SSID='your-network-name'
export JUNO_WIFI_PASSWORD='your-network-password'
```

Compilation fails with a clear error if the variable is unset, or if the argument isn't a literal
string at all.

#### Alternative: a `.env` file via `juno-maven-plugin`

For Maven-built examples, `juno-maven-plugin`'s `env` goal is a Maven-level alternative to
exporting shell environment variables by hand: it reads a git-ignored `.env` file (`KEY=VALUE` per
line) from the module's base directory and exports every entry into the Maven JVM's real process
environment, overwriting any value already set for that name, before `juno-maven-plugin:compile`
runs later in that same JVM. Enable it with an execution in the module's plugin configuration (see
[`juno-examples/pom.xml`](../juno-examples/pom.xml)):

```xml
<executions>
    <execution>
        <goals>
            <goal>env</goal>
        </goals>
    </execution>
</executions>
```

Then create `.env` (never committed — it's in this repository's `.gitignore`) next to that
module's `pom.xml`:

```text
JUNO_WIFI_SSID=your-network-name
JUNO_WIFI_PASSWORD=your-network-password
```

A missing `.env` file is not an error — nothing is exported, so modules that don't need it are
unaffected. Mutating the JVM's environment map is an unsupported-but-stable reflection trick that,
on JDK 9+, needs the Maven JVM launched with `--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED` — this repository's
[`.mvn/jvm.config`](../.mvn/jvm.config) already adds both flags to every `mvn`/`mvnw` invocation.

#### This is not runtime secret storage

Environment variables keep credentials out of Java source and Git history, but Juno embeds the
resolved value directly into the generated `.ino` sketch and firmware as a plain string constant —
anyone who extracts the compiled firmware can read it back out. Keep build artifacts private, avoid
exposing the firmware, and clear exported variables when they are no longer needed.

### Buffers are caller-owned, forever

Juno has no general heap — every `new T[N]` (`N` a compile-time constant) is allocated from a
fixed, program-lifetime arena reclaimed by a conservative mark/sweep collector (see
[docs/FEATURES.md](FEATURES.md)). Declaring a fresh response buffer inside `while (true)` still
works, but it churns the arena on every iteration for no reason; declare it once, outside the loop,
and reuse it:

```java
byte[] response = new byte[512];
while (true) {
    int responseBytes = HttpClient.get(HOST, PORT, PATH, response, response.length);
    // ...
    Delay.millis(60000);
}
```

### Result codes are just documented conventions

There is no exception mechanism here — every intrinsic reports success/failure through its
returned `int`. `HttpClient.get` returns `-1` for a failed TCP connection, `0` for an empty body,
or the number of body bytes captured; `Smtp.send` (see [docs/EMAIL.md](EMAIL.md)) instead uses a
distinct negative code per failing protocol step. Each API's Javadoc is the source of truth for
its own codes — read it before assuming `< 0` always means the same thing across different APIs.

## Anatomy of an API, piece by piece

Adding (or understanding) an intrinsic touches four places, all in the `juno` module. Numbers
below match `HttpClient.get`'s real wiring.

### 1. The Java-facing API class

`io.github.jabrena.juno.api.io.net.http.HttpClient` — `final`, private constructor, one `public
static native` method per operation. This is the only file `juno-examples` code ever imports;
everything past this point is internal to `juno` itself.

### 2. An `Intrinsic` enum constant

`io.github.jabrena.juno.intrinsic.Intrinsic` gets one constant per distinct operation
(`HTTP_GET`, `HTTP_POST`, `SMTP_SEND`, `POP3_READ_SUBJECT`, ...) — the compiler's own internal name
for "the hardware/network thing this call means," independent of the exact Java method signature
that reaches it.

### 3. An `IntrinsicRegistry` entry

`io.github.jabrena.juno.intrinsic.IntrinsicRegistry` maps the method's exact JVM-internal
`MethodRef` (owner as a slash-separated internal name, plus the JVM descriptor string) to that
`Intrinsic` constant:

```java
Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpClient", "get",
        "(Ljava/lang/String;ILjava/lang/String;[BI[BI[I)I"), Intrinsic.HTTP_GET),
```

This is the single place `BytecodeToIr` (bytecode-lowering time) decides a given call site is an
intrinsic rather than an ordinary static method call — get the descriptor string wrong (a missing
`;`, the wrong primitive letter) and the registry entry simply never matches, silently falling
back to "ordinary method call," which then fails to link since there's no real method body.

By default, every `String`-typed parameter of every intrinsic must be a compile-time literal (see
[above](#consuming-an-api-httpclient-walkthrough)); to allow a runtime value instead for one
specific parameter, add it to `IntrinsicRegistry.RUNTIME_STRING_PARAMETERS` — the one example today
is `HttpServer#respond`'s body, which is a plain `const char*` to its shim either way.

### 4. Backend codegen

`io.github.jabrena.juno.backend.CortexM4AsmBackend` has one big `switch (call.intrinsic())`. Each
case does two things:

- emits the ARM assembly to load the call's arguments and `bl` into a shim function, via
  `emitShimCall` — a helper that loads up to four words into `r0`-`r3` and spills any more onto the
  stack, so it handles an arbitrarily long argument list without hand-written register allocation;
- sets a `usesHttp`-style boolean field, so the corresponding `#include` and C++ shim
  implementation are only emitted into the generated sketch when the program actually calls that
  API — every other generated sketch keeps compiling without paying for code it never uses.

```java
case HTTP_GET, HTTP_DELETE -> {
    usesHttp = true;
    emitShimCall(output, frame,
            call.intrinsic() == Intrinsic.HTTP_GET ? "juno_http_get" : "juno_http_delete",
            List.of(new WordSource.StringAddress(call.literalArguments().get(0)),
                    new WordSource.FromValue(call.arguments().get(0)),
                    new WordSource.StringAddress(call.literalArguments().get(1)),
                    new WordSource.FromValue(call.arguments().get(1)),
                    new WordSource.FromValue(call.arguments().get(2)),
                    new WordSource.FromValue(call.arguments().get(3)),
                    new WordSource.FromValue(call.arguments().get(4)),
                    new WordSource.FromValue(call.arguments().get(5))));
    call.target().ifPresent(target -> store(output, frame, "r0", target));
}
```

#### `arguments()` vs. `literalArguments()`

`IrInstruction.IntrinsicCall` splits a call's arguments into two lists, each in the method's own
left-to-right declaration order: `literalArguments()` holds every `String`-typed argument (already
resolved to its compile-time value), and `arguments()` holds everything else (`int`, `byte[]`,
`int[]`, ...). Wiring a new case means walking the method's declared parameters left to right and
pulling the next item off whichever list matches that parameter's type — `HttpClient.get(String
host, int port, String path, byte[] responseBuffer, ...)` above pulls `literalArguments().get(0)`
(host), then `arguments().get(0)` (port), then `literalArguments().get(1)` (path), then
`arguments().get(1..)` for the rest. Get this interleaving wrong and arguments silently land in
the wrong shim parameter — there is no type-level check tying the two lists back to the original
signature.

#### The C++ shim

Anything nontrivial (an HTTP request/response loop, a POP3 login handshake, string parsing) is not
hand-written assembly — it is generated **C++ text**, emitted as a Java text block from a
`usesXyz()`-gated helper method (e.g. `httpHelpers()`, `emailHelpers()`) and appended to the
sketch's `Shim.cpp`. The Arduino toolchain compiles this like any other C++ source file in the
sketch. This is a deliberate, established pattern in this backend: reach for it before hand-rolling
new assembly, and reuse an existing shim helper (e.g. `juno_read_line`) across APIs that need the
same primitive instead of duplicating it — see `emailHelpers()`'s doc comment for why `Smtp` and
`Pop3Client` share it despite using two unrelated Arduino client types.

## Two different recipes, depending on what you're adding

Not every new capability needs a new intrinsic:

- **A pure Java convenience wrapper** around existing intrinsics needs none of the above — see
  `Delay.seconds(int)`, a plain (non-`native`) static method that just calls `millis(seconds *
  1000)`. It compiles down to an ordinary user method (a `juno_fnN` label calling `bl
  juno_delay_millis`), not a new `Intrinsic` constant. Prefer this whenever the operation is fully
  expressible in terms of what already exists.
- **A genuinely new hardware/protocol operation** (a new network protocol, a new peripheral) needs
  the full four-piece wiring above, plus a C++ shim implementation.

### Checklist for a new intrinsic

1. Add the `public static native` method(s) to a (possibly new) API class, with full Javadoc
   covering compile-time-string requirements and every result code.
2. Add one `Intrinsic` enum constant per operation.
3. Add the `IntrinsicRegistry` entry (or entries, for overloads) with the exact JVM descriptor.
4. Add the backend `switch` case: `emitShimCall` with correctly-interleaved
   `arguments()`/`literalArguments()`, plus a `usesXyz` flag gating both the `#include`(s) and the
   shim helper.
5. Write the C++ shim as a text block in a `usesXyz()`-gated helper method, reusing existing
   helpers (`emitShimCall`'s target functions, `juno_read_line`-style primitives) where they fit.
6. Add a backend unit test (`CortexM4AsmBackendTest`) asserting the emitted `bl <shim>`, the
   `#include`(s), and that an unrelated program does *not* pull the new shim in — see
   `lowersSmtpSendThroughAStarttlsShim` for the pattern.
7. Add an end-to-end test (`JunoCompilerTest`) that compiles real Java source through the full
   pipeline (`CompilerTestSupport.compileJava` + `compileJuno`) and asserts on the generated
   assembly/shim text — see `lowersEmailIntrinsics`.
8. If the API needs a third-party Arduino library not bundled with `arduino:renesas_uno`, add it
   to `InstallDepsMojo.OPTIONAL_LIBRARIES` and document the manual-install command in
   [docs/ARDUINO.md](ARDUINO.md).
9. Add or extend a `juno-examples` program, and — before considering the feature done — actually
   flash it to a real board and confirm the behavior; a clean `arduino-cli compile` only proves the
   generated C++ is syntactically valid, not that the protocol logic is correct.
10. Write (or extend) an end-user doc under `docs/` — see [docs/EMAIL.md](EMAIL.md) for the
    `Smtp`/`Pop3Client` example this guide was written alongside.

## See also

- [docs/INTERNET.md](INTERNET.md) — full end-user reference for `Wifi`/`HttpClient`/
  `HttpsClient`/`Json`.
- [docs/EMAIL.md](EMAIL.md) — full end-user reference for `Smtp`/`Pop3Client`, and a second worked
  example of everything in this guide (including a case where an intrinsic needed a third-party
  library).
- [docs/FEATURES.md](FEATURES.md) — the supported Java subset these APIs are built on top of
  (arrays, the arena, runtime strings, ...).
