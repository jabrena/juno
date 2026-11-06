package io.github.jabrena.juno.hardware.device.ir;

/** Configures one of the two low-level infrared carrier implementations. */
public final class InfraredDriver {
    /** The supported infrared carrier implementations. */
    public enum Type {
        /** The original software-carrier driver used by HX modules. */
        HX(0),
        /** The hardware-carrier driver used by Grove modules on ESP32 boards. */
        GROVE(1);

        private final int code;

        Type(int code) {
            this.code = code;
        }
    }

    /** Configures both endpoints of the shared infrared transport. */
    public InfraredDriver(Type type, int receivePin, int transmitPin, int baud) {
        if (type.code == 1) {
            Infrared.beginGrove(receivePin, transmitPin, baud);
        } else {
            Infrared.beginHX(receivePin, transmitPin, baud);
        }
    }
}
