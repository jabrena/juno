package io.github.jabrena.juno.thirdparty.bearssl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BearSslLibraryTest {
    @TempDir
    private Path directory;

    @Test
    @DisplayName("Installs the unmodified BearSSL 0.6 sources as an Arduino library")
    void shouldInstallTheBearSslSourcesAsAnArduinoLibrary() throws IOException {
        // When
        Path library = new BearSslLibrary().installInto(directory);

        // Then
        assertThat(library).isEqualTo(directory.resolve("JunoBearSSL"));
        assertThat(Files.readString(library.resolve("library.properties")))
                .contains("name=JunoBearSSL", "version=0.6.0", "architectures=*");
        assertThat(Files.readString(library.resolve("LICENSE.txt"))).contains("Thomas Pornin");
        assertThat(library.resolve("src/bearssl.h")).isRegularFile();
        assertThat(library.resolve("src/inner.h")).isRegularFile();
        assertThat(library.resolve("src/ssl/ssl_server_full_ec.c")).isRegularFile();
        // Kept as upstream ships it: the Cortex-M inline assembly stays opt-in.
        assertThat(Files.readString(library.resolve("src/config.h"))).contains("#define BR_ARMEL_CORTEXM_GCC   1\n */");
    }

    @Test
    @DisplayName("Keeps an installed copy whose library.properties matches")
    void shouldKeepAMatchingInstalledCopy() throws IOException {
        // Given
        BearSslLibrary bearSsl = new BearSslLibrary();
        Path library = bearSsl.installInto(directory);
        Path source = library.resolve("src/bearssl.h");
        Files.writeString(source, "kept");

        // When
        bearSsl.installInto(directory);

        // Then
        assertThat(source).hasContent("kept");
    }

    @Test
    @DisplayName("Replaces an installed copy of another version")
    void shouldReplaceAnInstalledCopyOfAnotherVersion() throws IOException {
        // Given
        BearSslLibrary bearSsl = new BearSslLibrary();
        Path library = bearSsl.installInto(directory);
        Files.writeString(library.resolve("library.properties"), "version=0.5\n");
        Files.writeString(library.resolve("src/bearssl.h"), "stale");

        // When
        bearSsl.installInto(directory);

        // Then
        assertThat(Files.readString(library.resolve("src/bearssl.h"))).isNotEqualTo("stale");
        assertThat(Files.readString(library.resolve("library.properties"))).contains("version=0.6.0");
    }

    @Test
    @DisplayName("Installs the library from inside a jar, as the Maven plugin sees it")
    void shouldInstallTheLibraryFromInsideAJar() throws IOException {
        // Given a jar holding a small library in the same layout
        Path jar = directory.resolve("bearssl.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            write(output, "arduino-libraries/JunoBearSSL/library.properties", "name=JunoBearSSL\n");
            write(output, "arduino-libraries/JunoBearSSL/src/ssl/engine.c", "/* engine */\n");
        }
        Path target = directory.resolve("libraries");

        // When
        Path library;
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            library = new BearSslLibrary(loader).installInto(target);
        }

        // Then
        assertThat(library.resolve("src/ssl/engine.c")).hasContent("/* engine */");
        assertThat(library.resolve("library.properties")).hasContent("name=JunoBearSSL");
    }

    @Test
    @DisplayName("Fails when the sources are not on the classpath")
    void shouldFailWhenTheSourcesAreMissing() throws IOException {
        try (URLClassLoader empty = new URLClassLoader(new URL[0], null)) {
            // When & Then
            assertThatThrownBy(() -> new BearSslLibrary(empty).installInto(directory))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("JunoBearSSL");
        }
    }

    private static void write(JarOutputStream jar, String name, String content) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        OutputStream output = jar;
        output.write(content.getBytes(StandardCharsets.UTF_8));
        jar.closeEntry();
    }
}
