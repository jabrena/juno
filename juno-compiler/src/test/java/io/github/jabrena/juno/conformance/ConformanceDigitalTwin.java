package io.github.jabrena.juno.conformance;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Executes a conformance fixture on the host JDK and returns its serial transcript. */
public final class ConformanceDigitalTwin {
    private final Path openJdkClasses;
    private final Path programClasses;
    private final Path junoApiClasses;

    public ConformanceDigitalTwin(Path openJdkClasses, Path programClasses, Path junoApiClasses) {
        this.openJdkClasses = openJdkClasses;
        this.programClasses = programClasses;
        this.junoApiClasses = junoApiClasses;
    }

    public String run(String mainClass) throws IOException, InterruptedException {
        String classpath = String.join(File.pathSeparator, openJdkClasses.toString(), programClasses.toString(),
                junoApiClasses.toString());
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-preview", "-cp", classpath, mainClass).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("OpenJDK run timed out for " + mainClass);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("OpenJDK run failed for " + mainClass + ":\n" + output);
        }
        return output;
    }
}
