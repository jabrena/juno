package io.github.jabrena.juno.maven;

final class ArduinoCliException extends RuntimeException {
    ArduinoCliException(String message) {
        super(message);
    }

    ArduinoCliException(String message, Throwable cause) {
        super(message, cause);
    }
}
