package io.github.jabrena.juno.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which Arduino board a Juno program targets, e.g. {@code @Board(ArduinoUnoR4WiFi.class)} on
 * the entry-point class. Juno reads this annotation directly from the class file at compile time; it
 * carries no runtime behavior. A class with no {@code @Board} annotation targets the UNO R4 WiFi by
 * default. The selected board gates board-specific intrinsics, such as {@code LedMatrix}, which only
 * the WiFi variant has.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Board {
    Class<? extends ArduinoBoard> value();
}
