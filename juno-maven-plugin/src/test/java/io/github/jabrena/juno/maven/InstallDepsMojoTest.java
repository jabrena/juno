package io.github.jabrena.juno.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static io.github.jabrena.juno.maven.MojoConfig.configuration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code juno:install-deps} through a real, Maven-configured {@link InstallDepsMojo}; never touches a real
 * process. */
@DisplayName("juno:install-deps goal")
class InstallDepsMojoTest extends AbstractMojoTestCase {
    @BeforeEach
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Test
    @DisplayName("Installs the core, then every optional library")
    void shouldInstallTheCoreThenEveryOptionalLibrary() throws Exception {
        // Given
        InstallDepsMojo mojo = lookupConfiguredMojo();
        RecordingLog log = new RecordingLog();
        mojo.setLog(log);
        FakeExecutor executor = new FakeExecutor();
        for (int step = 0; step < 6; step++) {
            executor.respond(0, "installed");
        }
        mojo.useExecutor(executor);

        // When
        mojo.execute();

        // Then
        assertThat(executor.invocations()).hasSize(6);
        assertThat(executor.invocations().get(0).arguments())
                .containsExactly("arduino-cli", "core", "install", "arduino:renesas_uno");
        assertThat(executor.invocations().subList(1, 6))
                .extracting(command -> command.arguments().get(3))
                .containsExactly("Mouse", "ArduinoBLE", "ESP_SSLClient", "Servo", "SdFat");
        assertThat(log.infos()).anyMatch(line -> line.contains("Installing Arduino dependencies (6 steps)"));
        assertThat(log.infos()).anyMatch(line -> line.contains("installation completed in"));
    }

    @Test
    @DisplayName("Stops and logs on the first failing install")
    void shouldStopAndLogOnTheFirstFailingInstall() throws Exception {
        // Given
        InstallDepsMojo mojo = lookupConfiguredMojo();
        RecordingLog log = new RecordingLog();
        mojo.setLog(log);
        FakeExecutor executor = new FakeExecutor()
                .respond(0, "core installed")
                .respond(1, "network unreachable");
        mojo.useExecutor(executor);

        // When & Then
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("network unreachable");

        assertThat(executor.invocations()).hasSize(2);
        assertThat(log.errors()).anyMatch(line -> line.contains("installation failed after"));
    }

    private InstallDepsMojo lookupConfiguredMojo() throws Exception {
        InstallDepsMojo mojo = lookupEmptyMojo("install-deps", new File("."));
        return configureMojo(mojo, configuration().set("arduinoCli", "arduino-cli").build());
    }
}
