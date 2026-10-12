package io.github.jabrena.juno.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables the target's hardware watchdog timer on {@code @Watchdog}'s entry-point class. UNO R3,
 * UNO WiFi Rev2 and UNO R4 WiFi are supported. UNO Q is rejected because its current Arduino
 * Zephyr firmware does not enable the watchdog subsystem, even though its STM32U585 contains the
 * peripheral.
 *
 * <p>Juno reads this annotation directly from the class file at compile time; it carries no runtime
 * behavior itself. When present, the generated program starts the watchdog before static
 * initialization and kicks it from generated loop backedges and the core's {@code yield()} path, so
 * no user code has to call anything. {@code juno_panic()} deliberately never kicks it (nor does
 * anything else once a panic halts the program), so instead of hanging forever, the board reboots
 * itself after the selected timeout — after printing a Serial diagnostic before interrupts are
 * disabled.
 *
 * <p>AVR watchdogs provide discrete periods. Juno rounds {@link #timeoutMillis()} up to the nearest
 * available period: at most 8,000 ms on UNO R3 and approximately 8,200 ms on UNO WiFi Rev2.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Watchdog {
    /** Requested milliseconds without a generated watchdog refresh before the board resets. */
    int timeoutMillis() default 5000;
}
