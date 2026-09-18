# Supported Java subset

Juno deliberately fails at link time when reachable code uses something outside the current
subset. Diagnostics identify the method, bytecode offset, and unsupported opcode.

Supported today:

- `static void main(String[])` and the embedded-friendly `static void main()`
- static methods with `boolean`, `byte`, `char`, `short`, and `int` arguments/results
- local variables, integer constants, arithmetic, bitwise operations, shifts, comparisons,
  conditionals, and loops
- `private/static final` primitive constants (`boolean`/`byte`/`char`/`short`/`int`) initialized
  with a compile-time constant expression, same class or a different one — `javac` inlines these
  as ordinary literals (JLS 4.12.4), so Juno never sees a field read
- arrays of `boolean`/`byte`/`char`/`short`/`int`: `new T[N]` with a compile-time-constant `N`
  (there is no heap, so every array is a fixed-size local C array, stored at its natural width —
  `byte[]`/`boolean[]` as `int8_t`, `char[]` as `uint16_t`, `short[]` as `int16_t`, `int[]` as
  `int32_t`); passing an array to another static method (pointer semantics); returning an array is
  supported only when directly forwarding a received array parameter (e.g. `return arr;`), never a
  locally created one, since that pointer would dangle once the method returns. `arr.length` and
  bounds-checked `arr[i]`/`arr[i] = v` work only on an array local that is assigned exactly once in
  its method (i.e. "effectively final") right after `new T[...]` — an array received as a
  parameter, or a reassigned local, still supports `arr[i]`/`arr[i] = v` but without a bounds check
  and without `.length`, since its size isn't known at compile time there. Multi-dimensional arrays
  are not supported.
- `long` locals with arithmetic, shifts, bitwise operations, comparisons, `int`/`long` conversions,
  and loops — represented internally as a pair of 32-bit halves, since there is no 64-bit register on
  the target. Not supported as a method parameter or return type, a field, or an array element type.
- `float` locals with constants, arithmetic (including remainder), negation, comparisons, `int`/`float`
  conversions, and loops. Java's NaN comparison rules and saturating/NaN-to-zero `float`-to-`int`
  conversion are preserved. Not supported as a method parameter or return type, a field, or an array
  element type. `double` remains entirely unsupported.
- `enum` constants as plain 0-based ordinal `int`s — Juno never constructs a real enum object (no
  heap), it reads a constant's declaration-order position directly. Supported: enum-typed locals,
  parameters, and return values; `==`/`!=` comparisons (including against a named constant). Not
  supported: `switch` on an enum (javac routes it through a synthetic lookup-table class Juno doesn't
  understand), `.name()`/`.toString()`/`.compareTo()`/`.ordinal()`, `values()`/`valueOf()`, and
  per-constant fields, methods, or constructors.
- simple `record`s with `boolean`/`byte`/`char`/`short`/`int` components, as local variables only —
  Juno never constructs a real object (no heap), a record decomposes into its N component values
  directly at `new`, and an accessor call (`p.x()`) resolves straight to the matching value. Only the
  plain compiler-generated canonical constructor and accessors are accepted (verified by their exact
  bytecode shape); a compact/custom constructor or a hand-written accessor override is rejected, since
  Juno cannot otherwise tell it apart from the trivial default. Not supported: records as a method
  parameter or return type (an N-component record needs N scalar slots, which would need real
  parameter-slot renumbering), `equals()`/`hashCode()`/`toString()`, non-canonical constructors, and
  non-int-like components (nested records, arrays, `long`, `String`, etc.).
- direct static calls with closed-world reachability; unused methods are omitted
- opaque `DigitalOutput` handles which compile down to integer pin numbers without heap allocation
- Java-compatible 32-bit wrapping arithmetic and divide-overflow behavior
- `.class` inputs from directories, individual files, or JARs

Not yet supported:

- general objects, constructors, instance/virtual/interface calls, multi-dimensional arrays, or
  returning a locally created array (only forwarding a received array parameter is supported) —
  simple records (see above) are the one narrow exception
- mutable/non-constant static fields (needs `getstatic`/`putstatic`), strings, exceptions, garbage
  collection, threads, reflection, or dynamic loading
- `long` or `float` as a method parameter/return type, field, or array element type; `double` entirely
- `switch` on an enum, enum instance methods (`.name()`, `.ordinal()`, etc.), `values()`/`valueOf()`,
  and enums with per-constant state (custom constructors/fields/abstract methods)
- records as a method parameter/return type, `equals()`/`hashCode()`/`toString()`, non-canonical
  constructors, custom accessor overrides, and non-int-like record components
- the desktop JDK class library

The `String[]` parameter of a conventional `main` is accepted as an entrypoint convention, but
there are no command-line arguments on the board and it must not be accessed.
