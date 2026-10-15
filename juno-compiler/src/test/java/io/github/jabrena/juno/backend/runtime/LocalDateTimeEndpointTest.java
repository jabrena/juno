package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.CompileException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDateTimeEndpointTest {
    @Test
    void suppliesTimeApiDefaults() {
        LocalDateTimeEndpoint endpoint = LocalDateTimeEndpoint.from(Map.of());

        assertThat(endpoint.host()).isEqualTo("timeapi.io");
        assertThat(endpoint.port()).isEqualTo(443);
        assertThat(endpoint.path()).isEqualTo("/api/Time/current/zone?timeZone=UTC");
        assertThat(endpoint.jsonPath()).isEqualTo("dateTime");
    }

    @Test
    void acceptsACustomHttpsUrlAndNestedJsonPath() {
        Map<String, String> environment = Map.of(
                "JUNO_LOCAL_DATE_TIME_URL", "https://clock.example:8443/v2/now?zone=UTC",
                "JUNO_LOCAL_DATE_TIME_JSON_PATH", "data.current.dateTime");
        LocalDateTimeEndpoint endpoint = LocalDateTimeEndpoint.from(environment);

        assertThat(endpoint.host()).isEqualTo("clock.example");
        assertThat(endpoint.port()).isEqualTo(8443);
        assertThat(endpoint.path()).isEqualTo("/v2/now?zone=UTC");
        assertThat(endpoint.jsonPath()).isEqualTo("data.current.dateTime");
        assertThat(new LocalDateTimeRuntime(environment).helpers(true)).contains(
                "juno_https_get(\"clock.example\", 8443,",
                "\"/v2/now?zone=UTC\"",
                "\"data.current.dateTime\"");
    }

    @Test
    void rejectsUnsafeOrAmbiguousConfiguration() {
        assertThatThrownBy(() -> LocalDateTimeEndpoint.from(Map.of(
                "JUNO_LOCAL_DATE_TIME_URL", "http://clock.example/now")))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("JUNO_LOCAL_DATE_TIME_URL")
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> LocalDateTimeEndpoint.from(Map.of(
                "JUNO_LOCAL_DATE_TIME_JSON_PATH", "data..dateTime")))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("JUNO_LOCAL_DATE_TIME_JSON_PATH")
                .hasMessageContaining("dot-separated");
    }
}
