package io.github.jabrena.juno.backend.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Local date-time runtime helpers, compiled and run as C++")
class LocalDateTimeRuntimeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Synchronizes over SNTP: sends a client request and turns the server's 1900-based seconds into UTC")
    void shouldSynchronizeOverSntp() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        long ntpSeconds = Instant.parse("2024-02-29T12:34:56Z").getEpochSecond() + 2_208_988_800L;
        String runtime = new LocalDateTimeRuntime(Map.of("JUNO_SNTP_HOST", "time.example", "JUNO_SNTP_PORT", "1123"))
                .helpers(true, false, "static FakeUdp juno_sntp_udp;");
        String driver = """
                #include <cstdint>
                #include <cstdlib>
                #include <cstring>

                static uint8_t juno_arena[4096];
                static uint32_t juno_arena_used = 0;
                static uint32_t juno_headers[1024];
                [[noreturn]] static void juno_panic() { std::abort(); }
                static void* juno_alloc(uint32_t size, uint32_t alignment) {
                  uint32_t start = (juno_arena_used + 4u + alignment - 1u) & ~(alignment - 1u);
                  juno_arena_used = start + size;
                  return juno_arena + start;
                }
                static bool juno_gc_is_block_start(uint32_t) { return true; }
                static uint32_t* juno_gc_header_at(uint32_t offset) { return juno_headers + offset / 4u; }
                static uint32_t juno_gc_block_size(uint32_t) { return 64u; }

                static uint32_t fakeMillis = 0;
                static unsigned long millis() { return fakeMillis; }
                static void delay(unsigned long) { fakeMillis += 10; }
                extern "C" int32_t juno_wifi_status() { return 3; }

                static const uint32_t serverSeconds = %dul;
                class FakeUdp {
                 public:
                  uint8_t request[48] = {0};
                  const char* host = nullptr;
                  uint16_t port = 0;
                  bool open = false;
                  int begin(uint16_t) { open = true; return 1; }
                  int beginPacket(const char* name, uint16_t remotePort) { host = name; port = remotePort; return 1; }
                  size_t write(const uint8_t* data, size_t size) { std::memcpy(request, data, size); return size; }
                  int endPacket() { return 1; }
                  int parsePacket() { return 48; }
                  int read(uint8_t* data, size_t size) {
                    std::memset(data, 0, size);
                    data[0] = 0x24;  // version 4, mode 4 (server)
                    data[1] = 2;     // stratum 2
                    data[40] = static_cast<uint8_t>(serverSeconds >> 24);
                    data[41] = static_cast<uint8_t>(serverSeconds >> 16);
                    data[42] = static_cast<uint8_t>(serverSeconds >> 8);
                    data[43] = static_cast<uint8_t>(serverSeconds);
                    return static_cast<int>(size);
                  }
                  void stop() { open = false; }
                };

                %s

                int main() {
                  JunoLocalDateTime fetched;
                  if (!juno_local_date_time_fetch(&fetched)) return 1;
                  if (juno_sntp_udp.request[0] != 0x1B) return 2;
                  if (std::strcmp(juno_sntp_udp.host, "time.example") != 0 || juno_sntp_udp.port != 1123) return 3;
                  if (juno_sntp_udp.open) return 4;
                  if (fetched.year != 2024 || fetched.month != 2 || fetched.day != 29
                      || fetched.hour != 12 || fetched.minute != 34 || fetched.second != 56) return 5;
                  return 0;
                }
                """.formatted(ntpSeconds, runtime);

        assertThat(compileAndRun(compiler, "local-date-time-sntp-driver", driver)).isEqualTo(0);
    }

    private int compileAndRun(String compiler, String name, String driver) throws Exception {
        Path source = temporaryDirectory.resolve(name + ".cpp");
        Path executable = temporaryDirectory.resolve(name);
        Files.writeString(source, driver, StandardCharsets.UTF_8);

        Process compile = new ProcessBuilder(compiler, "-std=c++17", "-O1", "-w", "-o",
                executable.toString(), source.toString()).redirectErrorStream(true).start();
        assertThat(compile.waitFor(20, TimeUnit.SECONDS)).isTrue();
        String diagnostics = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(compile.exitValue()).as(diagnostics).isEqualTo(0);

        Process run = new ProcessBuilder(executable.toString()).redirectErrorStream(true).start();
        assertThat(run.waitFor(10, TimeUnit.SECONDS)).isTrue();
        return run.exitValue();
    }

    @Test
    @DisplayName("Emits JUNO_SNTP_HOST and JUNO_SNTP_PORT into the SNTP request")
    void shouldEmitTheConfiguredSntpServerIntoTheRequest() {
        // When
        String helpers = new LocalDateTimeRuntime(Map.of("JUNO_SNTP_HOST", "time.google.com", "JUNO_SNTP_PORT", "1123"))
                .helpers(true, false, "static WiFiUDP juno_sntp_udp;");

        // Then
        assertThat(helpers).contains(
                "static WiFiUDP juno_sntp_udp;",
                "juno_sntp_udp.beginPacket(\"time.google.com\", static_cast<uint16_t>(1123))");
    }

    @Test
    @DisplayName("An SD-card override starts from the build's SNTP server and reads sntp.host and sntp.port")
    void shouldStartTheSdCardOverrideFromTheBuildsSntpServer() {
        // When
        String helpers = new LocalDateTimeRuntime(Map.of()).helpers(true, true, "static WiFiUDP juno_sntp_udp;");

        // Then
        assertThat(helpers).contains(
                "static char juno_local_date_time_host[64] = \"pool.ntp.org\";",
                "static int32_t juno_local_date_time_port = 123;",
                "\"sntp.host\"",
                "\"sntp.port\"",
                "juno_sntp_udp.beginPacket(juno_local_date_time_host,")
                .doesNotContain("juno_https_get", "time.api.url");
    }

    private String availableCppCompiler() {
        for (String candidate : new String[]{"clang++", "g++"}) {
            try {
                Process process = new ProcessBuilder(candidate, "--version").start();
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    return candidate;
                }
            } catch (IOException | InterruptedException ignored) {
                // Try the next compiler.
            }
        }
        return null;
    }
}
