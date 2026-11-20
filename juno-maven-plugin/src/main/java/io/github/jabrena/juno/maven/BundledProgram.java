package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.hardware.MCU;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * A board-side program the plugin ships, compiled when a goal is run without {@code -Djuno.main}. Juno reads every
 * class of every classpath entry, so the plugin's own jar is not put on the classpath: the program's class file is
 * copied into a directory of its own, which goes on the classpath next to {@code juno-api}, the only library the
 * program uses.
 *
 * @param entryPoint the program's class; it must be a single top-level class with no nested classes
 */
record BundledProgram(Class<?> entryPoint) {

    /** The fully qualified name {@code juno.main} would have named. */
    String mainClass() {
        return entryPoint.getName();
    }

    /** Copies the program into {@code directory} and returns the closed-world classpath to compile it from. */
    List<Path> classpath(Path directory) {
        String resource = entryPoint.getName().replace('.', '/') + ".class";
        Path target = directory.resolve(resource);
        try (InputStream classFile = entryPoint.getClassLoader().getResourceAsStream(resource)) {
            if (classFile == null) {
                throw new ArduinoCliException("The plugin is missing its bundled program " + resource);
            }
            Files.createDirectories(target.getParent());
            Files.copy(classFile, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ArduinoCliException("Cannot copy the bundled program " + mainClass() + " to " + directory, exception);
        }
        return List.of(directory, apiLocation());
    }

    /** The {@code juno-api} jar (or classes directory) the plugin itself was resolved with. */
    private static Path apiLocation() {
        try {
            return Path.of(MCU.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException exception) {
            throw new ArduinoCliException("Cannot locate juno-api for the bundled program", exception);
        }
    }
}
