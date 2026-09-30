/**
 * Introductory Juno examples for learning Java language fundamentals on the UNO R4 WiFi.
 *
 * <p>The examples progress from basic output to core language features:
 * <ul>
 *   <li>{@link io.github.jabrena.juno.HelloWorld} — serial output.</li>
 *   <li>{@link io.github.jabrena.juno.DataTypes} — primitive data types and ranges.</li>
 *   <li>{@link io.github.jabrena.juno.Variables} — constants, fields, local variables, and scope.</li>
 *   <li>{@link io.github.jabrena.juno.Operators} — arithmetic, logical, bitwise, and other operators.</li>
 *   <li>{@link io.github.jabrena.juno.Methods} — parameters, return values, and method calls.</li>
 *   <li>{@link io.github.jabrena.juno.ControlFlow} — sequence, bifurcation, and iteration.</li>
 *   <li>{@link io.github.jabrena.juno.RandomNumbers} — seeded and bounded pseudorandom values.</li>
 *   <li>{@link io.github.jabrena.juno.MathFunctions} — {@code java.lang.Math} helpers, rounding, and trigonometry.</li>
 *   <li>{@link io.github.jabrena.juno.Exceptions} — throwing and catching exceptions, {@code finally}, and custom exceptions.</li>
 * </ul>
 *
 * <p>Every class above has a {@code main} method and writes its results to USB serial at 115200
 * baud. {@link io.github.jabrena.juno.UnsupportedFeature} is not part of that tour: it's a
 * counter-example, deliberately outside Juno's supported subset, so {@code juno:compile} rejects
 * it with a link-time diagnostic.
 */
package io.github.jabrena.juno;
