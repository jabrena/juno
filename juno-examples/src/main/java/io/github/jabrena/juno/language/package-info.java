/**
 * Introductory Juno examples for learning Java language fundamentals on the UNO R4 WiFi.
 *
 * <p>The examples progress from basic output to core language features:
 * <ul>
 *   <li>{@link io.github.jabrena.juno.language.HelloWorld} — serial output.</li>
 *   <li>{@link io.github.jabrena.juno.language.PrimitiveTypes} — all eight primitive types and their ranges.</li>
 *   <li>{@link io.github.jabrena.juno.language.Types} — the supported primitive, array, object, UUID, date-time, and
 *       arbitrary-precision number types in one catalogue.</li>
 *   <li>{@link io.github.jabrena.juno.language.Arrays} — primitive arrays, indexing, nested arrays, and array methods.</li>
 *   <li>{@link io.github.jabrena.juno.language.Objects} — constructors, fields, records, enums, and arena allocation.</li>
 *   <li>{@link io.github.jabrena.juno.language.Inheritance} — abstract classes, overriding, {@code super}, and virtual dispatch.</li>
 *   <li>{@link io.github.jabrena.juno.language.Interfaces} — interface dispatch and inherited/default methods.</li>
 *   <li>{@link io.github.jabrena.juno.language.WeakUuid} — a non-cryptographic UUID for the classic UNO R3.</li>
 *   <li>{@link io.github.jabrena.juno.language.Strings} — literals, {@code length}, {@code charAt}, {@code equals}, concatenation, and {@code StringBuilder}.</li>
 *   <li>{@link io.github.jabrena.juno.language.Lambdas} — lambda expressions and method references.</li>
 *   <li>{@link io.github.jabrena.juno.language.Variables} — constants, fields, local variables, and scope.</li>
 *   <li>{@link io.github.jabrena.juno.language.Operators} — arithmetic, logical, bitwise, and other operators.</li>
 *   <li>{@link io.github.jabrena.juno.language.Methods} — parameters, return values, and method calls.</li>
 *   <li>{@link io.github.jabrena.juno.language.ControlFlow} — sequence, bifurcation, and iteration.</li>
 *   <li>{@link io.github.jabrena.juno.language.RandomNumbers} — seeded and bounded pseudorandom values.</li>
 *   <li>{@link io.github.jabrena.juno.language.MathFunctionsSinglePrecision} — {@code java.lang.Math} helpers and rounding on
 *       {@code int} and {@code float}, portable to every board.</li>
 *   <li>{@link io.github.jabrena.juno.language.MathFunctionsDoublePrecision} — double-precision powers, logarithms and
 *       trigonometry, on the UNO R4 WiFi and UNO Q.</li>
 *   <li>{@link io.github.jabrena.juno.language.Exceptions} — throwing and catching exceptions, {@code finally}, and custom exceptions.</li>
 *   <li>{@link io.github.jabrena.juno.language.VirtualThreads} — virtual threads and joining.</li>
 *   <li>{@link io.github.jabrena.juno.language.StructuredConcurrency} — structured task scopes and subtasks.</li>
 *   <li>{@link io.github.jabrena.juno.language.ScopedValuesPrecision} — scoped values with arbitrary-precision arithmetic.</li>
 *   <li>{@link io.github.jabrena.juno.language.Synchronization} — volatile fields, monitors, locks, and atomics.</li>
 * </ul>
 *
 * <p>Every class above has a {@code main} method and writes its results to USB serial at 115200
 * baud. {@link io.github.jabrena.juno.language.UnsupportedFeature} is not part of that tour: it's a
 * counter-example, deliberately outside Juno's supported subset, so {@code juno:compile} rejects
 * it with a link-time diagnostic. The board-backed {@code LocalDateTime.now()} examples are
 * {@link io.github.jabrena.juno.api.hardware.net.udp.SntpTime} and
 * {@link io.github.jabrena.juno.api.hardware.net.udp.SntpTimeSD}; they live with the networking examples because
 * they require Wi-Fi and SNTP.
 */
package io.github.jabrena.juno.language;
