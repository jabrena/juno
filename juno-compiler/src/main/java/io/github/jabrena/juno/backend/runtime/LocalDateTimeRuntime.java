package io.github.jabrena.juno.backend.runtime;

import java.util.Map;

/** Runtime implementation of Juno's supported {@code java.time.LocalDateTime} operations. */
final class LocalDateTimeRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();
    private final Map<String, String> environment;

    LocalDateTimeRuntime() {
        this(System.getenv());
    }

    LocalDateTimeRuntime(Map<String, String> environment) {
        this.environment = Map.copyOf(environment);
    }

    String helpers(boolean internetClock) {
        return helpers(internetClock, false);
    }

    String helpers(boolean internetClock, boolean configurable) {
        if (!internetClock && !configurable) {
            return runtimeSource.load("common/local-date-time.cpp")
                    .replace("${JUNO_LOCAL_DATE_TIME_CONFIGURATION}", "")
                    .replace("${JUNO_LOCAL_DATE_TIME_NOW}", "");
        }
        LocalDateTimeEndpoint endpoint = LocalDateTimeEndpoint.from(environment);
        String configuration = configurable ? configurationHelpers(endpoint) : "";
        return runtimeSource.load("common/local-date-time.cpp")
                .replace("${JUNO_LOCAL_DATE_TIME_CONFIGURATION}", configuration)
                .replace("${JUNO_LOCAL_DATE_TIME_NOW}",
                        internetClock ? nowHelpers(endpoint, configurable) : "");
    }

    private String nowHelpers(LocalDateTimeEndpoint endpoint, boolean configurable) {
        return runtimeSource.load("common/local-date-time-now.cpp")
                .replace("${JUNO_LOCAL_DATE_TIME_HOST}",
                        configurable ? "juno_local_date_time_host" : endpoint.cppHost())
                .replace("${JUNO_LOCAL_DATE_TIME_PORT}",
                        configurable ? "juno_local_date_time_port" : Integer.toString(endpoint.port()))
                .replace("${JUNO_LOCAL_DATE_TIME_PATH}",
                        configurable ? "juno_local_date_time_path" : endpoint.cppPath())
                .replace("${JUNO_LOCAL_DATE_TIME_JSON_PATH}",
                        configurable ? "juno_local_date_time_json_path" : endpoint.cppJsonPath());
    }

    private String configurationHelpers(LocalDateTimeEndpoint endpoint) {
        return runtimeSource.load("common/local-date-time-config.cpp")
                .replace("${JUNO_LOCAL_DATE_TIME_DEFAULT_HOST}", endpoint.cppHost())
                .replace("${JUNO_LOCAL_DATE_TIME_DEFAULT_PORT}", Integer.toString(endpoint.port()))
                .replace("${JUNO_LOCAL_DATE_TIME_DEFAULT_PATH}", endpoint.cppPath())
                .replace("${JUNO_LOCAL_DATE_TIME_DEFAULT_JSON_PATH}", endpoint.cppJsonPath());
    }
}
