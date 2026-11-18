package io.github.jabrena.juno.hardware.device.tft;

import io.github.jabrena.juno.hardware.device.carrier.PortentaHatCarrier;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.spi.Spi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MHS-3.5 TFT on the Portenta Hat Carrier")
class Mhs35TftDisplayTest {
    private ScriptedSpi spi;
    private Mhs35TftDisplay tft;

    @BeforeEach
    void startTheDisplay() {
        Gpio pins = mock(Gpio.class);
        when(pins.digitalPin("D7")).thenReturn(7);
        when(pins.digitalPin("GPIO161")).thenReturn(161);
        when(pins.digitalPin("D5")).thenReturn(5);
        when(pins.digitalPin("D4")).thenReturn(4);
        spi = new ScriptedSpi();
        tft = new Mhs35TftDisplay(pins, spi, new PortentaHatCarrier(pins));
    }

    @Test
    @DisplayName("sends the startup as whole 16-bit words, each command and parameter with a zero high byte")
    void sendsWholeWords() {
        assertThat(spi.sent.size() % 2).isZero();
        for (int i = 0; i < spi.sent.size(); i = i + 2) {
            assertThat(spi.sent.get(i)).as("high byte of word %d", i / 2).isZero();
        }
    }

    @Test
    @DisplayName("starts in landscape, 480 pixels wide, and swaps the size for portrait")
    void rotatesTheView() {
        assertThat(tft.width()).isEqualTo(480);
        assertThat(tft.height()).isEqualTo(320);
        tft.setRotation(Mhs35TftDisplay.PORTRAIT);
        assertThat(tft.width()).isEqualTo(320);
        assertThat(tft.height()).isEqualTo(480);
    }

    @Test
    @DisplayName("fills a rectangle with its window and one RGB565 pixel per cell")
    void fillsARectangle() {
        tft.fillRect(10, 20, 2, 2, Mhs35TftDisplay.RED);
        int columns = spi.indexOfCommand(0x2A);
        assertThat(spi.sent.subList(columns + 1, columns + 9)).containsExactly(0, 0, 0, 10, 0, 0, 0, 11);
        int rows = spi.indexOfCommand(0x2B);
        assertThat(spi.sent.subList(rows + 1, rows + 9)).containsExactly(0, 0, 0, 20, 0, 0, 0, 21);
        assertThat(spi.sent.subList(spi.sent.size() - 8, spi.sent.size()))
                .containsExactly(0xF8, 0x00, 0xF8, 0x00, 0xF8, 0x00, 0xF8, 0x00);
    }

    @Test
    @DisplayName("sends only the range that changed between two windows")
    void skipsAnUnchangedRange() {
        tft.fillRect(10, 20, 1, 5, Mhs35TftDisplay.RED);
        int before = spi.sent.size();
        tft.fillRect(10, 40, 1, 5, Mhs35TftDisplay.RED);
        List<Integer> second = spi.sent.subList(before, spi.sent.size());
        assertThat(second).doesNotContain(0x2A).contains(0x2B, 0x2C);
    }

    @Test
    @DisplayName("clips a rectangle that starts off the screen to the visible part")
    void clipsARectangleToTheScreen() {
        tft.fillRect(-2, -3, 4, 5, Mhs35TftDisplay.WHITE);
        int columns = spi.indexOfCommand(0x2A);
        assertThat(spi.sent.subList(columns + 1, columns + 9)).containsExactly(0, 0, 0, 0, 0, 0, 0, 1);
        int rows = spi.indexOfCommand(0x2B);
        assertThat(spi.sent.subList(rows + 1, rows + 9)).containsExactly(0, 0, 0, 0, 0, 0, 0, 1);
    }

    @Test
    @DisplayName("sends a fill larger than the pixel buffer whole, every pixel once")
    void sendsALargeFillAcrossBuffers() {
        tft.fillRect(0, 0, 480, 2, Mhs35TftDisplay.BLUE);
        int memoryWrite = spi.indexOfCommand(0x2C);
        assertThat(spi.sent.size() - memoryWrite - 1).isEqualTo(480 * 2 * 2);
        assertThat(spi.sent.subList(spi.sent.size() - 2, spi.sent.size())).containsExactly(0x00, 0x1F);
    }

    @Test
    @DisplayName("streams a row pixel by pixel and refuses a window off the screen")
    void streamsPixels() {
        assertThat(tft.beginPixels(470, 0, 20, 1)).isFalse();
        assertThat(tft.beginPixels(0, 0, 3, 1)).isTrue();
        tft.pushPixel(Mhs35TftDisplay.BLACK);
        tft.pushPixel(Mhs35TftDisplay.RED);
        tft.pushPixel(Mhs35TftDisplay.BLACK);
        assertThat(spi.sent.subList(spi.sent.size() - 6, spi.sent.size()))
                .containsExactly(0x00, 0x00, 0xF8, 0x00, 0x00, 0x00);
    }

    @Test
    @DisplayName("runs the display at its fast clock and the touch controller at its own slow one")
    void setsEachDevicesClock() {
        tft.fillRect(0, 0, 1, 1, Mhs35TftDisplay.WHITE);
        assertThat(spi.clocks.get(spi.clocks.size() - 1)).isEqualTo(Ili9486Bus.CLOCK_HZ);
        spi.reply(0xB0, 50);
        tft.readTouch();
        assertThat(spi.clocks.get(spi.clocks.size() - 1)).isEqualTo(Xpt2046Touch.CLOCK_HZ);
    }

    @Test
    @DisplayName("draws lines, outlines and circles as runs of pixels in the given color")
    void drawsShapes() {
        int before = spi.sent.size();
        tft.drawHorizontalLine(0, 0, 4, Mhs35TftDisplay.RED);
        tft.drawVerticalLine(0, 0, 4, Mhs35TftDisplay.RED);
        tft.drawRect(10, 10, 5, 5, Mhs35TftDisplay.GREEN);
        tft.fillCircle(100, 100, 3, Mhs35TftDisplay.BLUE);
        tft.drawCircle(200, 100, 3, Mhs35TftDisplay.YELLOW);
        tft.flush();
        assertThat(spi.sent.subList(before, spi.sent.size())).contains(0xF8, 0x07, 0xE0, 0x1F, 0xFF);
        assertThat((spi.sent.size() - before) % 2).isZero();
    }

    @Test
    @DisplayName("prints a number one character cell per digit, with its sign")
    void printsANumber() {
        tft.setCursor(0, 0);
        tft.print(-305);
        // Four cells: '-', '3', '0', '5', each opened as its own 6x8 window.
        long windows = spi.sent.stream().filter(value -> value == 0x2C).count();
        assertThat(windows).isGreaterThanOrEqualTo(4);
        tft.setCursor(0, 0);
        int before = spi.sent.size();
        tft.print(Integer.MIN_VALUE);
        int cellBytes = 2 * Mhs35TftDisplay.CHAR_WIDTH * Mhs35TftDisplay.CHAR_HEIGHT;
        assertThat(spi.sent.size() - before).isGreaterThan(11 * cellBytes);
    }

    @Test
    @DisplayName("reports no touch below the pressure threshold")
    void reportsNoTouchWithoutPressure() {
        spi.reply(0xB0, 50);
        assertThat(tft.readTouch()).isFalse();
    }

    @Test
    @DisplayName("maps a raw touch to landscape with the panel's axes swapped and the width mirrored")
    void mapsATouchToTheScreen() {
        spi.reply(0xB0, 500);
        spi.reply(0xD0, 2000);
        spi.reply(0x90, 1000);
        assertThat(tft.readTouch()).isTrue();
        // Width from raw Y, 3936 at the left to 227 at the right; height from raw X, 268 at the top to 3880.
        assertThat(tft.touchX()).isEqualTo(379);
        assertThat(tft.touchY()).isEqualTo(153);
    }

    @Test
    @DisplayName("scales a 320x240 layout by 4/3 onto the panel, centered between black bars")
    void fitsALogicalSize() {
        tft.fitLogicalSize(320, 240);
        assertThat(tft.width()).isEqualTo(320);
        assertThat(tft.height()).isEqualTo(240);
        tft.fillRect(0, 0, 3, 3, Mhs35TftDisplay.RED);
        int columns = spi.indexOfCommand(0x2A);
        assertThat(spi.sent.subList(columns + 1, columns + 9)).containsExactly(0, 0, 0, 27, 0, 0, 0, 30);
        int rows = spi.indexOfCommand(0x2B);
        assertThat(spi.sent.subList(rows + 1, rows + 9)).containsExactly(0, 0, 0, 0, 0, 0, 0, 3);
        assertThat(spi.sent.size() - spi.indexOfCommand(0x2C) - 1).isEqualTo(4 * 4 * 2);
        assertThat(tft.beginPixels(0, 0, 1, 1)).isFalse();
    }

    @Test
    @DisplayName("reports a touch in the scaled layout's own coordinates")
    void mapsATouchIntoTheLogicalSize() {
        tft.fitLogicalSize(320, 240);
        spi.reply(0xB0, 500);
        spi.reply(0xD0, 2000);
        spi.reply(0x90, 1000);
        assertThat(tft.readTouch()).isTrue();
        assertThat(tft.touchX()).isEqualTo(264);
        assertThat(tft.touchY()).isEqualTo(115);
    }

    @Test
    @DisplayName("prints a character in the scaled layout as one panel window covering its whole cell")
    void printsInTheLogicalSize() {
        tft.fitLogicalSize(320, 240);
        tft.setCursor(0, 0);
        tft.print('A');
        // The 6x8 cell covers 8x10 panel pixels at 4/3.
        assertThat(spi.sent.size() - spi.indexOfCommand(0x2C) - 1).isEqualTo(8 * 10 * 2);
    }

    @Test
    @DisplayName("returns to the panel's own resolution and default calibration on a rotation")
    void rotationResetsTheLogicalSize() {
        tft.fitLogicalSize(320, 240);
        tft.setRotation(Mhs35TftDisplay.LANDSCAPE);
        assertThat(tft.width()).isEqualTo(480);
        tft.calibrateTouch(0, 4000, 0, 4000);
        spi.reply(0xB0, 500);
        spi.reply(0xD0, 2000);
        spi.reply(0x90, 1000);
        assertThat(tft.readTouch()).isTrue();
        assertThat(tft.touchX()).isEqualTo(120);
        assertThat(tft.touchY()).isEqualTo(160);
    }

    /** An SPI bus that records every byte and answers the touch commands. */
    static final class ScriptedSpi implements Spi {
        final List<Integer> sent = new ArrayList<>();
        final List<Integer> clocks = new ArrayList<>();
        private final Map<Integer, int[]> replies = new HashMap<>();
        private int[] pending = new int[0];
        private int pendingNext;

        @Override
        public void begin() {
        }

        @Override
        public void setClock(int frequencyHz) {
            clocks.add(frequencyHz);
        }

        @Override
        public void transfer(byte[] buffer, int length) {
            for (int i = 0; i < length; i++) {
                sent.add(buffer[i] & 0xFF);
                buffer[i] = 0;
            }
        }

        @Override
        public void write(byte[] buffer, int length) {
            for (int i = 0; i < length; i++) {
                sent.add(buffer[i] & 0xFF);
            }
        }

        @Override
        public int transfer(int value) {
            sent.add(value);
            if (pendingNext < pending.length) {
                return pending[pendingNext++];
            }
            int[] reply = replies.get(value);
            if (reply != null) {
                pending = reply;
                pendingNext = 0;
            }
            return 0;
        }

        /** A touch reply for {@code command}: the 12-bit {@code value} in the two bytes the chip returns. */
        void reply(int command, int value) {
            int raw = value << 3;
            replies.put(command, new int[] {(raw >> 8) & 0xFF, raw & 0xFF});
        }

        int indexOfCommand(int command) {
            return sent.lastIndexOf(command);
        }
    }
}
