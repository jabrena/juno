package io.github.jabrena.juno.thirdparty.pico.secure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Juno's TrustZone secure firmware for the Raspberry Pi Pico 2 W, built from {@code src/main/c} and shipped prebuilt.
 *
 * <p>It takes the first 12 KB of flash, the slot every arduino-pico image reserves for its OTA stage, so a sketch built
 * as usual still starts at {@code 0x10003000}: the boot ROM starts this firmware, which fences off itself, the
 * secret-store sector and the top 8 KB of SRAM, and starts the sketch in the non-secure world. The sketch then reaches
 * the store only through the gateway at {@link #GATEWAY}. {@link #installIntoUf2} swaps it into a sketch's UF2 in
 * place of the OTA stage; the sketch has to be built with {@link #RAM_LENGTH_PROPERTY} so its heap ends below the
 * secure SRAM.
 */
public final class PicoSecureFirmware {
    /** Where the slot starts, and where the sketch's own vector table follows it. */
    public static final int SLOT_START = 0x10000000;
    public static final int SLOT_END = 0x10003000;
    /** The non-secure-callable entry: {@code int juno_secure_call(int op, int a, int b, int c)}. */
    public static final int GATEWAY = 0x10002F00;
    /** The flash sector holding the store, fenced off from the sketch. */
    public static final int STORE_SECTOR = 0x103FE000;
    /** The arduino-pico build property that keeps the sketch's RAM below the secure firmware's 8 KB. */
    public static final String RAM_LENGTH_PROPERTY = "build.ram_length=504k";
    /**
     * The arduino-pico build property that routes the SDK's flash writes (BTstack's bonds, the EEPROM library) through
     * the gateway: the sketch cannot erase or program flash itself from the non-secure world.
     */
    public static final String WRAP_FLASH_PROPERTY =
            "compiler.c.elf.extra_flags=-Wl,--wrap=flash_range_erase,--wrap=flash_range_program";

    private static final String RESOURCE = "pico-secure/juno-pico-secure.bin";
    private static final int UF2_BLOCK = 512;
    private static final int UF2_MAGIC_START0 = 0x0A324655;
    private static final int UF2_MAGIC_START1 = 0x9E5D5157;
    private static final int UF2_MAGIC_END = 0x0AB16F30;
    private static final int UF2_PAYLOAD = 256;

    private final byte[] image;

    private PicoSecureFirmware(byte[] image) {
        if (image.length == 0 || image.length > SLOT_END - SLOT_START) {
            throw new IllegalArgumentException("The secure firmware must fit the 12 KB slot, not " + image.length
                    + " bytes");
        }
        this.image = image.clone();
    }

    /** The firmware shipped in this jar. */
    public static PicoSecureFirmware bundled() throws IOException {
        try (InputStream input = PicoSecureFirmware.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IOException("The Pico secure firmware image is missing from the classpath");
            }
            return new PicoSecureFirmware(input.readAllBytes());
        }
    }

    static PicoSecureFirmware of(byte[] image) {
        return new PicoSecureFirmware(image);
    }

    /** The firmware's bytes, from {@link #SLOT_START}. */
    public byte[] image() {
        return image.clone();
    }

    /**
     * Returns {@code uf2}, an arduino-pico sketch's UF2, with every 256-byte block inside the slot rewritten to carry
     * this firmware instead of the OTA stage; the rest of the slot is padded with erased flash ({@code 0xFF}). Every
     * other block, the sketch from {@link #SLOT_END} on, is left exactly as it was.
     */
    public byte[] installIntoUf2(byte[] uf2) {
        if (uf2.length == 0 || uf2.length % UF2_BLOCK != 0) {
            throw new IllegalArgumentException("Not a UF2 file: its length is not a multiple of " + UF2_BLOCK);
        }
        byte[] slot = new byte[SLOT_END - SLOT_START];
        Arrays.fill(slot, (byte) 0xFF);
        System.arraycopy(image, 0, slot, 0, image.length);
        byte[] result = uf2.clone();
        boolean[] covered = new boolean[slot.length / UF2_PAYLOAD];
        for (int offset = 0; offset < result.length; offset += UF2_BLOCK) {
            ByteBuffer block = ByteBuffer.wrap(result, offset, UF2_BLOCK).slice().order(ByteOrder.LITTLE_ENDIAN);
            if (block.getInt(0) != UF2_MAGIC_START0 || block.getInt(4) != UF2_MAGIC_START1
                    || block.getInt(UF2_BLOCK - 4) != UF2_MAGIC_END) {
                throw new IllegalArgumentException("Not a UF2 file: block " + offset / UF2_BLOCK + " has no UF2 magic");
            }
            long address = Integer.toUnsignedLong(block.getInt(12));
            if (address < SLOT_START || address >= SLOT_END) {
                continue;
            }
            if (block.getInt(16) != UF2_PAYLOAD || (address - SLOT_START) % UF2_PAYLOAD != 0) {
                throw new IllegalArgumentException("UF2 block at 0x" + Long.toHexString(address)
                        + " is not a whole 256-byte page");
            }
            int page = (int) (address - SLOT_START);
            System.arraycopy(slot, page, result, offset + 32, UF2_PAYLOAD);
            covered[page / UF2_PAYLOAD] = true;
        }
        for (int page = 0; page < covered.length; page++) {
            if (!covered[page]) {
                throw new IllegalArgumentException("The UF2 does not cover the slot: no block at 0x"
                        + Integer.toHexString(SLOT_START + page * UF2_PAYLOAD));
            }
        }
        return result;
    }
}
