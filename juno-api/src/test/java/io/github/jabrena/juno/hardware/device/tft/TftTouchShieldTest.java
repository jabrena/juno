package io.github.jabrena.juno.hardware.device.tft;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.INT_ARRAY;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ELEGOO TFT touch shield")
class TftTouchShieldTest {
    private Gpio pins;
    private TftTouchShield tft;
    private int touchXPin;
    private int touchYPin;

    @BeforeEach
    void initialiseThePanelInPortrait() {
        Wire.resetBoard();
        ParallelBus.reset();
        pins = mock(Gpio.class);
        when(pins.analogPin("A2")).thenReturn(16);
        when(pins.analogPin("A3")).thenReturn(17);
        touchXPin = pins.analogPin("A2");
        touchYPin = pins.analogPin("A3");
        tft = new TftTouchShield(pins);
        ParallelBus.bytes.clear();
        ParallelBus.runs.clear();
    }

    static Stream<Arguments> rotations() {
        return Stream.of(
                arguments(TftTouchShield.LANDSCAPE, 320, 240, 0xE8),
                arguments(TftTouchShield.LANDSCAPE_FLIPPED, 320, 240, 0x28),
                arguments(TftTouchShield.PORTRAIT_FLIPPED, 240, 320, 0x48));
    }

    static Stream<Arguments> colors() {
        return Stream.of(
                arguments(255, 0, 0, TftTouchShield.RED),
                arguments(0, 255, 0, TftTouchShield.GREEN),
                arguments(0, 0, 255, TftTouchShield.BLUE),
                arguments(255, 255, 255, TftTouchShield.WHITE));
    }

    static Stream<Arguments> touchOrientations() {
        return Stream.of(
                arguments(TftTouchShield.LANDSCAPE, 158, 146),
                arguments(TftTouchShield.LANDSCAPE_FLIPPED, 161, 93),
                arguments(TftTouchShield.PORTRAIT_FLIPPED, 146, 161));
    }

    static Stream<Arguments> windowsOutsideTheScreen() {
        return Stream.of(
                arguments(1, 0, 240, 1, "past the right edge"),
                arguments(0, -1, 1, 1, "above the top"),
                arguments(0, 0, 1, 321, "past the bottom edge"),
                arguments(239, 0, 2, 1, "past the right edge at the last column"),
                arguments(0, 0, 0, 2, "empty"));
    }

    @Test
    @DisplayName("Begin reports the portrait dimensions")
    void shouldReportThePortraitDimensionsAfterBegin() {
        // When & Then
        assertThat(new int[] {tft.width(), tft.height()}).containsExactly(240, 320);
    }

    @Test
    @DisplayName("Begin routes the data bus to the parallel pins")
    void shouldRouteTheDataBusToTheParallelPins() {
        // When & Then
        assertThat(ParallelBus.dataPins).containsExactly(8, 9, 2, 3, 4, 5, 6, 7);
    }

    @Test
    @DisplayName("Begin routes the strobe to the write pin")
    void shouldRouteTheStrobeToTheWritePin() {
        // When & Then
        assertThat(ParallelBus.strobePin).isEqualTo(TftTouchShield.PIN_WR);
    }

    @ParameterizedTest(name = "rotation {0} is {1}x{2} with MADCTL 0x{3}")
    @MethodSource("rotations")
    @DisplayName("Set rotation swaps the dimensions for the landscape orientations")
    void shouldSwapTheDimensionsForTheRotation(int rotation, int width, int height, int madctl) {
        // When
        tft.setRotation(rotation);

        // Then
        assertThat(new int[] {tft.width(), tft.height()}).containsExactly(width, height);
        assertThat(ParallelBus.bytes).containsSubsequence(0x36, madctl);
    }

    @ParameterizedTest(name = "r={0}, g={1}, b={2} packs to {3}")
    @MethodSource("colors")
    @DisplayName("Color packs RGB into sixteen bits")
    void shouldPackRgbIntoSixteenBits(int red, int green, int blue, int expected) {
        // When & Then
        assertThat(TftTouchShield.color(red, green, blue)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Fill rect clips to the screen before sending its pixels")
    void shouldClipTheFillRectToTheScreenBeforeSendingItsPixels() {
        // When
        tft.fillRect(-5, -5, 10, 10, TftTouchShield.RED);

        // Then
        assertThat(ParallelBus.runs).hasSize(1).first(INT_ARRAY).containsExactly(TftTouchShield.RED, 25);
    }

    @Test
    @DisplayName("Fill rect sends the clipped column window")
    void shouldSendTheClippedColumnWindowForFillRect() {
        // When
        tft.fillRect(-5, -5, 10, 10, TftTouchShield.RED);

        // Then
        assertThat(ParallelBus.bytes).containsSubsequence(TftTouchShield.COLUMN_ADDRESS_SET, 0x00, 0x00, 0x00, 0x04);
    }

    @Test
    @DisplayName("An unchanged window is not sent twice")
    void shouldNotSendAnUnchangedWindowTwice() {
        // Given
        tft.fillRect(0, 0, 4, 4, TftTouchShield.BLUE);
        ParallelBus.bytes.clear();

        // When
        tft.fillRect(0, 0, 4, 4, TftTouchShield.BLUE);

        // Then
        assertThat(ParallelBus.bytes).containsExactly(TftTouchShield.MEMORY_WRITE);
    }

    @Test
    @DisplayName("Fill rect outside the screen sends nothing")
    void shouldSendNothingForAFillRectOutsideTheScreen() {
        // When
        tft.fillRect(300, 300, 10, 10, TftTouchShield.RED);

        // Then
        assertThat(ParallelBus.runs).isEmpty();
    }

    @Test
    @DisplayName("Fill screen covers every pixel")
    void shouldCoverEveryPixelWhenFillingTheScreen() {
        // When
        tft.fillScreen(TftTouchShield.GREEN);

        // Then
        assertThat(ParallelBus.runs).hasSize(1).first(INT_ARRAY).containsExactly(TftTouchShield.GREEN, 240 * 320);
    }

    @Test
    @DisplayName("Draw pixel ignores points off the screen")
    void shouldIgnorePixelsOffTheScreen() {
        // When
        tft.drawPixel(240, 0, TftTouchShield.RED);
        tft.drawPixel(-1, 0, TftTouchShield.RED);

        // Then
        assertThat(ParallelBus.runs).isEmpty();
    }

    @Test
    @DisplayName("Draw pixel draws a point on the screen")
    void shouldDrawAPixelOnTheScreen() {
        // When
        tft.drawPixel(10, 10, TftTouchShield.BLUE);

        // Then
        assertThat(ParallelBus.runs).hasSize(1).first(INT_ARRAY).containsExactly(TftTouchShield.BLUE, 1);
    }

    @Test
    @DisplayName("Draw rect draws four edges")
    void shouldDrawARectangleWithFourEdges() {
        // When
        tft.drawRect(10, 10, 4, 3, TftTouchShield.WHITE);

        // Then
        assertThat(ParallelBus.runs).hasSize(4).allSatisfy(run -> assertThat(run[0]).isEqualTo(TftTouchShield.WHITE));
    }

    @Test
    @DisplayName("Draw rect covers its edges with the expected pixel count")
    void shouldCoverTheRectangleEdgesWithTheExpectedPixelCount() {
        // When
        tft.drawRect(10, 10, 4, 3, TftTouchShield.WHITE);

        // Then
        assertThat(ParallelBus.runs.stream().mapToInt(run -> run[1]).sum()).isEqualTo(4 + 4 + 3 + 3);
    }

    @Test
    @DisplayName("Horizontal and vertical lines draw their length")
    void shouldDrawHorizontalAndVerticalLinesWithTheirLength() {
        // When
        tft.drawHorizontalLine(0, 0, 5, TftTouchShield.CYAN);
        tft.drawVerticalLine(0, 0, 6, TftTouchShield.CYAN);

        // Then
        assertThat(ParallelBus.runs.stream().mapToInt(run -> run[1]).sum()).isEqualTo(5 + 6);
    }

    @Test
    @DisplayName("Fill circle covers the disc row by row")
    void shouldCoverTheDiscRowByRow() {
        // When
        tft.fillCircle(50, 50, 2, TftTouchShield.WHITE);

        // Then
        assertThat(ParallelBus.runs.stream().mapToInt(run -> run[1]).sum()).isEqualTo(5 + 3 + 3 + 1 + 1);
    }

    @Test
    @DisplayName("Draw circle outlines with single pixels")
    void shouldOutlineACircleWithSinglePixels() {
        // When
        tft.drawCircle(50, 50, 3, TftTouchShield.WHITE);

        // Then
        assertThat(ParallelBus.runs).isNotEmpty().allSatisfy(run -> assertThat(run[1]).isEqualTo(1));
    }

    @Test
    @DisplayName("Begin pixels accepts a window that fits the screen")
    void shouldAcceptAWindowThatFitsTheScreen() {
        // When & Then
        assertThat(tft.beginPixels(0, 0, 2, 2)).isTrue();
    }

    @Test
    @DisplayName("Push pixel streams a pixel into the open window")
    void shouldStreamAPixelIntoTheOpenWindow() {
        // Given
        tft.beginPixels(0, 0, 2, 2);

        // When
        tft.pushPixel(TftTouchShield.RED);

        // Then
        assertThat(ParallelBus.runs).hasSize(1).first(INT_ARRAY).containsExactly(TftTouchShield.RED, 1);
    }

    @Test
    @DisplayName("Begin pixels accepts the whole screen as a window")
    void shouldAcceptTheWholeScreenAsAWindow() {
        // When & Then
        assertThat(tft.beginPixels(0, 0, 240, 320)).isTrue();
    }

    @ParameterizedTest(name = "rejects a window {4}")
    @MethodSource("windowsOutsideTheScreen")
    @DisplayName("Begin pixels rejects windows outside the screen")
    void shouldRejectWindowsOutsideTheScreen(int x, int y, int width, int height, String description) {
        // When & Then
        assertThat(tft.beginPixels(x, y, width, height)).isFalse();
    }

    @Test
    @DisplayName("Print draws each glyph as its pixels and advances the cursor")
    void shouldDrawEachGlyphAsItsPixelsAndAdvanceTheCursor() {
        // Given
        tft.setCursor(0, 0);

        // When
        tft.print("A");

        // Then
        assertThat(ParallelBus.runs).hasSize(TftTouchShield.CHAR_WIDTH * TftTouchShield.CHAR_HEIGHT);
    }

    @Test
    @DisplayName("Print scales the text size")
    void shouldScaleTheTextSize() {
        // Given
        tft.setTextSize(2);
        tft.setCursor(0, 0);

        // When
        tft.print("A");

        // Then
        assertThat(ParallelBus.runs).hasSize(TftTouchShield.CHAR_WIDTH * TftTouchShield.CHAR_HEIGHT * 4);
    }

    @Test
    @DisplayName("Print wraps to the next line at the right edge")
    void shouldWrapToTheNextLineAtTheRightEdge() {
        // Given
        tft.setCursor(236, 0);

        // When
        tft.print("B");

        // Then
        assertThat(ParallelBus.bytes).containsSubsequence(TftTouchShield.COLUMN_ADDRESS_SET, 0x00, 0x00, 0x00, 0x05);
    }

    @Test
    @DisplayName("Print draws each digit of an integer")
    void shouldDrawEachDigitOfAnInteger() {
        // Given
        tft.setCursor(0, 0);

        // When
        tft.print(255);

        // Then
        assertThat(ParallelBus.runs).hasSize(3 * TftTouchShield.CHAR_WIDTH * TftTouchShield.CHAR_HEIGHT);
    }

    @Test
    @DisplayName("Print draws each byte of a buffer, including unprintable ones")
    void shouldDrawEachByteOfABufferIncludingUnprintableOnes() {
        // Given
        tft.setCursor(0, 0);

        // When
        tft.print(new byte[] {'\u0001', 'x', 0x7F}, 3);

        // Then
        assertThat(ParallelBus.runs).hasSize(3 * TftTouchShield.CHAR_WIDTH * TftTouchShield.CHAR_HEIGHT);
    }

    @Test
    @DisplayName("Println with an empty string moves to the next line")
    void shouldMoveToTheNextLineForAnEmptyPrintln() {
        // Given
        tft.setCursor(0, 0);

        // When
        tft.println("");
        tft.print("C");

        // Then
        assertThat(ParallelBus.bytes).containsSubsequence(TftTouchShield.COLUMN_ADDRESS_SET, 0x00, 0x00, 0x00, 0x05);
    }

    @Test
    @DisplayName("Read touch reports the raw coordinates and pressure")
    void shouldReportTheRawTouchCoordinatesAndPressure() {
        // Given
        when(pins.analogRead(touchXPin)).thenReturn(523);
        when(pins.analogRead(touchYPin)).thenReturn(600);

        // When
        boolean touched = tft.readTouch();

        // Then
        assertThat(touched).isTrue();
        assertThat(new int[] {tft.touchRawX(), tft.touchRawY(), tft.touchPressure()})
                .containsExactly(423, 500, 946);
    }

    @Test
    @DisplayName("Read touch maps the touch to screen coordinates")
    void shouldMapTheTouchToScreenCoordinates() {
        // Given
        when(pins.analogRead(touchXPin)).thenReturn(523);
        when(pins.analogRead(touchYPin)).thenReturn(600);

        // When
        boolean touched = tft.readTouch();

        // Then
        assertThat(touched).isTrue();
        assertThat(new int[] {tft.touchX(), tft.touchY()}).containsExactly(93, 158);
    }

    @Test
    @DisplayName("Read touch reports no touch without pressure")
    void shouldReportNoTouchWithoutPressure() {
        // Given
        when(pins.analogRead(touchXPin)).thenReturn(0);
        when(pins.analogRead(touchYPin)).thenReturn(0);

        // When & Then
        assertThat(tft.readTouch()).isFalse();
    }

    @ParameterizedTest(name = "rotation {0} maps the touch to ({1}, {2})")
    @MethodSource("touchOrientations")
    @DisplayName("Read touch follows the rotation")
    void shouldFollowTheRotationWhenReadingATouch(int rotation, int x, int y) {
        // Given
        when(pins.analogRead(touchXPin)).thenReturn(523);
        when(pins.analogRead(touchYPin)).thenReturn(600);
        tft.setRotation(rotation);

        // When
        boolean touched = tft.readTouch();

        // Then
        assertThat(touched).isTrue();
        assertThat(new int[] {tft.touchX(), tft.touchY()}).containsExactly(x, y);
    }

    @Test
    @DisplayName("Calibration changes the raw-to-screen mapping")
    void shouldChangeTheRawToScreenMappingWhenCalibrated() {
        // Given
        when(pins.analogRead(touchXPin)).thenReturn(523);
        when(pins.analogRead(touchYPin)).thenReturn(600);
        tft.calibrateTouch(0, 1000, 1000, 0);

        // When
        boolean touched = tft.readTouch();

        // Then
        assertThat(touched).isTrue();
        assertThat(new int[] {tft.touchX(), tft.touchY()}).containsExactly(101, 160);
    }

    @Test
    @DisplayName("Draw pixel ignores points above or below the screen")
    void shouldIgnorePixelsAboveOrBelowTheScreen() {
        // When
        tft.drawPixel(0, -1, TftTouchShield.RED);
        tft.drawPixel(0, 320, TftTouchShield.RED);

        // Then
        assertThat(ParallelBus.runs).isEmpty();
    }

    @Test
    @DisplayName("A character that would run off the bottom is not drawn")
    void shouldNotDrawACharacterThatWouldRunOffTheBottom() {
        // Given
        tft.setCursor(0, 316);

        // When
        tft.print("A");

        // Then
        assertThat(ParallelBus.runs).isEmpty();
    }

    @Test
    @DisplayName("A touch that moves between samples is not accepted")
    void shouldNotAcceptATouchThatMovesBetweenSamples() {
        // Given
        // Pressure, then X and Y, then X and Y again, then pressure again: the second X sample moved too far.
        when(pins.analogRead(anyInt())).thenReturn(100, 600, 513, 100, 300, 100, 100, 600);

        // When & Then
        assertThat(tft.readTouch()).isFalse();
    }

    @Test
    @DisplayName("A confirming pressure reading out of range is not accepted")
    void shouldNotAcceptATouchWhoseConfirmingPressureIsOutOfRange() {
        // Given
        // The first pressure is in range; the confirming pressure afterwards is not.
        when(pins.analogRead(anyInt())).thenReturn(100, 600, 513, 100, 513, 100, 100, 0);

        // When & Then
        assertThat(tft.readTouch()).isFalse();
    }

    @Test
    @DisplayName("A rejected touch reports zero pressure")
    void shouldReportZeroPressureForARejectedTouch() {
        // Given
        when(pins.analogRead(anyInt())).thenReturn(100, 600, 513, 100, 513, 100, 100, 0);
        tft.readTouch();

        // When & Then
        assertThat(tft.touchPressure()).isZero();
    }
}
