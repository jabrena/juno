package io.github.jabrena.juno;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The deliberately bounded {@code java.util.UUID} subset lowered onto the runtime shim. */
class UuidTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void constructsUuidAndReadsBothHalves() throws Exception {
        CompilationResult result = compile("""
                public static void main() {
                    java.util.UUID value = new java.util.UUID(0x123456789abcdef0L, 0x0fedcba987654321L);
                    long most = value.getMostSignificantBits();
                    long least = value.getLeastSignificantBits();
                }
                """);

        assertThat(result.assembly()).contains(
                "bl juno_uuid_new", "bl juno_uuid_get_most_significant_bits",
                "bl juno_uuid_get_least_significant_bits");
        assertThat(result.runtimeShim()).contains(
                "juno_uuid_new", "juno_uuid_get_most_significant_bits",
                "juno_uuid_get_least_significant_bits");
    }

    @Test
    void lowersValueSemanticsAndCanonicalText() throws Exception {
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

        assertThat(result.assembly()).contains(
                "bl juno_uuid_from_string", "bl juno_uuid_version", "bl juno_uuid_variant",
                "bl juno_uuid_equals", "bl juno_uuid_compare_to", "bl juno_uuid_hash_code",
                "bl juno_uuid_to_string", "bl juno_throw_pending");
        assertThat(result.runtimeShim()).contains(
                "juno_uuid_from_string", "juno_uuid_version", "juno_uuid_variant",
                "juno_uuid_equals", "juno_uuid_compare_to", "juno_uuid_hash_code",
                "juno_uuid_to_string", "java.lang.IllegalArgumentException");
    }

    @Test
    void lowersRandomUuidThroughTheR4HardwareEntropyProvider() throws Exception {
        CompilationResult result = compile("""
                public static void main() {
                    java.util.UUID value = java.util.UUID.randomUUID();
                    int version = value.version();
                    int variant = value.variant();
                }
                """);

        assertThat(result.assembly()).contains("bl juno_uuid_random");
        assertThat(result.runtimeShim()).contains(
                "juno_uuid_random", "HW_SCE_McuSpecificInit", "HW_SCE_GenerateRandomNumberSub");
    }

    @Test
    void rejectsRandomUuidOnTheClassicUnoWithoutAHardwareEntropySource() throws Exception {
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

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("cryptographic entropy source");
    }

    @Test
    void lowersExplicitPortNoiseSamplingWithoutTreatingItAsSecureEntropy() throws Exception {
        CompilationResult result = compile("""
                public static void main() {
                    int seed = io.github.jabrena.juno.api.io.gpio.PortNoise.collectSeed(14, 2, 64);
                    io.github.jabrena.juno.api.util.Random.seed(seed);
                }
                """);

        assertThat(result.assembly()).contains("bl analogRead", "bl digitalRead", "bl micros", "juno_random_seed");
        assertThat(result.runtimeShim()).doesNotContain("juno_uuid_random_bytes");
    }

    @Test
    void compilesNetworkByteConversionThroughThePublicApi() throws Exception {
        CompilationResult result = compile("""
                public static void main() {
                    byte[] bytes = new byte[20];
                    java.util.UUID original = new java.util.UUID(0x123456789abcdef0L, 0x0fedcba987654321L);
                    io.github.jabrena.juno.api.util.UuidCodec.toBytes(original, bytes, 2);
                    java.util.UUID copy = io.github.jabrena.juno.api.util.UuidCodec.fromBytes(bytes, 2);
                    boolean equal = original.equals(copy);
                }
                """);

        assertThat(result.assembly()).contains(
                "juno_uuid_new", "juno_uuid_get_most_significant_bits",
                "juno_uuid_get_least_significant_bits", "juno_uuid_equals");
    }

    @Test
    void uuidCanCrossMethodAndFieldBoundaries() throws Exception {
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
