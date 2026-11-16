package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.thirdparty.pico.secure.PicoSecureFirmware;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Swaps Juno's TrustZone secure firmware into a built Pico 2 W sketch's UF2, in place of arduino-pico's OTA stage. */
final class PicoSecureFirmwareInstaller {
    private final FirmwareSource firmware;

    PicoSecureFirmwareInstaller() {
        this(PicoSecureFirmware::bundled);
    }

    PicoSecureFirmwareInstaller(FirmwareSource firmware) {
        this.firmware = firmware;
    }

    void install(Path uf2) {
        try {
            byte[] installed = firmware.load().installIntoUf2(Files.readAllBytes(uf2));
            Files.write(uf2, installed);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ArduinoCliException("Could not install the Pico 2 W secure firmware into " + uf2 + ": "
                    + exception.getMessage(), exception);
        }
    }

    /** Where the firmware image comes from: the bundled one, except in tests. */
    interface FirmwareSource {
        PicoSecureFirmware load() throws IOException;
    }
}
