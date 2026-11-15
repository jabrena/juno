package io.github.jabrena.juno.maven.fixture;

import io.github.jabrena.juno.hardware.security.SecretSlots;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.security.SecretStoreFactory;
import io.github.jabrena.juno.hardware.serial.Serial;

/** A Pico 2 W program that uses the TrustZone secret store, so its build needs Juno's secure firmware. */
public final class PicoSecretProgram {
    private PicoSecretProgram() {
    }

    public static void main(String[] args) {
        SecretStore store = SecretStoreFactory.open(new SecretSlots().add("demo", 2));
        Serial.println(store.read("demo", new byte[63]));
    }
}
