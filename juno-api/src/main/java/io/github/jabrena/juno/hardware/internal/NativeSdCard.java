package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.device.sd.SdCard;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;

/**
 * The card in a shield's SD socket: each method but {@link #begin()} is an SD intrinsic the compiler lowers to the
 * {@code SdFat} library, so it has no Java body and cannot run, or be mocked, on a desktop JVM. Mock {@link SdCard}
 * instead. The board has one SD bus, so every instance drives the same card; an instance only remembers the
 * chip-select pin of the socket it was made for.
 */
public final class NativeSdCard implements SdCard {
    /**
     * What {@link #sdCard(int)} hands out instead of a board card once a whole-program emulator in a test replaced
     * it; {@code null} on the board.
     */
    private static @Nullable SdCard replacement;

    private final int chipSelectPin;

    private NativeSdCard(int chipSelectPin) {
        this.chipSelectPin = chipSelectPin;
    }

    /**
     * The card behind a socket whose chip select is {@code chipSelectPin}, for a shield's {@code getSdCard()}: the
     * shield creates it once and keeps it.
     */
    public static SdCard sdCard(int chipSelectPin) {
        SdCard emulated = replacement;
        return emulated != null ? emulated : new NativeSdCard(chipSelectPin);
    }

    /**
     * Makes every shield's {@code getSdCard()} hand out {@code card} from now on: for a whole-program emulator in a
     * test, never on the board. {@code null} restores the board's cards.
     */
    public static void setSdCard(@Nullable SdCard card) {
        replacement = card;
    }

    /** The chip-select pin {@link #begin()} initializes the card on. */
    public int chipSelectPin() {
        return chipSelectPin;
    }

    @Override
    public boolean begin() {
        return begin(chipSelectPin);
    }

    @Override
    public native boolean begin(int chipSelectPin);

    @Override
    public native boolean exists(String path);

    @Override
    public native boolean createDirectory(String path);

    @Override
    public native int size(String path);

    @Override
    public native InputStream open(String path);

    @Override
    public native InputStream open(String directory, String fileStem);

    @Override
    public native int list(String directory, int index, byte[] name, int capacity);

    @Override
    public native boolean appendLine(String path, String line);

    @Override
    public native boolean appendText(String path, String text);

    @Override
    public native boolean appendText(String directory, String fileStem, String text);

    @Override
    public native boolean remove(String path);

    @Override
    public native boolean removeDirectory(String path);

    @Override
    public native boolean remove(String directory, String fileStem);

    @Override
    public native int totalKiB();

    @Override
    public native int freeKiB();
}
