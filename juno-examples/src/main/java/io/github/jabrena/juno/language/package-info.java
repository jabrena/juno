/**
 * Introductory Juno examples for learning Java language fundamentals on the UNO R4 WiFi.
 *
 * <p>The examples progress from basic output to core language features:
 * <ul>
 *   <li>{@link io.github.jabrena.juno.language.HelloWorld} — serial output.</li>
 *   <li>{@link io.github.jabrena.juno.language.PrimitiveTypes} — all eight primitive types and their ranges.</li>
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
 * </ul>
 *
 * <p>Every class above has a {@code main} method and writes its results to USB serial at 115200
 * baud. {@link io.github.jabrena.juno.language.UnsupportedFeature} is not part of that tour: it's a
 * counter-example, deliberately outside Juno's supported subset, so {@code juno:compile} rejects
 * it with a link-time diagnostic.
 */
package io.github.jabrena.juno.language;
