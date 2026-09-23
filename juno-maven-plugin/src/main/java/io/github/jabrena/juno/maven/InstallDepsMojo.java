package io.github.jabrena.juno.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.util.List;

/**
 * Installs the Arduino core and every optional library any current Juno example needs, so a fresh
 * checkout can build every example without hunting down "arduino-cli lib install X" instructions
 * scattered across {@code docs/ARDUINO.md} one at a time. Run this once per machine (or again after
 * a new example starts needing a new optional library) — ordinary {@code compile}/{@code verify}/
 * {@code upload} builds never run this themselves, so they don't pay arduino-cli's library-index/
 * network cost on every single build.
 */
@Mojo(name = "install-deps", defaultPhase = LifecyclePhase.NONE, threadSafe = true)
public final class InstallDepsMojo extends AbstractArduinoMojo {
    /**
     * Every optional Arduino library (not bundled with the {@code arduino:renesas_uno} core) any
     * current Juno example needs: {@code Mouse} for {@code RatonLoco}, {@code ESP_SSLClient} for
     * {@code Smtp}'s {@code STARTTLS} upgrade, {@code Servo} for {@code Servo}-driven examples.
     * Update this list — and the matching mention in {@code docs/ARDUINO.md} — together whenever a
     * new example starts needing another one.
     */
    private static final List<String> OPTIONAL_LIBRARIES = List.of("Mouse", "ESP_SSLClient", "Servo");

    /** The arduino-cli platform (board family) backing every {@code @Board} target Juno supports today. */
    private static final String CORE_PLATFORM = "arduino:renesas_uno";

    @Override
    public void execute() throws MojoFailureException {
        try {
            ArduinoCli cli = arduinoCli();
            getLog().info("Installing Arduino core " + CORE_PLATFORM);
            cli.installCore(CORE_PLATFORM);
            for (String library : OPTIONAL_LIBRARIES) {
                getLog().info("Installing Arduino library " + library);
                cli.installLibrary(library);
            }
        } catch (ArduinoCliException exception) {
            throw failure(exception);
        }
    }
}
