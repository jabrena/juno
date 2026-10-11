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
    HTTP_CLIENT("HttpClient") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " does not provide a supported plain-HTTP client";
        }
    },
    HTTPS_CLIENT("HTTPS networking") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " does not provide a supported TLS client";
        }
    },
    HTTP_SERVER("HttpServer") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " does not provide a TCP server stack";
        }
    },
    WIFI_S3_NETWORKING("Email networking") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " does not provide the WiFiS3 networking stack";
        }
    },
    BLUETOOTH_LE("PoweredUpHubRemote") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " has no Bluetooth LE adapter reachable through ArduinoBLE";
        }
    },
    WATCHDOG("@Watchdog") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + "'s " + board.core().displayName() + " core has no WDT library";
        }
    },
    USB_HID("Mouse") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " has no native USB port to act as a HID device";
        }
    },
    STRUCTURED_CONCURRENCY("StructuredTaskScope, ScopedValue and locks") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " has no room for per-task stacks and the cooperative scheduler";
        }
    },
    BIG_NUMBERS("java.math") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " has no room for the arbitrary-precision runtime";
        }
    },
    DOUBLE_MATH("double-precision Math functions (sin, cos, tan, asin, acos, atan, atan2, exp, log, log10, pow, cbrt, hypot)") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + "'s " + board.core().displayName()
                    + " toolchain only has single-precision libm, which would silently change the results";
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
