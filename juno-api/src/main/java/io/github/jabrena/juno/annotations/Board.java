package io.github.jabrena.juno.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which Arduino board(s) a Juno program targets, e.g. {@code @Board(ArduinoUnoR4WiFi.class)},
 * {@code @Board(ArduinoUnoQ.class)}, or {@code @Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})} on
 * the entry-point class. Juno reads this annotation directly from the class file at compile time; it
 * carries no runtime behavior. A class with no {@code @Board} annotation targets the UNO R4 WiFi by
 * default. Every declared board gates board-specific intrinsics, such as {@code LedMatrix}, which only
 * the WiFi variant has: a program that lists more than one board must compile cleanly for all of them,
 * not just whichever one ends up selected for a given build.
 *
 * <p>When more than one board is declared, the actual build must say which one it wants: the standalone
 * CLI's {@code --board=<id>} option or the Maven plugin's {@code -Djuno.board=<id>} property (see
 * {@code io.github.jabrena.juno.board.Board#fromId}). Compilation fails rather than picking one
 * silently.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Board {
    Class<? extends ArduinoBoard>[] value();
}
