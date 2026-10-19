package io.github.jabrena.juno.maven;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.jabrena.juno.maven.MojoConfig.configuration;
import static org.assertj.core.api.Assertions.assertThat;

/** {@code juno:env} through a real, Maven-configured {@link EnvMojo}. Exports into the real process environment
 * (as the goal is documented to do), under keys unique to this test so other tests are unaffected. */
@NullMarked
@DisplayName("juno:env goal")
class EnvMojoTest extends AbstractMojoTestCase {
    @BeforeEach
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Test
    @DisplayName("Exports every entry from the configured env file, keeping the last value of a repeated key")
    void shouldExportEveryEntryFromTheConfiguredEnvFileKeepingTheLastValueOfARepeatedKey(@TempDir Path tempDir)
            throws Exception {
        // Given
        Path envFile = writeEnvFile(tempDir, """
                # a comment, ignored
                JUNO_TEST_ENV_MOJO_KEY=first
                JUNO_TEST_ENV_MOJO_KEY=second

                ANOTHER_JUNO_TEST_KEY = spaced value
                """);
        EnvMojo mojo = lookupEmptyMojo("env", new File("."));
        mojo = configureMojo(mojo, configuration().set("envFile", envFile.toString()).build());
        RecordingLog log = new RecordingLog();
        mojo.setLog(log);

        // When
        mojo.execute();

        // Then
        assertThat(System.getenv("JUNO_TEST_ENV_MOJO_KEY")).isEqualTo("second");
        assertThat(System.getenv("ANOTHER_JUNO_TEST_KEY")).isEqualTo("spaced value");
        assertThat(log.infos()).anyMatch(line -> line.contains("Exported 2 variable(s)"));
    }

    @Test
    @DisplayName("Does nothing when the env file is missing")
    void shouldDoNothingWhenTheEnvFileIsMissing(@TempDir Path tempDir) throws Exception {
        // Given
        EnvMojo mojo = lookupEmptyMojo("env", new File("."));
        mojo = configureMojo(mojo, configuration()
                .set("envFile", tempDir.resolve("does-not-exist.env").toString())
                .build());
        RecordingLog log = new RecordingLog();
        mojo.setLog(log);

        // When
        mojo.execute();

        // Then
        assertThat(log.infos()).anyMatch(line -> line.contains("nothing exported"));
    }

    private static Path writeEnvFile(Path directory, String content) throws IOException {
        Path file = directory.resolve("sample.env");
        Files.writeString(file, content);
        return file;
    }
}
