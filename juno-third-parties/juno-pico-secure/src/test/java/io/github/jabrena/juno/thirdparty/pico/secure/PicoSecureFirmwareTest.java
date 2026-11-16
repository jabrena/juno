package io.github.jabrena.juno.thirdparty.pico.secure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PicoSecureFirmwareTest {
    private static final int SLOT_PAGES = (PicoSecureFirmware.SLOT_END - PicoSecureFirmware.SLOT_START) / 256;

    @Test
    void bundledImageIsASecureRp2350ExecutableThatFitsTheSlot() throws IOException {
        ByteBuffer image = ByteBuffer.wrap(PicoSecureFirmware.bundled().image()).order(ByteOrder.LITTLE_ENDIAN);

        assertThat(image.capacity()).isLessThanOrEqualTo(PicoSecureFirmware.SLOT_END - PicoSecureFirmware.SLOT_START);
        assertThat(image.getInt(0)).as("initial secure stack").isEqualTo(0x20080000);
        assertThat(image.getInt(4) & ~1).as("reset handler").isBetween(PicoSecureFirmware.SLOT_START,
                PicoSecureFirmware.GATEWAY);
        assertThat(image.getInt(0x40)).as("IMAGE_DEF start marker").isEqualTo(0xFFFFDED3);
        assertThat(image.getShort(0x46)).as("EXE, secure, Arm, RP2350").isEqualTo((short) 0x1021);
        assertThat(image.getInt(0x50)).as("IMAGE_DEF end marker").isEqualTo(0xAB123579);
        assertThat(image.getInt(PicoSecureFirmware.GATEWAY - PicoSecureFirmware.SLOT_START))
                .as("SG at the gateway").isEqualTo(0xE97FE97F);
    }

    @Test
    void installReplacesOnlyTheSlotBlocks() {
        byte[] firmware = new byte[300];
        Arrays.fill(firmware, (byte) 0x5A);
        byte[] uf2 = uf2(SLOT_PAGES + 2);

        byte[] installed = PicoSecureFirmware.of(firmware).installIntoUf2(uf2);

        assertThat(payload(installed, 0)).containsOnly((byte) 0x5A);
        assertThat(Arrays.copyOfRange(payload(installed, 1), 0, 44)).containsOnly((byte) 0x5A);
        assertThat(Arrays.copyOfRange(payload(installed, 1), 44, 256)).containsOnly((byte) 0xFF);
        assertThat(payload(installed, SLOT_PAGES - 1)).containsOnly((byte) 0xFF);
        assertThat(payload(installed, SLOT_PAGES)).isEqualTo(payload(uf2, SLOT_PAGES));
        assertThat(payload(installed, SLOT_PAGES + 1)).isEqualTo(payload(uf2, SLOT_PAGES + 1));
        assertThat(uf2[32]).as("the input is left untouched").isEqualTo((byte) 1);
    }

    @Test
    void installRejectsAUf2ThatDoesNotCoverTheSlot() {
        byte[] uf2 = uf2(SLOT_PAGES - 1);

        assertThatIllegalArgumentException().isThrownBy(() -> PicoSecureFirmware.of(new byte[4]).installIntoUf2(uf2))
                .withMessageContaining("does not cover the slot");
    }

    @Test
    void installRejectsAFileThatIsNotUf2() {
        PicoSecureFirmware firmware = PicoSecureFirmware.of(new byte[4]);

        assertThatIllegalArgumentException().isThrownBy(() -> firmware.installIntoUf2(new byte[100]))
                .withMessageContaining("multiple of 512");
        assertThatIllegalArgumentException().isThrownBy(() -> firmware.installIntoUf2(new byte[512]))
                .withMessageContaining("no UF2 magic");
    }

    @Test
    void installRejectsABlockWithEitherOtherMagicWrong() {
        PicoSecureFirmware firmware = PicoSecureFirmware.of(new byte[4]);
        byte[] secondMagic = uf2(SLOT_PAGES);
        ByteBuffer.wrap(secondMagic).order(ByteOrder.LITTLE_ENDIAN).putInt(512 + 4, 0);
        byte[] endMagic = uf2(SLOT_PAGES);
        ByteBuffer.wrap(endMagic).order(ByteOrder.LITTLE_ENDIAN).putInt(512 + 508, 0);

        assertThatIllegalArgumentException().isThrownBy(() -> firmware.installIntoUf2(secondMagic))
                .withMessageContaining("block 1 has no UF2 magic");
        assertThatIllegalArgumentException().isThrownBy(() -> firmware.installIntoUf2(endMagic))
                .withMessageContaining("block 1 has no UF2 magic");
    }

    @Test
    void installRejectsSlotBlocksThatAreNotWholePages() {
        PicoSecureFirmware firmware = PicoSecureFirmware.of(new byte[4]);
        byte[] shortPayload = uf2(SLOT_PAGES);
        ByteBuffer.wrap(shortPayload).order(ByteOrder.LITTLE_ENDIAN).putInt(512 * 3 + 16, 128);
        byte[] unaligned = uf2(SLOT_PAGES);
        ByteBuffer.wrap(unaligned).order(ByteOrder.LITTLE_ENDIAN).putInt(512 * 3 + 12,
                PicoSecureFirmware.SLOT_START + 256 * 3 + 16);

        assertThatIllegalArgumentException().isThrownBy(() -> firmware.installIntoUf2(shortPayload))
                .withMessageContaining("0x10000300 is not a whole 256-byte page");
        assertThatIllegalArgumentException().isThrownBy(() -> firmware.installIntoUf2(unaligned))
                .withMessageContaining("is not a whole 256-byte page");
    }

    @Test
    void installLeavesBlocksBelowTheSlotAlone() {
        byte[] uf2 = uf2(SLOT_PAGES + 1);
        ByteBuffer.wrap(uf2).order(ByteOrder.LITTLE_ENDIAN).putInt(512 * SLOT_PAGES + 12, 0x0FFFFF00);

        byte[] installed = PicoSecureFirmware.of(new byte[4]).installIntoUf2(uf2);

        assertThat(payload(installed, SLOT_PAGES)).isEqualTo(payload(uf2, SLOT_PAGES));
    }

    @Test
    void firmwareMustFitTheSlot() {
        assertThatIllegalArgumentException().isThrownBy(() -> PicoSecureFirmware.of(new byte[0x3001]));
        assertThatIllegalArgumentException().isThrownBy(() -> PicoSecureFirmware.of(new byte[0]));
    }

    /** A UF2 of {@code pages} consecutive 256-byte pages from the slot start, page {@code n} filled with {@code n + 1}. */
    private static byte[] uf2(int pages) {
        ByteBuffer file = ByteBuffer.allocate(512 * pages).order(ByteOrder.LITTLE_ENDIAN);
        for (int page = 0; page < pages; page++) {
            int base = 512 * page;
            file.putInt(base, 0x0A324655).putInt(base + 4, 0x9E5D5157).putInt(base + 8, 0x2000)
                    .putInt(base + 12, PicoSecureFirmware.SLOT_START + 256 * page).putInt(base + 16, 256)
                    .putInt(base + 20, page).putInt(base + 24, pages).putInt(base + 28, 0xE48BFF59)
                    .putInt(base + 508, 0x0AB16F30);
            for (int i = 0; i < 256; i++) {
                file.put(base + 32 + i, (byte) (page + 1));
            }
        }
        return file.array();
    }

    private static byte[] payload(byte[] uf2, int block) {
        return Arrays.copyOfRange(uf2, 512 * block + 32, 512 * block + 288);
    }
}
