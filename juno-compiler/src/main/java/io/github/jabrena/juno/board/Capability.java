package io.github.jabrena.juno.board;

/**
 * An optional on-board feature a Juno program can depend on. The linker rejects a program that uses
 * one on any of its declared {@link Board}s that lacks it, so the backend only ever generates code
 * for features the target actually has.
 */
public enum Capability {
    SECURE_ENTROPY("UUID.randomUUID") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " has no board-native cryptographic entropy source";
        }
    },
    LED_MATRIX("LedMatrix") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " has no onboard LED matrix";
        }
    },
    M5_DISPLAY("M5StackCardputer.getDisplay") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " has no M5Stack Cardputer display";
        }
    },
    M5_KEYBOARD("M5StackCardputer.getKeyboard") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " has no M5Stack Cardputer keyboard";
        }
    },
    M5_AUDIO("M5StackCardputer.getAudio") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " has no M5Stack Cardputer speaker";
        }
    },
    WIFI("Wifi") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " has no onboard WiFi module";
        }
    },
    WIFI_ACCESS_POINT("Wifi.beginAP") {
        @Override
        public String unsupportedReason(Board board) {
            if (board.supports(WIFI)) {
                return board.displayName() + "'s WiFi library cannot start an access point; join a network with "
                        + "Wifi.begin instead";
            }
            return WIFI.unsupportedReason(board);
        }
    },
    ONBOARD_WIFI("Arduino.getWifi") {
        @Override
        public String unsupportedReason(Board board) {
            if (board.supports(WIFI)) {
                return board.displayName() + " has no onboard Wi-Fi radio; stack the Arduino WiFi Shield and get its "
                        + "Wi-Fi with new ArduinoWifiShield().getWifi() instead";
            }
            return WIFI.unsupportedReason(board);
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
    TLS_EMAIL("Smtp.sendTls and Pop3Client") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " does not provide the TLS email clients";
        }
    },
    HTTP_SERVER("HttpServer") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " does not provide a TCP server stack";
        }
    },
    SMTP_STARTTLS("Smtp.send (STARTTLS)") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " cannot upgrade a plain connection to TLS in place (its TLS client only "
                    + "speaks TLS from the first byte); use Smtp.sendTls with implicit TLS, usually port 465";
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
            if (board == Board.UNO_Q) {
                return "UNO Q's Zephyr firmware does not enable its watchdog subsystem";
            }
            return board.displayName() + "'s " + board.core().displayName() + " core has no WDT library";
        }
    },
    USB_HID("Mouse") {
        @Override
        public String unsupportedReason(Board board) {
            if (board == Board.UNO_Q) {
                return "UNO Q's Zephyr core has no HID.h, which the Mouse library needs";
            }
            return board.displayName() + " has no native USB port to act as a HID device";
        }
    },
    STRUCTURED_CONCURRENCY("StructuredTaskScope, ScopedValue and locks") {
        @Override
        public String unsupportedReason(Board board) {
            return board.displayName() + " has no room for per-task stacks and the thread scheduler";
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
