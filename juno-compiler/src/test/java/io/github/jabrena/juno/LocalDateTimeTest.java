package io.github.jabrena.juno;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The deliberately bounded {@code java.time.LocalDateTime} subset lowered onto the runtime shim. */
@DisplayName("Bounded LocalDateTime subset")
class LocalDateTimeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Lowers the internet-backed now and its calls to the shim")
    void shouldLowerInternetBackedNowAndItsCallsToTheShim() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    io.github.jabrena.juno.hardware.MCU.getWifi().begin("ssid", "password");
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

        // When & Then
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
    }

    @Test
    @DisplayName("The runtime shim declares the default SNTP request on its own socket and the offline check")
    void shouldDeclareTheSntpRequestAndTheOfflineCheckInTheShim() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    io.github.jabrena.juno.hardware.MCU.getWifi().begin("ssid", "password");
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

        // When & Then
        assertThat(result.runtimeShim()).contains(
                "static WiFiUDP juno_sntp_udp;",
                "juno_sntp_udp.beginPacket(\"pool.ntp.org\", static_cast<uint16_t>(123))",
                "juno_wifi_status() != 3",
                "juno_local_date_time_now")
                .doesNotContain("juno_https_get", "timeapi.io");
    }

    @Test
    @DisplayName("Wi-Fi begins before the clock is read")
    void shouldBeginWifiBeforeTheClockIsRead() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    io.github.jabrena.juno.hardware.MCU.getWifi().begin("ssid", "password");
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

        // When & Then
        assertThat(result.assembly().indexOf("bl juno_wifi_begin"))
                .isLessThan(result.assembly().indexOf("bl juno_local_date_time_now"));
    }

    @Test
    @DisplayName("A LocalDateTime can cross method and field boundaries")
    void shouldLetLocalDateTimeCrossMethodAndFieldBoundaries() throws Exception {
        // Given
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

        // When & Then
        assertThat(result.assembly()).contains("juno_local_date_time_now", "juno_local_date_time_min");
    }

    @Test
    @DisplayName("Accepts an application-properties override loaded by the application")
    void shouldAcceptAnApplicationPropertiesOverrideLoadedByTheApplication() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    java.util.Properties properties = new java.util.Properties();
                    boolean configured = io.github.jabrena.juno.hardware.net.udp.SntpClock.configure(properties);
                    java.time.LocalDateTime value = java.time.LocalDateTime.now();
                }
                """);

        // When & Then
        assertThat(result.assembly()).contains(
                "juno_local_date_time_configure", "juno_local_date_time_now");
    }

    @Test
    @DisplayName("The override reads the SNTP host and port properties in the shim")
    void shouldReadTheSntpHostAndPortPropertiesInTheShim() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    java.util.Properties properties = new java.util.Properties();
                    boolean configured = io.github.jabrena.juno.hardware.net.udp.SntpClock.configure(properties);
                    java.time.LocalDateTime value = java.time.LocalDateTime.now();
                }
                """);

        // When & Then
        assertThat(result.runtimeShim()).contains(
                "\"sntp.host\"", "\"sntp.port\"", "juno_properties_get",
                "juno_sntp_udp.beginPacket(juno_local_date_time_host,");
    }

    @Test
    @DisplayName("Rejects methods outside the bounded subset")
    void shouldRejectMethodsOutsideTheBoundedSubset() throws Exception {
        // When & Then
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
    @DisplayName("Rejects the internet clock on a board without Wi-Fi")
    void shouldRejectTheInternetClockOnABoardWithoutWifi() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoNanoEsp32;
                import io.github.jabrena.juno.annotations.Board;
                @Board(ArduinoNanoEsp32.class)
                public final class OfflineClock {
                    public static void main() {
                        java.time.LocalDateTime value = java.time.LocalDateTime.now();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.OfflineClock", source, "25");

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.OfflineClock"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Nano ESP32 has no onboard WiFi module");
    }

    @Test
    @DisplayName("Runs the SNTP clock on the UNO R3 through the WiFi Shield's UDP socket")
    void shouldRunTheSntpClockOnTheUnoR3ThroughTheWifiShield() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR3;
                import io.github.jabrena.juno.annotations.Board;
                @Board(ArduinoUnoR3.class)
                public final class ShieldClock {
                    public static void main() {
                        java.time.LocalDateTime value = java.time.LocalDateTime.now();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.ShieldClock", source, "25");

        // When
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ShieldClock");

        // Then
        assertThat(result.runtimeShim())
                .contains("#include <WiFiUdp.h>", "static JunoShieldUdp juno_sntp_udp;")
                .doesNotContain("juno_https_get");
    }

    @Test
    @DisplayName("Supports the UNO WiFi Rev2 through AVR thunks")
    void shouldSupportTheUnoWifiRev2ThroughAvrThunks() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
                import io.github.jabrena.juno.annotations.Board;
                import java.time.LocalDateTime;
                @Board(ArduinoUnoR3WiFiRev2.class)
                public final class WifiRev2Clock {
                    public static void main() {
                        LocalDateTime value = LocalDateTime.now();
                        int second = value.getSecond();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WifiRev2Clock", source, "25");
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WifiRev2Clock");

        // When & Then
        assertThat(result.assembly()).contains(
                "call juno_avr_thunk_juno_local_date_time_now",
                "call juno_avr_thunk_juno_local_date_time_get_second");
    }

    @Test
    @DisplayName("The UNO WiFi Rev2 shim includes WiFiNINA and the SNTP server")
    void shouldIncludeWifiNinaAndTheTimeApiInTheRev2Shim() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
                import io.github.jabrena.juno.annotations.Board;
                import java.time.LocalDateTime;
                @Board(ArduinoUnoR3WiFiRev2.class)
                public final class WifiRev2Clock {
                    public static void main() {
                        LocalDateTime value = LocalDateTime.now();
                        int second = value.getSecond();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WifiRev2Clock", source, "25");
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WifiRev2Clock");

        // When & Then
        assertThat(result.runtimeShim()).contains("#include <WiFiNINA.h>", "#include <WiFiUdp.h>", "pool.ntp.org");
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
