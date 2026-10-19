package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.CompileException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Local date-time endpoint configuration")
class LocalDateTimeEndpointTest {
    @Test
    @DisplayName("Defaults the host to the TimeAPI service")
    void shouldDefaultToTheTimeApiHost() {
        // Given
        LocalDateTimeEndpoint endpoint = LocalDateTimeEndpoint.from(Map.of());

        // When & Then
        assertThat(endpoint.host()).isEqualTo("timeapi.io");
    }

    @Test
    @DisplayName("Defaults the port to 443")
    void shouldDefaultToPort443() {
        // Given
        LocalDateTimeEndpoint endpoint = LocalDateTimeEndpoint.from(Map.of());

        // When & Then
        assertThat(endpoint.port()).isEqualTo(443);
    }

    @Test
    @DisplayName("Defaults the path to the UTC current-time query")
    void shouldDefaultToTheUtcPath() {
        // Given
        LocalDateTimeEndpoint endpoint = LocalDateTimeEndpoint.from(Map.of());

        // When & Then
        assertThat(endpoint.path()).isEqualTo("/api/Time/current/zone?timeZone=UTC");
    }

    @Test
    @DisplayName("Defaults the JSON path to dateTime")
    void shouldDefaultToTheDateTimeJsonPath() {
        // Given
        LocalDateTimeEndpoint endpoint = LocalDateTimeEndpoint.from(Map.of());

        // When & Then
        assertThat(endpoint.jsonPath()).isEqualTo("dateTime");
    }

    @Test
    @DisplayName("Reads the host and port from a custom HTTPS URL")
    void shouldReadTheHostAndPortFromACustomHttpsUrl() {
        // Given
        Map<String, String> environment = Map.of(
                "JUNO_LOCAL_DATE_TIME_URL", "https://clock.example:8443/v2/now?zone=UTC",
                "JUNO_LOCAL_DATE_TIME_JSON_PATH", "data.current.dateTime");

        // When
        LocalDateTimeEndpoint endpoint = LocalDateTimeEndpoint.from(environment);

        // Then
        assertThat(endpoint.host()).isEqualTo("clock.example");
        assertThat(endpoint.port()).isEqualTo(8443);
    }

    @Test
    @DisplayName("Reads the path and nested JSON path from the configuration")
    void shouldReadThePathAndNestedJsonPathFromTheConfiguration() {
        // Given
        Map<String, String> environment = Map.of(
                "JUNO_LOCAL_DATE_TIME_URL", "https://clock.example:8443/v2/now?zone=UTC",
                "JUNO_LOCAL_DATE_TIME_JSON_PATH", "data.current.dateTime");

        // When
        LocalDateTimeEndpoint endpoint = LocalDateTimeEndpoint.from(environment);

        // Then
        assertThat(endpoint.path()).isEqualTo("/v2/now?zone=UTC");
        assertThat(endpoint.jsonPath()).isEqualTo("data.current.dateTime");
    }

    @Test
    @DisplayName("Emits the custom endpoint into the generated helpers")
    void shouldEmitTheCustomEndpointIntoTheGeneratedHelpers() {
        // Given
        Map<String, String> environment = Map.of(
                "JUNO_LOCAL_DATE_TIME_URL", "https://clock.example:8443/v2/now?zone=UTC",
                "JUNO_LOCAL_DATE_TIME_JSON_PATH", "data.current.dateTime");

        // When
        String helpers = new LocalDateTimeRuntime(environment).helpers(true);

        // Then
        assertThat(helpers).contains(
                "juno_https_get(\"clock.example\", 8443,",
                "\"/v2/now?zone=UTC\"",
                "\"data.current.dateTime\"");
    }

    @Test
    @DisplayName("Rejects a non-HTTPS URL")
    void shouldRejectANonHttpsUrl() {
        // When & Then
        assertThatThrownBy(() -> LocalDateTimeEndpoint.from(Map.of(
                "JUNO_LOCAL_DATE_TIME_URL", "http://clock.example/now")))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("JUNO_LOCAL_DATE_TIME_URL")
                .hasMessageContaining("HTTPS");
    }

    @Test
    @DisplayName("Rejects an ambiguous JSON path")
    void shouldRejectAnAmbiguousJsonPath() {
        // When & Then
        assertThatThrownBy(() -> LocalDateTimeEndpoint.from(Map.of(
                "JUNO_LOCAL_DATE_TIME_JSON_PATH", "data..dateTime")))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("JUNO_LOCAL_DATE_TIME_JSON_PATH")
                .hasMessageContaining("dot-separated");
    }
}
