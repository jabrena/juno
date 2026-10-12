package io.github.jabrena.juno.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs the Arduino core and every optional library any current Juno example needs, so a fresh
 * checkout can build every example without hunting down "arduino-cli lib install X" instructions
 * one at a time. Run this once per machine (or again after a new example starts needing a new
 * optional library) — ordinary {@code compile}/{@code verify}/{@code upload} builds never run
 * this themselves, so they don't pay arduino-cli's library-index/network cost on every single
 * build.
 */
@Mojo(name = "install-deps", defaultPhase = LifecyclePhase.NONE, threadSafe = true)
public final class InstallDepsMojo extends AbstractArduinoMojo {
    /**
     * Every optional Arduino library (not bundled with the {@code arduino:renesas_uno} core) any
     * current Juno example needs: {@code Mouse} for {@code RatonLoco}, {@code ArduinoBLE} for
     * {@code PoweredUpHubRemote} (e.g. {@code LegoTrain}), {@code ESP_SSLClient} for
     * {@code Smtp}'s {@code STARTTLS} upgrade, {@code Servo} for {@code Servo}-driven examples,
     * and {@code SdFat} for long-file-name-capable removable-storage examples.
     * Update this list whenever a new example starts needing another one.
     */
    private static final List<String> OPTIONAL_LIBRARIES = List.of(
            "Mouse", "ArduinoBLE", "ESP_SSLClient", "Servo", "SdFat");

    /** The arduino-cli platform (board family) backing every {@code @Board} target Juno supports today. */
    private static final String CORE_PLATFORM = "arduino:renesas_uno";

    @Override
    void run() {
        List<InstallStep> steps = installSteps(arduinoCli());
        Stopwatch installation = new Stopwatch();
        getLog().info("Installing Arduino dependencies (" + steps.size() + " steps)");
        try {
            for (int index = 0; index < steps.size(); index++) {
                install(steps.get(index), "[" + (index + 1) + "/" + steps.size() + "] ");
            }
            getLog().info("Arduino dependency installation completed in " + installation.elapsedMillis() + " ms");
        } catch (ArduinoCliException exception) {
            getLog().error("Arduino dependency installation failed after " + installation.elapsedMillis() + " ms");
            throw exception;
        }
    }

    private List<InstallStep> installSteps(ArduinoCli cli) {
        List<InstallStep> steps = new ArrayList<>();
        steps.add(new InstallStep("Arduino core " + CORE_PLATFORM, () -> cli.installCore(CORE_PLATFORM)));
        OPTIONAL_LIBRARIES.forEach(library ->
                steps.add(new InstallStep("Arduino library " + library, () -> cli.installLibrary(library))));
        return steps;
    }

    private void install(InstallStep step, String prefix) {
        Stopwatch stopwatch = new Stopwatch();
        getLog().info(prefix + "Installing " + step.description());
        step.installation().run();
        getLog().info(prefix + "Installed " + step.description() + " in " + stopwatch.elapsedMillis() + " ms");
    }

    private record InstallStep(String description, Runnable installation) {
    }

    /** Measures wall-clock time from its creation. */
    private static final class Stopwatch {
        private final long startedNanos = System.nanoTime();

        long elapsedMillis() {
            return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        }
    }
}
