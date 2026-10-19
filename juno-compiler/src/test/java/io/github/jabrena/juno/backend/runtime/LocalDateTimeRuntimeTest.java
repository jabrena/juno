package io.github.jabrena.juno.backend.runtime;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
@DisplayName("Local date-time runtime helpers, compiled and run as C++")
class LocalDateTimeRuntimeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Returns the minimum while offline, then advances the synchronized clock across a leap day")
    void shouldReturnMinimumWhileOfflineThenAdvanceTheSynchronizedClockAcrossLeapDay() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String runtime = new LocalDateTimeRuntime(Map.of(
                "JUNO_LOCAL_DATE_TIME_JSON_PATH", "data.current.dateTime")).helpers(true);
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
                static int32_t fakeWifiStatus = 0;
                static int32_t requests = 0;
                static const char* response =
                    "{\\\"data\\\":{\\\"current\\\":{"
                    "\\\"dateTime\\\":\\\"2024-02-28T23:59:59.1234567\\\"}}}";
                static unsigned long millis() { return fakeMillis; }
                extern "C" int32_t juno_wifi_status() { return fakeWifiStatus; }
                extern "C" int32_t juno_https_get(const char*, int32_t, const char*, uint8_t* body,
                    int32_t bodyCapacity, uint8_t*, int32_t, int32_t* status) {
                  requests++;
                  int32_t length = static_cast<int32_t>(std::strlen(response));
                  if (length > bodyCapacity) length = bodyCapacity;
                  std::memcpy(body, response, static_cast<size_t>(length));
                  status[0] = 200;
                  status[1] = 0;
                  return length;
                }

                %s

                int main() {
                  if (juno_local_date_time_now() != juno_local_date_time_min()) return 1;
                  if (requests != 0) return 2;
                  fakeWifiStatus = 3;
                  juno_local_date_time_now();
                  if (requests != 1) return 3;
                  JunoLocalDateTime fetched;
                  if (!juno_local_date_time_fetch(&fetched)) return 4;
                  if (fetched.year != 2024 || fetched.month != 2 || fetched.day != 28
                      || fetched.hour != 23 || fetched.minute != 59 || fetched.second != 59) return 5;
                  JunoLocalDateTime advanced = juno_local_date_time_advance(fetched, 2u);
                  if (advanced.month != 2 || advanced.day != 29 || advanced.hour != 0
                      || advanced.minute != 0 || advanced.second != 1) return 6;
                  if (requests != 2) return 7;
                  return 0;
                }
                """.formatted(runtime);
        Path source = temporaryDirectory.resolve("local-date-time-driver.cpp");
        Path executable = temporaryDirectory.resolve("local-date-time-driver");
        Files.writeString(source, driver, StandardCharsets.UTF_8);

        Process compile = new ProcessBuilder(compiler, "-std=c++17", "-O1", "-w", "-o",
                executable.toString(), source.toString()).redirectErrorStream(true).start();
        assertThat(compile.waitFor(20, TimeUnit.SECONDS)).isTrue();
        String diagnostics = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(compile.exitValue()).as(diagnostics).isEqualTo(0);

        Process run = new ProcessBuilder(executable.toString()).redirectErrorStream(true).start();
        assertThat(run.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(run.exitValue()).isEqualTo(0);
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
