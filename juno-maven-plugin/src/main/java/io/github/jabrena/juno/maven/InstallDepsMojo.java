package io.github.jabrena.juno.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

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
    public void execute() throws MojoFailureException {
        int totalSteps = OPTIONAL_LIBRARIES.size() + 1;
        long installationStarted = System.nanoTime();
        getLog().info("Installing Arduino dependencies (" + totalSteps + " steps)");
        try {
            ArduinoCli cli = arduinoCli();
            installStep(1, totalSteps, "Arduino core " + CORE_PLATFORM,
                    () -> cli.installCore(CORE_PLATFORM));
            for (int index = 0; index < OPTIONAL_LIBRARIES.size(); index++) {
                String library = OPTIONAL_LIBRARIES.get(index);
                installStep(index + 2, totalSteps, "Arduino library " + library,
                        () -> cli.installLibrary(library));
            }
            getLog().info("Arduino dependency installation completed in "
                    + elapsedMillis(installationStarted) + " ms");
        } catch (ArduinoCliException exception) {
            getLog().error("Arduino dependency installation failed after "
                    + elapsedMillis(installationStarted) + " ms");
            throw failure(exception);
        }
    }

    private void installStep(int step, int totalSteps, String description, Runnable installation) {
        long started = System.nanoTime();
        String prefix = "[" + step + "/" + totalSteps + "] ";
        getLog().info(prefix + "Installing " + description);
        installation.run();
        getLog().info(prefix + "Installed " + description + " in " + elapsedMillis(started) + " ms");
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
