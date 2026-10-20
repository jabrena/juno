package io.github.jabrena.juno.maven.fixture;

import io.github.jabrena.juno.api.hardware.gpio.Gpio;

import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * The closed-world classpath a Mojo test compiles {@link TinyProgram} from: this module's test classes and juno-api's
 * classes, whose {@link Gpio} the program links like any other class. A real build gets both from Maven.
 */
public final class TinyProgramClasspath {
    private TinyProgramClasspath() {
    }

    public static String[] elements() {
        return new String[] {"target/test-classes", apiLocation()};
    }

    private static String apiLocation() {
        try {
            return Path.of(Gpio.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
