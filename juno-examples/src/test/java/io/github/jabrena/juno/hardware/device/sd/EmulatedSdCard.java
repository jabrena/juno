package io.github.jabrena.juno.hardware.device.sd;

import io.github.jabrena.juno.hardware.internal.NativeSdCard;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * An SD card for tests, its files held in memory. Once {@link #install()} has run, every shield's {@code getSdCard()}
 * hands out one such card, which has no card inserted unless {@code -Djuno.emulator.sdCard=true} or a seeded Miner2
 * identity ({@code -Djuno.emulator.miner2Identity}) says otherwise, so the games that look for files on the card at
 * startup take their no-card path, deterministically. A unit test creates its own card and passes it in.
 */
public final class EmulatedSdCard implements SdCard {
    private final Map<String, String> files = new HashMap<>();
    private boolean present;

    /** An empty card; {@link #insert()} it before the program looks for it. */
    public EmulatedSdCard() {
    }

    /** Makes every shield's {@code getSdCard()} from now on hand out an emulated card instead of the board's. */
    public static void install() {
        EmulatedSdCard card = new EmulatedSdCard();
        String miner2Identity = System.getProperty("juno.emulator.miner2Identity");
        if (Boolean.getBoolean("juno.emulator.sdCard") || miner2Identity != null) {
            card.insert();
        }
        if (miner2Identity != null) {
            card.appendLine("MINER2.ID", miner2Identity);
        }
        NativeSdCard.setSdCard(card);
    }

    /** Puts the card in its socket, so {@link #begin()} finds it. */
    public void insert() {
        present = true;
    }

    @Override
    public boolean begin() {
        return present;
    }

    @Override
    public boolean begin(int chipSelectPin) {
        return present;
    }

    @Override
    public boolean exists(String path) {
        return present && files.containsKey(path);
    }

    @Override
    public int size(String path) {
        return exists(path) ? files.get(path).getBytes(StandardCharsets.UTF_8).length : -1;
    }

    @Override
    public InputStream open(String path) {
        return exists(path)
                ? new ByteArrayInputStream(files.get(path).getBytes(StandardCharsets.UTF_8))
                : null;
    }

    @Override
    public boolean appendLine(String path, String line) {
        return appendText(path, line + "\n");
    }

    @Override
    public boolean appendText(String path, String text) {
        if (!present) {
            return false;
        }
        files.merge(path, text, String::concat);
        return true;
    }

    @Override
    public boolean remove(String path) {
        return present && files.remove(path) != null;
    }

    @Override
    public int totalKiB() {
        return present ? 1024 : -1;
    }

    @Override
    public int freeKiB() {
        return present ? 1024 : -1;
    }
}
