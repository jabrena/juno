package io.github.jabrena.juno.hardware.security;

import io.github.jabrena.juno.hardware.BoardModel;
import io.github.jabrena.juno.hardware.i2c.I2c;

/** Selects the secure-element provider compiled for a board. */
public final class SecretStoreFactory {
    private SecretStoreFactory() {
    }

    /**
     * Opens the board's secure store. Portenta H7 uses its NXP SE050C2; other supported boards use an ATECC608 on
     * {@code i2c}. The compiler folds the board query, so the unused provider is removed from the generated program.
     */
    public static SecretStore open(BoardModel board, I2c i2c, SecretSlots slots) {
        if (board.isPortentaH7()) {
            return new Se050C2(slots);
        }
        i2c.begin();
        return new Atecc608(i2c, slots);
    }
}
