package io.github.jabrena.juno.api.thirdparty.tft;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TftTouchShieldTest {

    @BeforeEach
    void initialiseThePanelInPortrait() {
        Wire.resetBoard();
        Gpio.analogSequence = null;
        ParallelBus.reset();
        TftTouchShield.begin();
        ParallelBus.bytes.clear();
        ParallelBus.runs.clear();
    }

    @Test
    void beginRoutesTheDataBusAndStrobeAndStartsInPortrait() {
        assertThat(TftTouchShield.width()).isEqualTo(240);
        assertThat(TftTouchShield.height()).isEqualTo(320);
        assertThat(ParallelBus.dataPins).containsExactly(8, 9, 2, 3, 4, 5, 6, 7);
        assertThat(ParallelBus.strobePin).isEqualTo(TftTouchShield.PIN_WR);
    }

    @Test
    void setRotationSwapsTheDimensionsForTheLandscapeOrientations() {
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        assertThat(TftTouchShield.width()).isEqualTo(320);
        assertThat(TftTouchShield.height()).isEqualTo(240);
        assertThat(ParallelBus.bytes).containsSubsequence(0x36, 0xE8);

        ParallelBus.bytes.clear();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE_FLIPPED);
        assertThat(TftTouchShield.width()).isEqualTo(320);
        assertThat(ParallelBus.bytes).containsSubsequence(0x36, 0x28);

        ParallelBus.bytes.clear();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        assertThat(TftTouchShield.height()).isEqualTo(320);
        assertThat(ParallelBus.bytes).containsSubsequence(0x36, 0x48);
    }

    @Test
    void colorPacksRgbIntoSixteenBits() {
        assertThat(TftTouchShield.color(255, 0, 0)).isEqualTo(TftTouchShield.RED);
        assertThat(TftTouchShield.color(0, 255, 0)).isEqualTo(TftTouchShield.GREEN);
        assertThat(TftTouchShield.color(0, 0, 255)).isEqualTo(TftTouchShield.BLUE);
        assertThat(TftTouchShield.color(255, 255, 255)).isEqualTo(TftTouchShield.WHITE);
    }

    @Test
    void fillRectClipsToTheScreenBeforeSendingItsPixels() {
        TftTouchShield.fillRect(-5, -5, 10, 10, TftTouchShield.RED);

        assertThat(ParallelBus.runs).hasSize(1);
        assertThat(ParallelBus.runs.get(0)).containsExactly(TftTouchShield.RED, 25);
        assertThat(ParallelBus.bytes).containsSubsequence(TftTouchShield.COLUMN_ADDRESS_SET, 0x00, 0x00, 0x00, 0x04);
    }

    @Test
    void anUnchangedWindowIsNotSentTwice() {
        TftTouchShield.fillRect(0, 0, 4, 4, TftTouchShield.BLUE);
        ParallelBus.bytes.clear();

        TftTouchShield.fillRect(0, 0, 4, 4, TftTouchShield.BLUE);

        assertThat(ParallelBus.bytes).containsExactly(TftTouchShield.MEMORY_WRITE);
    }

    @Test
    void fillRectOutsideTheScreenSendsNothing() {
        TftTouchShield.fillRect(300, 300, 10, 10, TftTouchShield.RED);

        assertThat(ParallelBus.runs).isEmpty();
    }

    @Test
    void fillScreenCoversEveryPixel() {
        TftTouchShield.fillScreen(TftTouchShield.GREEN);

        assertThat(ParallelBus.runs).hasSize(1);
        assertThat(ParallelBus.runs.get(0)).containsExactly(TftTouchShield.GREEN, 240 * 320);
    }

    @Test
    void drawPixelIgnoresPointsOffTheScreen() {
        TftTouchShield.drawPixel(240, 0, TftTouchShield.RED);
        TftTouchShield.drawPixel(-1, 0, TftTouchShield.RED);
        assertThat(ParallelBus.runs).isEmpty();

        TftTouchShield.drawPixel(10, 10, TftTouchShield.BLUE);
        assertThat(ParallelBus.runs).hasSize(1);
        assertThat(ParallelBus.runs.get(0)).containsExactly(TftTouchShield.BLUE, 1);
    }

    @Test
    void rectanglesAndLinesDrawTheirEdges() {
        TftTouchShield.drawRect(10, 10, 4, 3, TftTouchShield.WHITE);

        assertThat(ParallelBus.runs).hasSize(4);
        assertThat(ParallelBus.runs).allSatisfy(run -> assertThat(run[0]).isEqualTo(TftTouchShield.WHITE));
        assertThat(ParallelBus.runs.stream().mapToInt(run -> run[1]).sum()).isEqualTo(4 + 4 + 3 + 3);

        ParallelBus.runs.clear();
        TftTouchShield.drawHorizontalLine(0, 0, 5, TftTouchShield.CYAN);
        TftTouchShield.drawVerticalLine(0, 0, 6, TftTouchShield.CYAN);
        assertThat(ParallelBus.runs.stream().mapToInt(run -> run[1]).sum()).isEqualTo(5 + 6);
    }

    @Test
    void fillCircleCoversTheDiscRowByRow() {
        TftTouchShield.fillCircle(50, 50, 2, TftTouchShield.WHITE);

        assertThat(ParallelBus.runs.stream().mapToInt(run -> run[1]).sum()).isEqualTo(5 + 3 + 3 + 1 + 1);
    }

    @Test
    void drawCircleOutlinesWithSinglePixels() {
        TftTouchShield.drawCircle(50, 50, 3, TftTouchShield.WHITE);

        assertThat(ParallelBus.runs).isNotEmpty();
        assertThat(ParallelBus.runs).allSatisfy(run -> assertThat(run[1]).isEqualTo(1));
    }

    @Test
    void beginPixelsStreamsIntoAWindowThatFitsTheScreen() {
        assertThat(TftTouchShield.beginPixels(0, 0, 2, 2)).isTrue();
        TftTouchShield.pushPixel(TftTouchShield.RED);
        assertThat(ParallelBus.runs).hasSize(1);
        assertThat(ParallelBus.runs.get(0)).containsExactly(TftTouchShield.RED, 1);

        assertThat(TftTouchShield.beginPixels(0, 0, 0, 2)).as("empty").isFalse();
        assertThat(TftTouchShield.beginPixels(239, 0, 2, 1)).as("past the right edge").isFalse();
    }

    @Test
    void printDrawsEachGlyphAsItsPixelsAndAdvancesTheCursor() {
        TftTouchShield.setCursor(0, 0);
        TftTouchShield.print("A");

        assertThat(ParallelBus.runs).hasSize(TftTouchShield.CHAR_WIDTH * TftTouchShield.CHAR_HEIGHT);
    }

    @Test
    void printScalesTheTextSize() {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(0, 0);
        TftTouchShield.print("A");

        assertThat(ParallelBus.runs).hasSize(TftTouchShield.CHAR_WIDTH * TftTouchShield.CHAR_HEIGHT * 4);
    }

    @Test
    void printWrapsToTheNextLineAtTheRightEdge() {
        TftTouchShield.setCursor(236, 0);
        TftTouchShield.print("B");

        assertThat(ParallelBus.bytes).containsSubsequence(TftTouchShield.COLUMN_ADDRESS_SET, 0x00, 0x00, 0x00, 0x05);
    }

    @Test
    void printAcceptsIntegersBytesAndNewlines() {
        TftTouchShield.setCursor(0, 0);
        TftTouchShield.print(255);
        assertThat(ParallelBus.runs).hasSize(3 * TftTouchShield.CHAR_WIDTH * TftTouchShield.CHAR_HEIGHT);

        ParallelBus.runs.clear();
        TftTouchShield.print(new byte[] {'\u0001', 'x', 0x7F}, 3);
        assertThat(ParallelBus.runs).hasSize(3 * TftTouchShield.CHAR_WIDTH * TftTouchShield.CHAR_HEIGHT);

        ParallelBus.runs.clear();
        TftTouchShield.setCursor(0, 0);
        TftTouchShield.println("");
        TftTouchShield.print("C");
        assertThat(ParallelBus.bytes).containsSubsequence(TftTouchShield.COLUMN_ADDRESS_SET, 0x00, 0x00, 0x00, 0x05);
    }

    @Test
    void readTouchMapsARaisedPressureToScreenCoordinates() {
        Gpio.analogValues[16] = 523;
        Gpio.analogValues[17] = 600;

        assertThat(TftTouchShield.readTouch()).isTrue();

        assertThat(TftTouchShield.touchRawX()).isEqualTo(423);
        assertThat(TftTouchShield.touchRawY()).isEqualTo(500);
        assertThat(TftTouchShield.touchPressure()).isEqualTo(946);
        assertThat(TftTouchShield.touchX()).isEqualTo(93);
        assertThat(TftTouchShield.touchY()).isEqualTo(158);
    }

    @Test
    void readTouchReportsNoTouchWithoutPressure() {
        Gpio.analogValues[16] = 0;
        Gpio.analogValues[17] = 0;

        assertThat(TftTouchShield.readTouch()).isFalse();
    }

    @Test
    void readTouchFollowsTheRotation() {
        Gpio.analogValues[16] = 523;
        Gpio.analogValues[17] = 600;

        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        assertThat(TftTouchShield.readTouch()).isTrue();
        assertThat(TftTouchShield.touchX()).isEqualTo(158);
        assertThat(TftTouchShield.touchY()).isEqualTo(146);

        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE_FLIPPED);
        assertThat(TftTouchShield.readTouch()).isTrue();
        assertThat(TftTouchShield.touchX()).isEqualTo(161);
        assertThat(TftTouchShield.touchY()).isEqualTo(93);

        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        assertThat(TftTouchShield.readTouch()).isTrue();
        assertThat(TftTouchShield.touchX()).isEqualTo(146);
        assertThat(TftTouchShield.touchY()).isEqualTo(161);
    }

    @Test
    void calibrationChangesTheRawToScreenMapping() {
        Gpio.analogValues[16] = 523;
        Gpio.analogValues[17] = 600;
        TftTouchShield.calibrateTouch(0, 1000, 1000, 0);

        assertThat(TftTouchShield.readTouch()).isTrue();

        assertThat(TftTouchShield.touchX()).isEqualTo(101);
        assertThat(TftTouchShield.touchY()).isEqualTo(160);
    }

    @Test
    void beginPixelsRejectsWindowsOutsideTheScreen() {
        assertThat(TftTouchShield.beginPixels(0, 0, 240, 320)).isTrue();
        assertThat(TftTouchShield.beginPixels(1, 0, 240, 1)).as("past the right edge").isFalse();
        assertThat(TftTouchShield.beginPixels(0, -1, 1, 1)).as("above the top").isFalse();
        assertThat(TftTouchShield.beginPixels(0, 0, 1, 321)).as("past the bottom edge").isFalse();
    }

    @Test
    void drawPixelIgnoresPointsAboveOrBelowTheScreen() {
        TftTouchShield.drawPixel(0, -1, TftTouchShield.RED);
        TftTouchShield.drawPixel(0, 320, TftTouchShield.RED);

        assertThat(ParallelBus.runs).isEmpty();
    }

    @Test
    void aCharacterThatWouldRunOffTheBottomIsNotDrawn() {
        TftTouchShield.setCursor(0, 316);
        TftTouchShield.print("A");

        assertThat(ParallelBus.runs).isEmpty();
    }

    @Test
    void aTouchThatMovesBetweenSamplesIsNotAccepted() {
        // Pressure, then X and Y, then X and Y again, then pressure again: the second X sample moved too far.
        Gpio.analogSequence = new int[] {100, 600, 513, 100, 300, 100, 100, 600};

        assertThat(TftTouchShield.readTouch()).isFalse();
    }

    @Test
    void aSecondPressureReadingOutOfRangeIsNotAccepted() {
        // The first pressure is in range; the confirming pressure afterwards is not.
        Gpio.analogSequence = new int[] {100, 600, 513, 100, 513, 100, 100, 0};

        assertThat(TftTouchShield.readTouch()).isFalse();
        assertThat(TftTouchShield.touchPressure()).isZero();
    }
}
