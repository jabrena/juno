package io.github.jabrena.juno.backend.runtime;

import java.util.Map;

/**
 * Runtime implementation of Juno's supported {@code java.time.LocalDateTime} operations. {@code LocalDateTime.now()}
 * synchronizes UTC over SNTP with {@link SntpServer} on its own UDP socket, then advances it with {@code millis()}.
 */
final class LocalDateTimeRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();
    private final Map<String, String> environment;

    LocalDateTimeRuntime() {
        this(System.getenv());
    }

    LocalDateTimeRuntime(Map<String, String> environment) {
        this.environment = Map.copyOf(environment);
    }

    /** {@code sntpUdpDeclaration} declares the core's UDP socket {@code juno_sntp_udp}, used by the internet clock. */
    String helpers(boolean internetClock, boolean configurable, String sntpUdpDeclaration) {
        if (!internetClock && !configurable) {
            return runtimeSource.load("common/local-date-time.cpp")
                    .replace("${JUNO_LOCAL_DATE_TIME_CONFIGURATION}", "")
                    .replace("${JUNO_LOCAL_DATE_TIME_NOW}", "");
        }
        SntpServer server = SntpServer.from(environment);
        String configuration = configurable ? configurationHelpers(server) : "";
        return runtimeSource.load("common/local-date-time.cpp")
                .replace("${JUNO_LOCAL_DATE_TIME_CONFIGURATION}", configuration)
                .replace("${JUNO_LOCAL_DATE_TIME_NOW}",
                        internetClock ? nowHelpers(server, configurable, sntpUdpDeclaration) : "");
    }

    private String nowHelpers(SntpServer server, boolean configurable, String sntpUdpDeclaration) {
        String fetch = runtimeSource.load("common/local-date-time-sntp.cpp")
                .replace("${JUNO_SNTP_UDP_DECLARATION}", sntpUdpDeclaration)
                .replace("${JUNO_SNTP_HOST}", configurable ? "juno_local_date_time_host" : server.cppHost())
                .replace("${JUNO_SNTP_PORT}",
                        configurable ? "juno_local_date_time_port" : Integer.toString(server.port()));
        return runtimeSource.load("common/local-date-time-now.cpp").replace("${JUNO_LOCAL_DATE_TIME_FETCH}", fetch);
    }

    private String configurationHelpers(SntpServer server) {
        return runtimeSource.load("common/local-date-time-config.cpp")
                .replace("${JUNO_SNTP_DEFAULT_HOST}", server.cppHost())
                .replace("${JUNO_SNTP_DEFAULT_PORT}", Integer.toString(server.port()));
    }
}
