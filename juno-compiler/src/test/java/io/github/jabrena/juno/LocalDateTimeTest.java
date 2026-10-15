package io.github.jabrena.juno;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The deliberately bounded {@code java.time.LocalDateTime} subset lowered onto the runtime shim. */
class LocalDateTimeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void lowersInternetBackedNowAndMakesTheOfflineMinimumObservable() throws Exception {
        CompilationResult result = compile("""
                public static void main() {
                    io.github.jabrena.juno.api.net.Wifi.begin("ssid", "password");
                    java.time.LocalDateTime value = java.time.LocalDateTime.now();
                    boolean offline = value.equals(java.time.LocalDateTime.MIN);
                    int year = value.getYear();
                    int month = value.getMonthValue();
                    int day = value.getDayOfMonth();
                    int hour = value.getHour();
                    int minute = value.getMinute();
                    int second = value.getSecond();
                    String text = value.toString();
                }
                """);

        assertThat(result.assembly()).contains(
                "bl juno_local_date_time_now",
                "bl juno_local_date_time_min",
                "bl juno_local_date_time_equals",
                "bl juno_local_date_time_get_year",
                "bl juno_local_date_time_get_month_value",
                "bl juno_local_date_time_get_day_of_month",
                "bl juno_local_date_time_get_hour",
                "bl juno_local_date_time_get_minute",
                "bl juno_local_date_time_get_second",
                "bl juno_local_date_time_to_string");
        assertThat(result.runtimeShim()).contains(
                "timeapi.io",
                "/api/Time/current/zone?timeZone=UTC",
                "juno_wifi_status() != 3",
                "juno_https_get",
                "juno_local_date_time_now");
        assertThat(result.assembly().indexOf("bl juno_wifi_begin"))
                .isLessThan(result.assembly().indexOf("bl juno_local_date_time_now"));
    }

    @Test
    void localDateTimeCanCrossMethodAndFieldBoundaries() throws Exception {
        CompilationResult result = compile("""
                private static java.time.LocalDateTime shared;

                private static java.time.LocalDateTime identity(java.time.LocalDateTime value) {
                    return value;
                }

                public static void main() {
                    shared = java.time.LocalDateTime.now();
                    boolean offline = identity(shared).equals(java.time.LocalDateTime.MIN);
                }
                """);

        assertThat(result.assembly()).contains("juno_local_date_time_now", "juno_local_date_time_min");
    }

    @Test
    void acceptsAnApplicationPropertiesOverrideLoadedByTheApplication() throws Exception {
        CompilationResult result = compile("""
                public static void main() {
                    java.util.Properties properties = new java.util.Properties();
                    boolean configured = io.github.jabrena.juno.api.InternetClock.configure(properties);
                    java.time.LocalDateTime value = java.time.LocalDateTime.now();
                }
                """);

        assertThat(result.assembly()).contains(
                "juno_local_date_time_configure", "juno_local_date_time_now");
        assertThat(result.runtimeShim()).contains(
                "time.api.url", "time.api.jsonPath", "juno_properties_get");
    }

    @Test
    void rejectsMethodsOutsideTheBoundedSubset() throws Exception {
        assertThatThrownBy(() -> compile("""
                public static void main() {
                    int nano = java.time.LocalDateTime.now().getNano();
                }
                """))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("LocalDateTime subset does not support")
                .hasMessageContaining("getNano");
    }

    @Test
    void rejectsInternetClockOnABoardWithoutHttps() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR3;
                import io.github.jabrena.juno.annotations.Board;
                @Board(ArduinoUnoR3.class)
                public final class OfflineClock {
                    public static void main() {
                        java.time.LocalDateTime value = java.time.LocalDateTime.now();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.OfflineClock", source, "25");

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.OfflineClock"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("HTTPS networking");
    }

    @Test
    void supportsTheUnoWifiRev2ThroughAvrThunks() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoWiFiRev2;
                import io.github.jabrena.juno.annotations.Board;
                import java.time.LocalDateTime;
                @Board(ArduinoUnoWiFiRev2.class)
                public final class WifiRev2Clock {
                    public static void main() {
                        LocalDateTime value = LocalDateTime.now();
                        int second = value.getSecond();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WifiRev2Clock", source, "25");

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WifiRev2Clock");

        assertThat(result.assembly()).contains(
                "call juno_avr_thunk_juno_local_date_time_now",
                "call juno_avr_thunk_juno_local_date_time_get_second");
        assertThat(result.runtimeShim()).contains("#include <WiFiNINA.h>", "timeapi.io");
    }

    private CompilationResult compile(String members) throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
                import io.github.jabrena.juno.annotations.Board;
                @Board(ArduinoUnoR4WiFi.class)
                public final class Tasks {
                %s
                }
                """.formatted(members);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Tasks", source, "25");
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks");
    }
}
