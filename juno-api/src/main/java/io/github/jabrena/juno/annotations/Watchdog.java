package io.github.jabrena.juno.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables the RA4M1's hardware watchdog timer on {@code @Watchdog}'s entry-point class. Juno reads
 * this annotation directly from the class file at compile time; it carries no runtime behavior
 * itself. When present, the generated program starts the watchdog before anything else runs and
 * kicks it ({@code WDT.refresh()}) at every loop backedge — the same point {@code yield()} is
 * already emitted, so no user code has to call anything. {@code juno_panic()} deliberately never
 * kicks it (nor does anything else once a panic halts the program), so instead of hanging forever,
 * the board reboots itself after {@link #timeoutMillis()} — after printing a Serial diagnostic
 * identifying the reboot, since that happens before interrupts are disabled.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Watchdog {
    /** Milliseconds of silence (no {@code WDT.refresh()}) before the watchdog resets the board. */
    int timeoutMillis() default 5000;
}
