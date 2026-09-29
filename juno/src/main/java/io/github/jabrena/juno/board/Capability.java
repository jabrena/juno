package io.github.jabrena.juno.board;

/**
 * An optional on-board feature a Juno program can depend on. The linker rejects a program that uses
 * one on any of its declared {@link Board}s that lacks it, so the backend only ever generates code
 * for features the target actually has.
 */
public enum Capability {
    LED_MATRIX("LedMatrix") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " has no onboard LED matrix";
        }
    },
    WIFI("Wifi") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " has no onboard WiFi module";
        }
    },
    WIFI_S3_NETWORKING("HTTP/HTTPS/email networking") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " does not provide the WiFiS3 networking stack";
        }
    },
    WATCHDOG("@Watchdog") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + "'s " + board.core().displayName() + " core has no WDT library";
        }
    };

    private final String apiName;

    Capability(String apiName) {
        this.apiName = apiName;
    }

    /** The Java-facing API that needs this capability, as named in diagnostics (e.g. {@code "LedMatrix"}). */
    public String apiName() {
        return apiName;
    }

    /** Why {@code board}, which does not support this capability, can't provide it. */
    public abstract String unsupportedReason(Board board);
}
