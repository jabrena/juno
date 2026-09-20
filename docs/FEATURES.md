# Supported Java subset

Juno deliberately fails at link time when reachable code uses something outside the current
subset. Diagnostics identify the method, bytecode offset, and unsupported opcode.

Supported today:

- `static void main(String[])` and the embedded-friendly `static void main()`
- static methods with primitive arguments/results, including two-slot `long` and `double` values
- local variables, integer constants, arithmetic, bitwise operations, shifts, comparisons,
  conditionals, and loops
- `private/static final` primitive constants (`boolean`/`byte`/`char`/`short`/`int`) initialized
  with a compile-time constant expression, same class or a different one — `javac` inlines these
  as ordinary literals (JLS 4.12.4), so Juno never sees a field read
- mutable static primitive fields with JVM default-zero initialization. Reachable `<clinit>` methods
  that use the supported subset execute during generated startup; full JVM initialization ordering
  and cycle semantics are not yet modeled.
- arrays of `boolean`/`byte`/`char`/`short`/`int`/`long`/`float`/`double`: `new T[N]` with a compile-time-constant `N`
  (there is no general heap, so every array is allocated from the fixed program-lifetime arena at
  its natural width —
  `byte[]`/`boolean[]` as `int8_t`, `char[]` as `uint16_t`, `short[]` as `int16_t`, `int[]` as
  `int32_t`, `long[]` as `int64_t`, `float[]` as `float`, and `double[]` as `double`); passing an array
  to another static method (pointer semantics); returning an array is
  safe for locally created arrays because array storage comes from Juno's fixed 8 KiB program-lifetime
  arena. `arr.length` and
  bounds-checked `arr[i]`/`arr[i] = v` work only on an array local that is assigned exactly once in
  its method (i.e. "effectively final") right after `new T[...]` — an array received as a
  parameter, or a reassigned local, still supports `arr[i]`/`arr[i] = v` but without a bounds check
  and without `.length`, since its size isn't known at compile time there. Fixed-size multidimensional
  primitive arrays are supported when every dimension is a compile-time constant.
- `long` locals, method parameters/results, fields, and arrays, with arithmetic, shifts, bitwise
  operations, comparisons, numeric conversions, calls, returns, and loops. JVM locals/stack values are
  represented internally as paired 32-bit halves and packed to native `int64_t` at storage/call boundaries.
- `float` locals and static method parameters/results, with constants, arithmetic (including remainder),
  negation, comparisons, `int`/`float` conversions, calls, returns, and loops. Java's NaN comparison rules
  and saturating/NaN-to-zero numeric narrowing are preserved. Float fields and arrays are supported.
- `double` locals, static method parameters/results, fields, arrays, arithmetic, comparisons, numeric
  conversions, calls, and returns. Two JVM slots are preserved for every `double` local and parameter.
- `enum` constants as plain 0-based ordinal `int`s, including `ordinal()`, `values()`, equality, method
  parameters/results, and `switch`. Name/string operations and per-constant state remain unsupported.
- records as arena objects, including parameters/results, canonical and compact constructors, custom
  accessors, and supported primitive/array/reference components. Generated `equals()`/`hashCode()`/
  `toString()` remain unsupported because they require `invokedynamic` and broader object/String support.
- final closed-world classes with constructors, primitive/reference instance fields, and statically
  resolvable instance calls. Objects use a fixed 8 KiB zero-filled bump arena with no reclamation; code
  must not allocate indefinitely inside loops.
- direct static calls with closed-world reachability; unused methods are omitted
- opaque `DigitalOutput` handles which compile down to integer pin numbers without heap allocation
- UNO R4 WiFi networking through allocation-free `Wifi` and HTTP `GET`, `POST`, `DELETE`, `PATCH`,
  and RFC 10008 `QUERY` intrinsics; request bodies use compile-time JSON strings
- bounded, allocation-free JSON inspection over caller-owned `byte[]` buffers: strict whole-document
  validation, object dot paths and zero-based array indexes, value type/null detection, array sizing,
  32/64-bit integers, decimal/exponent numbers, booleans, and JSON-string escape decoding to UTF-8
- Java-compatible 32-bit wrapping arithmetic and divide-overflow behavior
- `.class` inputs from directories, individual files, or JARs

## Runtime-risk inspection

`juno inspect --main <class> --classpath <paths> --risks` analyzes the optimized, closed-world IR
without running or uploading the program. The resulting `CompilationReport.runtimeRisks()` data is
also available programmatically as immutable records with stable diagnostic codes.

The first analysis slice reports:

| Code | Meaning |
|------|---------|
| `JUNO-RISK-001` | Arena allocation occurs directly or transitively inside a control-flow loop. |
| `JUNO-RISK-002` | The conservative startup allocation estimate exceeds the fixed arena capacity. |
| `JUNO-RISK-003` | A recursive call cycle makes generated call depth and stack usage unbounded. |
| `JUNO-RISK-004` | Array accesses exist for which Juno cannot emit a bounds check. |
| `JUNO-RISK-005` | Integer/long division or remainder may receive a zero divisor and panic. |
| `JUNO-RISK-006` | A dereference uses a value proven null at compile time. |

The 8 KiB arena capacity and the counts of emitted bounds checks are exact compiler facts. Arena,
static-RAM, and stack figures are conservative source-level estimates: alignment is overestimated,
branch feasibility is not modeled, and the downstream C++ compiler may optimize stack locals. The
Arduino linker's final memory report is authoritative. Risk findings warn; they do not currently
reject compilation.

Two board examples make the distinction observable:

- `RuntimeRiskSafePulse` allocates once, has generated bounds checks, and should report no findings;
  it blinks alternating 120/360 ms pulses indefinitely.
- `RuntimeRiskArenaExhaustion` is intentionally unsafe. It allocates 1 KiB on every loop iteration,
  shows a one-second startup light, flashes eight more times, and then stops when the ninth allocation
  exhausts the 8 KiB arena. Its `JUNO-RISK-001` warning predicts that failure before upload.

Not yet supported:

- inheritance/polymorphic dispatch, interfaces, object arrays with polymorphism, or garbage collection
- strings, general exceptions, threads,
  reflection, or dynamic loading
- enum string methods (`.name()`, `.toString()`), `valueOf()`,
  and enums with per-constant state (custom constructors/fields/abstract methods)
- record `equals()`/`hashCode()`/`toString()` and other `invokedynamic`-based behavior
- the desktop JDK class library

The `String[]` parameter of a conventional `main` is accepted as an entrypoint convention, but
there are no command-line arguments on the board and it must not be accessed.
