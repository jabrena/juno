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
- direct static calls with closed-world reachability; unused methods are omitted
- opaque `DigitalOutput` handles which compile down to integer pin numbers without heap allocation
- Java-compatible 32-bit wrapping arithmetic and divide-overflow behavior
- `.class` inputs from directories, individual files, or JARs

Not yet supported:

- general objects, constructors, instance/virtual/interface calls, multi-dimensional arrays, or
  returning a locally created array (only forwarding a received array parameter is supported)
- mutable/non-constant static fields (needs `getstatic`/`putstatic`), strings, exceptions, garbage
  collection, threads, reflection, or dynamic loading
- `long` as a method parameter/return type, field, or array element type; `float` and `double`
  entirely
- enums
- the desktop JDK class library

The `String[]` parameter of a conventional `main` is accepted as an entrypoint convention, but
there are no command-line arguments on the board and it must not be accessed.
