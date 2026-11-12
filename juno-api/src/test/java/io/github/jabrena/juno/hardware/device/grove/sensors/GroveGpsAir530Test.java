package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Grove GPS Air530")
class GroveGpsAir530Test {

    @Test
    @DisplayName("Decodes a checksum-valid GGA fix into integer units")
    void shouldDecodeGgaPosition() {
        GroveGpsAir530 gps = new GroveGpsAir530(mock(Gpio.class), mock(Clock.class), 2);
        byte[] sentence = gga("4807.038", "S", "01131.000", "W", "1", "08", "545.4");
        int[] position = new int[GroveGpsAir530.POSITION_LENGTH];

        boolean valid = gps.parseGga(sentence, sentence.length, position, position.length);

        assertThat(valid).isTrue();
        assertThat(position).containsExactly(-48117300, -11516667, 54540, 8, 1);
    }

    @Test
    @DisplayName("Rejects a bad checksum and preserves the previous position")
    void shouldRejectBadChecksum() {
        GroveGpsAir530 gps = new GroveGpsAir530(mock(Gpio.class), mock(Clock.class), 2);
        byte[] sentence = gga("4807.038", "N", "01131.000", "E", "1", "08", "545.4");
        sentence[sentence.length - 1] = (byte) (sentence[sentence.length - 1] == '0' ? '1' : '0');
        int[] position = {1, 2, 3, 4, 5};

        assertThat(gps.parseGga(sentence, sentence.length, position, position.length)).isFalse();
        assertThat(position).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("Requires a Grove receive pin")
    void shouldRejectNegativeReceivePin() {
        assertThatThrownBy(() -> new GroveGpsAir530(mock(Gpio.class), mock(Clock.class), -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Configures the receive pin as an idle-high input")
    void shouldConfigureReceivePin() {
        Gpio gpio = mock(Gpio.class);
        when(gpio.getInputPullup()).thenReturn(7);
        GroveGpsAir530 gps = new GroveGpsAir530(gpio, mock(Clock.class), 2);

        gps.begin();

        verify(gpio).pinMode(2, 7);
    }

    @Test
    @DisplayName("Reads a sentence after discarding noise and carriage returns")
    void shouldReadSentenceFromSoftwareUart() {
        Gpio gpio = mock(Gpio.class);
        UartSignal signal = new UartSignal("noise$A\r\n");
        when(gpio.digitalRead(2)).thenAnswer(invocation -> signal.next());
        GroveGpsAir530 gps = new GroveGpsAir530(gpio, mock(Clock.class), 2);
        byte[] sentence = new byte[8];

        int length = gps.readSentence(sentence, sentence.length, 0);

        assertThat(length).isEqualTo(2);
        assertThat(sentence).startsWith("$A".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("Returns zero when no UART sentence starts before the timeout")
    void shouldTimeoutBeforeSentenceStart() {
        Gpio gpio = mock(Gpio.class);
        when(gpio.digitalRead(2)).thenReturn(true);
        Clock clock = mock(Clock.class);
        when(clock.micros()).thenReturn(100, 1_100);
        GroveGpsAir530 gps = new GroveGpsAir530(gpio, clock, 2);

        assertThat(gps.readSentence(new byte[8], 8, 1)).isZero();
    }

    @Test
    @DisplayName("Returns a negative length when a sentence exceeds its buffer")
    void shouldRejectSentenceOverflow() {
        Gpio gpio = mock(Gpio.class);
        UartSignal signal = new UartSignal("$ABC\n");
        when(gpio.digitalRead(2)).thenAnswer(invocation -> signal.next());
        GroveGpsAir530 gps = new GroveGpsAir530(gpio, mock(Clock.class), 2);

        assertThat(gps.readSentence(new byte[3], 3, 0)).isEqualTo(-1);
    }

    @Test
    @DisplayName("Returns a negative length when a UART stop bit is invalid")
    void shouldRejectBadStopBit() {
        Gpio gpio = mock(Gpio.class);
        UartSignal signal = new UartSignal("$A", 1);
        when(gpio.digitalRead(2)).thenAnswer(invocation -> signal.next());
        GroveGpsAir530 gps = new GroveGpsAir530(gpio, mock(Clock.class), 2);

        assertThat(gps.readSentence(new byte[8], 8, 0)).isEqualTo(-1);
    }

    @Test
    @DisplayName("Reads a valid position through the sentence loop")
    void shouldReadPosition() {
        byte[] validSentence = gga("4807.038", "N", "01131.000", "E", "1", "08", "545.4");
        Gpio gpio = mock(Gpio.class);
        UartSignal signal = new UartSignal(new String(validSentence, StandardCharsets.US_ASCII) + '\n');
        when(gpio.digitalRead(2)).thenAnswer(invocation -> signal.next());
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(0, 1, 2);
        when(clock.micros()).thenReturn(0);
        GroveGpsAir530 gps = new GroveGpsAir530(gpio, clock, 2);
        int[] position = new int[GroveGpsAir530.POSITION_LENGTH];

        assertThat(gps.readPosition(new byte[GroveGpsAir530.SENTENCE_CAPACITY],
                GroveGpsAir530.SENTENCE_CAPACITY, position, position.length, 100)).isTrue();
        assertThat(position).containsExactly(48117300, 11516667, 54540, 8, 1);
    }

    @Test
    @DisplayName("Leaves the position unchanged when the sentence loop times out")
    void shouldTimeoutWhileReadingPosition() {
        Gpio gpio = mock(Gpio.class);
        UartSignal signal = new UartSignal("$X\n");
        when(gpio.digitalRead(2)).thenAnswer(invocation -> signal.next());
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(0, 1, 2, 100);
        when(clock.micros()).thenReturn(0);
        GroveGpsAir530 gps = new GroveGpsAir530(gpio, clock, 2);
        int[] position = {1, 2, 3, 4, 5};

        assertThat(gps.readPosition(new byte[8], 8, position, position.length, 100)).isFalse();
        assertThat(position).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("Retries after an invalid sentence before accepting a later fix")
    void shouldRetryAfterInvalidSentence() {
        byte[] validSentence = gga("4807.038", "N", "01131.000", "E", "1", "08", "545.4");
        Gpio gpio = mock(Gpio.class);
        UartSignal signal = new UartSignal("$X\n" + new String(validSentence, StandardCharsets.US_ASCII) + '\n');
        when(gpio.digitalRead(2)).thenAnswer(invocation -> signal.next());
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(0, 1, 2, 3, 4, 5);
        when(clock.micros()).thenReturn(0, 100_000);
        GroveGpsAir530 gps = new GroveGpsAir530(gpio, clock, 2);

        assertThat(gps.readPosition(new byte[GroveGpsAir530.SENTENCE_CAPACITY],
                GroveGpsAir530.SENTENCE_CAPACITY, new int[5], 5, 100)).isTrue();
    }

    @Test
    @DisplayName("Reads a position when no timeout is requested")
    void shouldReadPositionWithoutTimeout() {
        byte[] validSentence = gga("4807.038", "N", "01131.000", "E", "1", "08", "545.4");
        Gpio gpio = mock(Gpio.class);
        UartSignal signal = new UartSignal(new String(validSentence, StandardCharsets.US_ASCII) + '\n');
        when(gpio.digitalRead(2)).thenAnswer(invocation -> signal.next());
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(0);
        when(clock.micros()).thenReturn(0);
        GroveGpsAir530 gps = new GroveGpsAir530(gpio, clock, 2);

        assertThat(gps.readPosition(new byte[GroveGpsAir530.SENTENCE_CAPACITY],
                GroveGpsAir530.SENTENCE_CAPACITY, new int[5], 5, 0)).isTrue();
    }

    @Test
    @DisplayName("Rejects malformed sentence and position buffer lengths")
    void shouldRejectInvalidBufferLengths() {
        GroveGpsAir530 gps = new GroveGpsAir530(mock(Gpio.class), mock(Clock.class), 2);
        byte[] sentence = gga("4807.038", "N", "01131.000", "E", "1", "08", "545.4");

        assertThatThrownBy(() -> gps.readSentence(new byte[1], 2, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gps.parseGga(sentence, 0, new int[5], 5))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gps.parseGga(sentence, sentence.length, new int[4], 4))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects invalid NMEA headers, checksum text and message types")
    void shouldRejectMalformedNmeaFrames() {
        GroveGpsAir530 gps = new GroveGpsAir530(mock(Gpio.class), mock(Clock.class), 2);
        byte[] valid = gga("4807.038", "N", "01131.000", "E", "1", "08", "545.4");

        byte[] wrongHeader = valid.clone();
        wrongHeader[0] = 'X';
        byte[] missingMarker = valid.clone();
        missingMarker[missingMarker.length - 3] = '!';
        byte[] invalidChecksumText = valid.clone();
        invalidChecksumText[invalidChecksumText.length - 1] = 'Z';

        assertThat(gps.parseGga(new byte[] {'$'}, 1, new int[5], 5)).isFalse();
        assertThat(gps.parseGga(wrongHeader, wrongHeader.length, new int[5], 5)).isFalse();
        assertThat(gps.parseGga(missingMarker, missingMarker.length, new int[5], 5)).isFalse();
        assertThat(gps.parseGga(invalidChecksumText, invalidChecksumText.length, new int[5], 5)).isFalse();
        byte[] otherMessage = nmea("GPRMC,123519");
        assertThat(gps.parseGga(otherMessage, otherMessage.length, new int[5], 5)).isFalse();
    }

    @Test
    @DisplayName("Rejects GGA-shaped messages with invalid headers or truncated fields")
    void shouldRejectNearMissGgaHeaders() {
        GroveGpsAir530 gps = new GroveGpsAir530(mock(Gpio.class), mock(Clock.class), 2);
        String fields = ",123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,";
        String[] invalidHeaders = {"GPRGA", "GPGBA", "GPGGB", "GPGGA;"};

        for (String invalidHeader : invalidHeaders) {
            byte[] sentence = nmea(invalidHeader + fields.substring(invalidHeader.length() == 6 ? 1 : 0));
            assertThat(gps.parseGga(sentence, sentence.length, new int[5], 5))
                .as(invalidHeader)
                .isFalse();
        }

        byte[] truncated = nmea("GPGGA,123519");
        assertThat(gps.parseGga(truncated, truncated.length, new int[5], 5)).isFalse();
    }

    @Test
    @DisplayName("Rejects invalid GGA directions, fixes and measurements")
    void shouldRejectInvalidGgaValues() {
        GroveGpsAir530 gps = new GroveGpsAir530(mock(Gpio.class), mock(Clock.class), 2);
        String[] invalidSentences = {
            "GPGGA,123519,4807.038,X,01131.000,E,1,08,0.9,545.4,M,46.9,M,,",
            "GPGGA,123519,4807.038,N,01131.000,X,1,08,0.9,545.4,M,46.9,M,,",
            "GPGGA,123519,4807.038,N,01131.000,E,0,08,0.9,545.4,M,46.9,M,,",
            "GPGGA,123519,4807.038,N,01131.000,E,1,x,0.9,545.4,M,46.9,M,,",
            "GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,x,M,46.9,M,,",
            "GPGGA,123519,AB07.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,",
            "GPGGA,123519,4861.000,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,",
            "GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.x,M,46.9,M,,",
            "GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9"
        };

        for (String invalidSentence : invalidSentences) {
            byte[] sentence = nmea(invalidSentence);
            assertThat(gps.parseGga(sentence, sentence.length, new int[5], 5))
                .as(invalidSentence)
                .isFalse();
        }
    }

    @Test
    @DisplayName("Accepts upper- and lower-case hexadecimal checksums")
    void shouldAcceptBothHexadecimalChecksumCases() {
        GroveGpsAir530 gps = new GroveGpsAir530(mock(Gpio.class), mock(Clock.class), 2);
        byte[] uppercase = ggaWithLetterChecksum();
        byte[] lowercase = uppercase.clone();
        for (int index = lowercase.length - 2; index < lowercase.length; index++) {
            if (lowercase[index] >= 'A' && lowercase[index] <= 'F') {
                lowercase[index] += 'a' - 'A';
            }
        }

        assertThat(gps.parseGga(uppercase, uppercase.length, new int[5], 5)).isTrue();
        assertThat(gps.parseGga(lowercase, lowercase.length, new int[5], 5)).isTrue();
    }

    @Test
    @DisplayName("Accepts decimal-free coordinates and negative altitudes")
    void shouldAcceptCoordinateAndAltitudeEdges() {
        GroveGpsAir530 gps = new GroveGpsAir530(mock(Gpio.class), mock(Clock.class), 2);

        byte[] coordinateWithoutFraction = gga("4807", "N", "01131.000", "E", "1", "08", "545.4");
        byte[] negativeAltitude = gga("4807.038", "N", "01131.000", "E", "1", "08", "-12.3");

        assertThat(gps.parseGga(coordinateWithoutFraction, coordinateWithoutFraction.length, new int[5], 5))
            .isTrue();
        assertThat(gps.parseGga(negativeAltitude, negativeAltitude.length, new int[5], 5)).isTrue();
    }

    @Test
    @DisplayName("Handles the maximum finite timeout without overflowing")
    void shouldHandleMaximumTimeout() {
        Gpio gpio = mock(Gpio.class);
        when(gpio.digitalRead(2)).thenReturn(true);
        Clock clock = mock(Clock.class);
        when(clock.micros()).thenReturn(0, Integer.MAX_VALUE);
        GroveGpsAir530 gps = new GroveGpsAir530(gpio, clock, 2);

        assertThat(gps.readSentence(new byte[8], 8, Integer.MAX_VALUE)).isZero();
    }

    private static byte[] gga(String latitude, String northSouth, String longitude, String eastWest,
            String fix, String satellites, String altitude) {
        return nmea("GPGGA,123519," + latitude + ',' + northSouth + ',' + longitude + ',' + eastWest + ',' + fix
                + ',' + satellites + ",0.9," + altitude + ",M,46.9,M,,");
    }

    private static byte[] ggaWithLetterChecksum() {
        for (int time = 0; time < 1000; time++) {
            String timeText = "%06d".formatted(time);
            byte[] sentence = nmea("GPGGA," + timeText + ",4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,");
            if (containsHexLetter(sentence)) {
                return sentence;
            }
        }
        throw new AssertionError("Could not find a checksum containing A-F");
    }

    private static boolean containsHexLetter(byte[] sentence) {
        return isHexLetter(sentence[sentence.length - 2]) || isHexLetter(sentence[sentence.length - 1]);
    }

    private static boolean isHexLetter(byte value) {
        return value >= 'A' && value <= 'F';
    }

    private static byte[] nmea(String payload) {
        int checksum = 0;
        for (int index = 0; index < payload.length(); index++) {
            checksum ^= payload.charAt(index);
        }
        return ("$" + payload + "*" + "%02X".formatted(checksum)).getBytes(StandardCharsets.US_ASCII);
    }

    private static final class UartSignal {
        private final boolean[] values;
        private int index;

        private UartSignal(String text) {
            this(text, -1);
        }

        private UartSignal(String text, int badStopByte) {
            values = new boolean[text.length() * 10];
            int offset = 0;
            for (int character = 0; character < text.length(); character++) {
                values[offset++] = false;
                for (int bit = 0; bit < 8; bit++) {
                    values[offset++] = (text.charAt(character) & (1 << bit)) != 0;
                }
                values[offset++] = character != badStopByte;
            }
        }

        private boolean next() {
            return values[index++];
        }
    }
}
