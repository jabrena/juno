package io.github.jabrena.juno;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The deliberately bounded {@code java.util.UUID} subset lowered onto the runtime shim. */
@DisplayName("Bounded java.util.UUID subset")
class UuidTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Constructs a UUID and reads both halves")
    void shouldConstructAUuidAndReadBothHalves() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    java.util.UUID value = new java.util.UUID(0x123456789abcdef0L, 0x0fedcba987654321L);
                    long most = value.getMostSignificantBits();
                    long least = value.getLeastSignificantBits();
                }
                """);

        // When & Then
        assertThat(result.assembly()).contains(
                "bl juno_uuid_new", "bl juno_uuid_get_most_significant_bits",
                "bl juno_uuid_get_least_significant_bits");
    }

    @Test
    @DisplayName("The runtime shim declares the UUID constructor and half accessors")
    void shouldDeclareTheUuidConstructorAndHalfAccessorsInTheShim() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    java.util.UUID value = new java.util.UUID(0x123456789abcdef0L, 0x0fedcba987654321L);
                    long most = value.getMostSignificantBits();
                    long least = value.getLeastSignificantBits();
                }
                """);

        // When & Then
        assertThat(result.runtimeShim()).contains(
                "juno_uuid_new", "juno_uuid_get_most_significant_bits",
                "juno_uuid_get_least_significant_bits");
    }

    @Test
    @DisplayName("Lowers value semantics and canonical text to the shim")
    void shouldLowerValueSemanticsAndCanonicalTextToTheShim() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    java.util.UUID parsed = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
                    java.util.UUID copy = new java.util.UUID(
                            parsed.getMostSignificantBits(), parsed.getLeastSignificantBits());
                    int version = parsed.version();
                    int variant = parsed.variant();
                    boolean equal = parsed.equals(copy);
                    int order = parsed.compareTo(copy);
                    int hash = parsed.hashCode();
                    String canonical = parsed.toString();
                    java.util.UUID reparsed = java.util.UUID.fromString(canonical);
                }
                """);

        // When & Then
        assertThat(result.assembly()).contains(
                "bl juno_uuid_from_string", "bl juno_uuid_version", "bl juno_uuid_variant",
                "bl juno_uuid_equals", "bl juno_uuid_compare_to", "bl juno_uuid_hash_code",
                "bl juno_uuid_to_string", "bl juno_throw_pending");
    }

    @Test
    @DisplayName("The runtime shim declares the value helpers and the invalid-argument exception")
    void shouldDeclareTheValueHelpersAndTheInvalidArgumentExceptionInTheShim() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    java.util.UUID parsed = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
                    java.util.UUID copy = new java.util.UUID(
                            parsed.getMostSignificantBits(), parsed.getLeastSignificantBits());
                    int version = parsed.version();
                    int variant = parsed.variant();
                    boolean equal = parsed.equals(copy);
                    int order = parsed.compareTo(copy);
                    int hash = parsed.hashCode();
                    String canonical = parsed.toString();
                    java.util.UUID reparsed = java.util.UUID.fromString(canonical);
                }
                """);

        // When & Then
        assertThat(result.runtimeShim()).contains(
                "juno_uuid_from_string", "juno_uuid_version", "juno_uuid_variant",
                "juno_uuid_equals", "juno_uuid_compare_to", "juno_uuid_hash_code",
                "juno_uuid_to_string", "java.lang.IllegalArgumentException");
    }

    @Test
    @DisplayName("Random UUID is lowered through the R4 hardware entropy provider in the assembly")
    void shouldLowerRandomUuidThroughTheR4HardwareEntropyProviderInTheAssembly() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    java.util.UUID value = java.util.UUID.randomUUID();
                    int version = value.version();
                    int variant = value.variant();
                }
                """);

        // When & Then
        assertThat(result.assembly()).contains("bl juno_uuid_random");
    }

    @Test
    @DisplayName("The R4 shim carries the hardware entropy provider for random UUIDs")
    void shouldCarryTheR4HardwareEntropyProviderInTheShim() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    java.util.UUID value = java.util.UUID.randomUUID();
                    int version = value.version();
                    int variant = value.variant();
                }
                """);

        // When & Then
        assertThat(result.runtimeShim()).contains(
                "juno_uuid_random", "HW_SCE_McuSpecificInit", "HW_SCE_GenerateRandomNumberSub");
    }

    @Test
    @DisplayName("Rejects random UUIDs on the classic UNO, which has no hardware entropy source")
    void shouldRejectRandomUuidOnTheClassicUnoWithoutAHardwareEntropySource() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR3;
                import io.github.jabrena.juno.annotations.Board;
                import java.util.UUID;
                @Board(ArduinoUnoR3.class)
                public final class Tasks {
                    public static void main() {
                        UUID value = UUID.randomUUID();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Tasks", source, "25");

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("cryptographic entropy source");
    }

    @Test
    @DisplayName("Explicit port-noise sampling is lowered without being treated as secure entropy")
    void shouldLowerExplicitPortNoiseSamplingWithoutTreatingItAsSecureEntropy() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    int seed = new io.github.jabrena.juno.api.hardware.io.gpio.PortNoise().collectSeed(14, 2, 64);
                    io.github.jabrena.juno.api.util.Random.seed(seed);
                }
                """);

        // When & Then
        assertThat(result.assembly()).contains("bl analogRead", "bl digitalRead", "bl micros", "juno_random_seed");
    }

    @Test
    @DisplayName("Port-noise sampling does not pull in the UUID random-bytes helper")
    void shouldNotPullInTheUuidRandomBytesHelperForPortNoise() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    int seed = new io.github.jabrena.juno.api.hardware.io.gpio.PortNoise().collectSeed(14, 2, 64);
                    io.github.jabrena.juno.api.util.Random.seed(seed);
                }
                """);

        // When & Then
        assertThat(result.runtimeShim()).doesNotContain("juno_uuid_random_bytes");
    }

    @Test
    @DisplayName("Compiles network byte conversion through the public API")
    void shouldCompileNetworkByteConversionThroughThePublicApi() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    byte[] bytes = new byte[20];
                    java.util.UUID original = new java.util.UUID(0x123456789abcdef0L, 0x0fedcba987654321L);
                    io.github.jabrena.juno.api.util.UuidCodec.toBytes(original, bytes, 2);
                    java.util.UUID copy = io.github.jabrena.juno.api.util.UuidCodec.fromBytes(bytes, 2);
                    boolean equal = original.equals(copy);
                }
                """);

        // When & Then
        assertThat(result.assembly()).contains(
                "juno_uuid_new", "juno_uuid_get_most_significant_bits",
                "juno_uuid_get_least_significant_bits", "juno_uuid_equals");
    }

    @Test
    @DisplayName("A UUID can cross method and field boundaries")
    void shouldLetAUuidCrossMethodAndFieldBoundaries() throws Exception {
        // Given
        CompilationResult result = compile("""
                private static java.util.UUID shared;
                private final java.util.UUID value;

                private Tasks(java.util.UUID value) {
                    this.value = value;
                }

                private java.util.UUID value() {
                    return value;
                }

                private static java.util.UUID identity(java.util.UUID value) {
                    return value;
                }

                public static void main() {
                    shared = new java.util.UUID(1L, 2L);
                    java.util.UUID result = identity(new Tasks(shared).value());
                    boolean equal = result.equals(shared);
                }
                """);

        // When & Then
        assertThat(result.assembly()).contains("juno_uuid_new", "juno_uuid_equals");
    }

    private CompilationResult compile(String members) throws Exception {
        String source = """
                package demo;
                public final class Tasks {
                %s
                }
                """.formatted(members);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Tasks", source, "25");
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks");
    }
}
