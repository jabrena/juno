package io.github.jabrena.juno.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads a git-ignored {@code .env} file (simple {@code KEY=VALUE} lines) from the module's base
 * directory and exports every entry into this JVM's real process environment, overwriting any
 * value already set for that name. Nothing is generated and no Java source is touched: a program
 * still reads credentials with a plain {@code System.getenv("NAME")}, which Juno resolves at
 * compile time (see {@code BytecodeToIr}) exactly as if the shell running the build had exported
 * the variable itself. Runs at {@code generate-sources}, before {@code juno-maven-plugin:compile}
 * runs in the same JVM at {@code process-classes}. A missing {@code .env} file is not an error:
 * nothing is exported, so modules that don't need it are unaffected. Mutating
 * {@code java.lang.ProcessEnvironment} needs the Maven JVM launched with
 * {@code --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED}
 * (see {@code .mvn/jvm.config}) on JDK 9+.
 */
@Mojo(name = "env", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public final class EnvMojo extends AbstractMojo {

    /** The .env file to read. Defaults to a file named .env in the module's base directory. */
    @Parameter(property = "juno.envFile", defaultValue = "${project.basedir}/.env")
    private File envFile;

    @Override
    public void execute() throws MojoFailureException {
        if (!envFile.isFile()) {
            getLog().info("No .env file at " + envFile + "; nothing exported");
            return;
        }

        Map<String, String> values = readEnvFile();
        exportToProcessEnvironment(values);
        getLog().info("Exported " + values.size() + " variable(s) from " + envFile + " into the process environment");
    }

    private Map<String, String> readEnvFile() throws MojoFailureException {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(envFile.toPath(), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                int separator = trimmed.indexOf('=');
                if (trimmed.isEmpty() || trimmed.startsWith("#") || separator < 0) {
                    continue;
                }
                values.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
            }
        } catch (IOException exception) {
            throw new MojoFailureException("Failed to read " + envFile, exception);
        }
        return values;
    }

    /**
     * {@code System.getenv} is backed by {@code ProcessEnvironment.theUnmodifiableEnvironment}, an
     * unmodifiable view whose private {@code m} field is the real, mutable map; writing to that map
     * directly is what makes the new values visible to {@code System.getenv} for the rest of this
     * JVM. {@code ProcessEnvironment.theEnvironment} is a separate, unrelated map and mutating it
     * alone has no effect on {@code System.getenv} (verified on JDK 25).
     */
    private void exportToProcessEnvironment(Map<String, String> values) throws MojoFailureException {
        try {
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            Field unmodifiableField = processEnvironmentClass.getDeclaredField("theUnmodifiableEnvironment");
            unmodifiableField.setAccessible(true);
            Object unmodifiableEnvironment = unmodifiableField.get(null);

            Field backingMapField = unmodifiableEnvironment.getClass().getDeclaredField("m");
            backingMapField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, String> backingMap = (Map<String, String>) backingMapField.get(unmodifiableEnvironment);

            backingMap.putAll(values);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            throw new MojoFailureException(
                    "Failed to export " + envFile + " into the process environment; this goal needs the Maven JVM "
                            + "launched with --add-opens java.base/java.lang=ALL-UNNAMED --add-opens "
                            + "java.base/java.util=ALL-UNNAMED (see .mvn/jvm.config)",
                    exception);
        }
    }
}
