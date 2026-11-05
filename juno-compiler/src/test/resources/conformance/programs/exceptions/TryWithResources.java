package demo;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * try-with-resources: resources close in reverse order of creation, a null resource is never closed, the extended form
 * with catch and finally clauses sees a body failure, and a failure in close() does not replace the body's exception.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class,
        ArduinoNanoEsp32Board.class})
public final class TryWithResources {
    static final class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
            Serial.print("open ");
            Serial.println(name);
        }

        void use(boolean fail) {
            if (fail) {
                throw new IllegalStateException("use failed");
            }
            Serial.print("use ");
            Serial.println(name);
        }

        @Override
        public void close() {
            Serial.print("close ");
            Serial.println(name);
        }
    }

    static final class FailingClose implements AutoCloseable {
        @Override
        public void close() {
            Serial.println("close failing");
            throw new IllegalStateException("close");
        }
    }

    private TryWithResources() {
    }

    public static void main(String[] args) {
        try (Resource first = new Resource("a"); Resource second = new Resource("b")) {
            second.use(false);
        }
        try (Resource only = new Resource("c")) {
            only.use(true);
        } catch (IllegalStateException exception) {
            Serial.println("caught use failure");
        } finally {
            Serial.println("finally after close");
        }
        try (Resource absent = null) {
            Serial.println("null body");
        }
        try (FailingClose failing = new FailingClose()) {
            Serial.println("body ok");
        } catch (IllegalStateException exception) {
            Serial.println("close raised");
        }
        try (FailingClose failing = new FailingClose()) {
            throw new IllegalArgumentException("body");
        } catch (IllegalArgumentException exception) {
            Serial.println("body exception kept");
        }
        Serial.println("done");
    }
}
