package io.github.jabrena.juno.hardware.net.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Minimal JSON reader")
class JsonTest {
    private static final byte[] DOCUMENT = bytes("""
            {"users":[{"id":1},{"id":2147483648,"active":false,
                "name":"A\\n\\u00e9\\uD83D\\uDE00"}],"numbers":[-12,1.25e2,0.1,-0.0,1e400,1e-400,1.5e300],"nothing":null,
                "minimum":-9223372036854775808,"tooLarge":9223372036854775808,"text":"a\\"b"}""");

    static Stream<Arguments> pathTypes() {
        return Stream.of(
                arguments("users", Json.TYPE_ARRAY),
                arguments("users[1].active", Json.TYPE_BOOLEAN),
                arguments("nothing", Json.TYPE_NULL),
                arguments("", Json.TYPE_OBJECT));
    }

    static Stream<Arguments> missingPaths() {
        return Stream.of(
                arguments("missing", Json.TYPE_MISSING),
                arguments("users[7]", Json.TYPE_MISSING),
                arguments("nothing.x", Json.TYPE_MISSING));
    }

    static Stream<Arguments> malformedPaths() {
        return Stream.of(
                arguments("users[01]"),
                arguments("users[]"),
                arguments("users[0"),
                arguments("users."),
                arguments("users[0]x"));
    }

    static Stream<Arguments> malformedDocuments() {
        return Stream.of(
                arguments("truncated array", "{\"x\":[1,2", "x"),
                arguments("leading zero", "{\"x\":01}", "x"),
                arguments("trailing comma in array", "{\"x\":[1,]}", "x"),
                arguments("trailing comma in object", "{\"x\":1,}", "x"),
                arguments("unknown escape", "{\"x\":\"\\q\"}", "x"),
                arguments("trailing content", "{\"x\":1} 2", "x"),
                arguments("nesting past 32", "[".repeat(34) + "]".repeat(34), ""));
    }

    static Stream<Arguments> invalidControlAndEscapeStrings() {
        return Stream.of(
                arguments("raw control character", "{\"x\":\"a\u0001\"}"),
                arguments("non-hex digit", "{\"x\":\"\\u12G4\"}"),
                arguments("short escape", "{\"x\":\"\\u12\"}"),
                arguments("unterminated", "{\"x\":\"abc"),
                arguments("escape at end", "{\"x\":\"a\\"));
    }

    static Stream<Arguments> invalidNumbers() {
        return Stream.of(
                arguments("no digit after the point", "{\"x\":1.}"),
                arguments("no exponent digits", "{\"x\":1e}"),
                arguments("no signed exponent digits", "{\"x\":1e+}"),
                arguments("sign only", "{\"x\":-}"),
                arguments("leading zero after sign", "{\"x\":-01}"));
    }

    static Stream<Arguments> exponents() {
        return Stream.of(
                arguments("{\"x\":1E-2}", 0.01),
                arguments("{\"x\":1e+2}", 100.0),
                arguments("{\"x\":-2.5e1}", -25.0));
    }

    static Stream<Arguments> malformedMembersAndArrays() {
        return Stream.of(
                arguments("{\"x\" 1}", "x", "no colon"),
                arguments("{\"x\"}", "x", "member without value"),
                arguments("{1:2}", "x", "unquoted name"),
                arguments("{\"x\":1 \"y\":2}", "x", "missing comma"),
                arguments("[1 2]", "[0]", "missing comma in array"),
                arguments("[1,", "[0]", "truncated array"),
                arguments("{\"x\":", "x", "truncated member"),
                arguments("{\"x\":tru}", "x", "truncated literal"),
                arguments("{\"x\":fals}", "x", "truncated false"),
                arguments("{\"x\":nul}", "x", "truncated null"),
                arguments("{\"x\":x}", "x", "unknown token"));
    }

    static Stream<Arguments> locations() {
        return Stream.of(
                arguments("b[1].c", Json.TYPE_NULL),
                arguments("b[1].d", Json.TYPE_MISSING),
                arguments("a[0]", Json.TYPE_MISSING),
                arguments("a.x", Json.TYPE_MISSING),
                arguments("b[2147483648]", Json.TYPE_INVALID),
                arguments("n.x", Json.TYPE_MISSING));
    }

    @ParameterizedTest(name = "reports {1} for path \"{0}\"")
    @MethodSource("pathTypes")
    @DisplayName("Reports the type of each path")
    void shouldReportTheTypeOfEachDocumentPath(String path, int expected) {
        // When
        int type = type(path);

        // Then
        assertThat(type).isEqualTo(expected);
    }

    @Test
    @DisplayName("Counts the elements of an array")
    void shouldCountTheElementsOfAnArray() {
        // When & Then
        assertThat(Json.arraySize(DOCUMENT, DOCUMENT.length, "users")).isEqualTo(2);
    }

    @Test
    @DisplayName("Reads an integer member")
    void shouldReadAnIntegerMember() {
        // When & Then
        assertThat(Json.getInt(DOCUMENT, DOCUMENT.length, "users[0].id")).isEqualTo(1);
    }

    @Test
    @DisplayName("Reads a boolean member")
    void shouldReadABooleanMember() {
        // When & Then
        assertThat(Json.getBool(DOCUMENT, DOCUMENT.length, "users[1].active")).isFalse();
    }

    @ParameterizedTest(name = "reports missing for path \"{0}\"")
    @MethodSource("missingPaths")
    @DisplayName("Reports a missing value as missing")
    void shouldReportMissingValuesAsMissing(String path, int expected) {
        // When
        int type = type(path);

        // Then
        assertThat(type).isEqualTo(expected);
    }

    @ParameterizedTest(name = "reports invalid for path \"{0}\"")
    @MethodSource("malformedPaths")
    @DisplayName("Reports a malformed path as invalid")
    void shouldReportMalformedPathsAsInvalid(String path) {
        // When
        int type = type(path);

        // Then
        assertThat(type).isEqualTo(Json.TYPE_INVALID);
    }

    @Test
    @DisplayName("Returns zero for an integer too large for an int")
    void shouldReturnZeroForAnIntegerTooLargeForAnInt() {
        // When & Then
        assertThat(Json.getInt(DOCUMENT, DOCUMENT.length, "users[1].id")).isZero();
    }

    @Test
    @DisplayName("Reads an integer too large for an int as a long")
    void shouldReadAnIntegerTooLargeForAnIntAsALong() {
        // When & Then
        assertThat(Json.getLong(DOCUMENT, DOCUMENT.length, "users[1].id")).isEqualTo(2147483648L);
    }

    @Test
    @DisplayName("Reads a negative integer")
    void shouldReadANegativeInteger() {
        // When & Then
        assertThat(Json.getInt(DOCUMENT, DOCUMENT.length, "numbers[0]")).isEqualTo(-12);
    }

    @Test
    @DisplayName("Returns zero for a number with an exponent read as an int")
    void shouldReturnZeroForANumberWithAnExponentReadAsAnInt() {
        // When & Then
        assertThat(Json.getInt(DOCUMENT, DOCUMENT.length, "numbers[1]")).isZero();
    }

    @Test
    @DisplayName("Reads the minimum long value")
    void shouldReadTheMinimumLongValue() {
        // When & Then
        assertThat(Json.getLong(DOCUMENT, DOCUMENT.length, "minimum")).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    @DisplayName("Returns zero for an integer too large for a long")
    void shouldReturnZeroForAnIntegerTooLargeForALong() {
        // When & Then
        assertThat(Json.getLong(DOCUMENT, DOCUMENT.length, "tooLarge")).isZero();
    }

    @Test
    @DisplayName("Reads a double with an exponent")
    void shouldReadADoubleWithAnExponent() {
        // When & Then
        assertThat(Json.getDouble(DOCUMENT, DOCUMENT.length, "numbers[1]")).isEqualTo(125.0);
    }

    @Test
    @DisplayName("Reads a decimal fraction")
    void shouldReadADecimalFraction() {
        // When & Then
        assertThat(Json.getDouble(DOCUMENT, DOCUMENT.length, "numbers[2]")).isEqualTo(0.1);
    }

    @Test
    @DisplayName("Preserves the sign of negative zero")
    void shouldPreserveTheSignOfNegativeZero() {
        // When
        double value = Json.getDouble(DOCUMENT, DOCUMENT.length, "numbers[3]");

        // Then
        assertThat(Double.doubleToRawLongBits(value)).isEqualTo(Double.doubleToRawLongBits(-0.0));
    }

    @Test
    @DisplayName("Returns zero for an overflowing double")
    void shouldReturnZeroForAnOverflowingDouble() {
        // When & Then
        assertThat(Json.getDouble(DOCUMENT, DOCUMENT.length, "numbers[4]")).as("overflow").isZero();
    }

    @Test
    @DisplayName("Returns zero for an underflowing double")
    void shouldReturnZeroForAnUnderflowingDouble() {
        // When & Then
        assertThat(Json.getDouble(DOCUMENT, DOCUMENT.length, "numbers[5]")).as("underflow").isZero();
    }

    @Test
    @DisplayName("Approximates a large power of ten, which rounds")
    void shouldApproximateALargePowerOfTen() {
        // When & Then
        assertThat(Json.getDouble(DOCUMENT, DOCUMENT.length, "numbers[6]"))
                .as("the power of ten is built by repeated multiplication, so it rounds")
                .isCloseTo(1.5e300, withinPercentage(1e-12));
    }

    @Test
    @DisplayName("Returns zero when a double is read from an array")
    void shouldReturnZeroWhenADoubleIsReadFromAnArray() {
        // When & Then
        assertThat(Json.getDouble(DOCUMENT, DOCUMENT.length, "users")).isZero();
    }

    @Test
    @DisplayName("Reports the bytes written for a string")
    void shouldReportTheBytesWrittenForAString() {
        // Given
        byte[] out = new byte[16];

        // When
        int written = Json.getString(DOCUMENT, DOCUMENT.length, "users[1].name", out, out.length);

        // Then
        assertThat(written).isEqualTo(8);
    }

    @Test
    @DisplayName("Decodes a string to UTF-8")
    void shouldDecodeAStringToUtf8() {
        // Given
        byte[] out = new byte[16];
        int written = Json.getString(DOCUMENT, DOCUMENT.length, "users[1].name", out, out.length);

        // When
        String decoded = new String(out, 0, written, StandardCharsets.UTF_8);

        // Then
        assertThat(decoded).isEqualTo("A\né\uD83D\uDE00");
    }

    @ParameterizedTest(name = "a capacity of {0} bytes writes {1}")
    @CsvSource({"4, 4", "5, 4"})
    @DisplayName("Stops before a character that does not fit in the buffer")
    void shouldStopBeforeACharacterThatDoesNotFit(int capacity, int expected) {
        // When
        int written = Json.getString(DOCUMENT, DOCUMENT.length, "users[1].name", new byte[16], capacity);

        // Then
        assertThat(written).as("stops before a character that does not fit").isEqualTo(expected);
    }

    @Test
    @DisplayName("Writes a short string fully")
    void shouldWriteAShortStringFully() {
        // When & Then
        assertThat(Json.getString(DOCUMENT, DOCUMENT.length, "text", new byte[16], 16)).isEqualTo(3);
    }

    @Test
    @DisplayName("Writes nothing for a non-string value")
    void shouldWriteNothingForANonStringValue() {
        // When & Then
        assertThat(Json.getString(DOCUMENT, DOCUMENT.length, "numbers", new byte[16], 16)).isZero();
    }

    @Test
    @DisplayName("Writes nothing into an empty buffer")
    void shouldWriteNothingIntoAnEmptyBuffer() {
        // When & Then
        assertThat(Json.getString(DOCUMENT, DOCUMENT.length, "text", new byte[0], 0)).isZero();
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("malformedDocuments")
    @DisplayName("Rejects malformed or truncated documents")
    void shouldRejectMalformedOrTruncatedDocuments(String description, String document, String path) {
        // When
        int type = typeOf(document, path);

        // Then
        assertThat(type).isEqualTo(Json.TYPE_INVALID);
    }

    @Test
    @DisplayName("Accepts nesting up to 33 levels as an array")
    void shouldAcceptNestingUpTo33LevelsAsAnArray() {
        // When & Then
        assertThat(typeOf("[".repeat(33) + "]".repeat(33), "")).isEqualTo(Json.TYPE_ARRAY);
    }

    @Test
    @DisplayName("Rejects a negative document length")
    void shouldRejectANegativeDocumentLength() {
        // When & Then
        assertThat(Json.type(DOCUMENT, -1, "")).isEqualTo(Json.TYPE_INVALID);
    }

    @Test
    @DisplayName("Types the elements of a root array")
    void shouldTypeTheElementsOfARootArray() {
        // Given
        byte[] root = bytes("  [true, 3] ");

        // When & Then
        assertThat(Json.type(root, root.length, "[0]")).isEqualTo(Json.TYPE_BOOLEAN);
    }

    @Test
    @DisplayName("Reads a boolean element of a root array")
    void shouldReadABooleanElementOfARootArray() {
        // Given
        byte[] root = bytes("  [true, 3] ");

        // When & Then
        assertThat(Json.getBool(root, root.length, "[0]")).isTrue();
    }

    @Test
    @DisplayName("Reads an integer element of a root array")
    void shouldReadAnIntegerElementOfARootArray() {
        // Given
        byte[] root = bytes("  [true, 3] ");

        // When & Then
        assertThat(Json.getInt(root, root.length, "[1]")).isEqualTo(3);
    }

    @Test
    @DisplayName("Counts the elements of a root array")
    void shouldCountTheElementsOfARootArray() {
        // Given
        byte[] root = bytes("  [true, 3] ");

        // When & Then
        assertThat(Json.arraySize(root, root.length, "")).isEqualTo(2);
    }

    @Test
    @DisplayName("Returns minus one for the size of a scalar element")
    void shouldReturnMinusOneForTheSizeOfAScalarElement() {
        // Given
        byte[] root = bytes("  [true, 3] ");

        // When & Then
        assertThat(Json.arraySize(root, root.length, "[1]")).isEqualTo(-1);
    }

    @Test
    @DisplayName("Reports the bytes written for short escapes")
    void shouldReportTheBytesWrittenForShortEscapes() {
        // Given
        byte[] buffer = bytes("{\"e\":\"\\b\\f\\r\\t\\/\\\\\\u0041\"}");
        byte[] out = new byte[16];

        // When
        int written = Json.getString(buffer, buffer.length, "e", out, out.length);

        // Then
        assertThat(written).isEqualTo(7);
    }

    @Test
    @DisplayName("Decodes every short escape and ASCII unicode escape")
    void shouldDecodeEveryShortEscapeAndAsciiUnicodeEscape() {
        // Given
        byte[] buffer = bytes("{\"e\":\"\\b\\f\\r\\t\\/\\\\\\u0041\"}");
        byte[] out = new byte[16];
        int written = Json.getString(buffer, buffer.length, "e", out, out.length);

        // When
        byte[] decoded = Arrays.copyOf(out, written);

        // Then
        assertThat(decoded).containsExactly(8, 12, 13, 9, '/', '\\', 'A');
    }

    @Test
    @DisplayName("Encodes a three-byte character as UTF-8")
    void shouldEncodeAThreeByteCharacterAsUtf8() {
        // Given
        byte[] buffer = bytes("{\"e\":\"\\u20AC\",\"high\":\"\\uD83D\",\"low\":\"\\uDE00\",\"pair\":\"\\uD83D\\u0041\"}");
        byte[] out = new byte[8];
        int written = Json.getString(buffer, buffer.length, "e", out, out.length);

        // When
        byte[] decoded = Arrays.copyOf(out, written);

        // Then
        assertThat(decoded).containsExactly((byte) 0xE2, (byte) 0x82, (byte) 0xAC);
    }

    @Test
    @DisplayName("Scans lone surrogates as text")
    void shouldScanLoneSurrogatesAsText() {
        // Given
        byte[] buffer = bytes("{\"e\":\"\\u20AC\",\"high\":\"\\uD83D\",\"low\":\"\\uDE00\",\"pair\":\"\\uD83D\\u0041\"}");

        // When
        int type = Json.type(buffer, buffer.length, "high");

        // Then
        assertThat(type).as("surrogates are scanned as text").isEqualTo(Json.TYPE_STRING);
    }

    @ParameterizedTest(name = "rejects {1}")
    @CsvSource({"high, unpaired high", "low, unpaired low", "pair, high then non-low"})
    @DisplayName("Rejects a lone or badly paired surrogate")
    void shouldRejectALoneOrBadlyPairedSurrogate(String member, String description) {
        // Given
        byte[] buffer = bytes("{\"e\":\"\\u20AC\",\"high\":\"\\uD83D\",\"low\":\"\\uDE00\",\"pair\":\"\\uD83D\\u0041\"}");

        // When
        int written = Json.getString(buffer, buffer.length, member, new byte[8], 8);

        // Then
        assertThat(written).isZero();
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidControlAndEscapeStrings")
    @DisplayName("Rejects control characters, bad escapes and truncated strings")
    void shouldRejectControlCharactersBadEscapesAndTruncatedStrings(String description, String document) {
        // When
        int type = typeOf(document, "x");

        // Then
        assertThat(type).isEqualTo(Json.TYPE_INVALID);
    }

    @ParameterizedTest(name = "reads {0}")
    @MethodSource("exponents")
    @DisplayName("Reads signed exponents")
    void shouldReadSignedExponents(String document, double expected) {
        // When
        double value = getDouble(bytes(document), "x");

        // Then
        assertThat(value).isCloseTo(expected, withinPercentage(1e-12));
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidNumbers")
    @DisplayName("Rejects incomplete numbers")
    void shouldRejectIncompleteNumbers(String description, String document) {
        // When
        int type = typeOf(document, "x");

        // Then
        assertThat(type).isEqualTo(Json.TYPE_INVALID);
    }

    @Test
    @DisplayName("Reads a long mantissa approximately")
    void shouldReadALongMantissaApproximately() {
        // Given
        byte[] buffer = bytes("{\"long\":0.1234567890123456789012345,\"big\":1e999999999,"
                + "\"small\":1e-999999999,\"wide\":123456789012345678901234567890,\"zero\":-0e5}");

        // When
        double value = getDouble(buffer, "long");

        // Then
        assertThat(value).isCloseTo(0.12345678901234568, withinPercentage(1e-12));
    }

    @Test
    @DisplayName("Returns zero for an overflowing exponent")
    void shouldReturnZeroForAnOverflowingExponent() {
        // Given
        byte[] buffer = bytes("{\"long\":0.1234567890123456789012345,\"big\":1e999999999,"
                + "\"small\":1e-999999999,\"wide\":123456789012345678901234567890,\"zero\":-0e5}");

        // When & Then
        assertThat(getDouble(buffer, "big")).as("overflow exponent").isZero();
    }

    @Test
    @DisplayName("Returns zero for an underflowing exponent")
    void shouldReturnZeroForAnUnderflowingExponent() {
        // Given
        byte[] buffer = bytes("{\"long\":0.1234567890123456789012345,\"big\":1e999999999,"
                + "\"small\":1e-999999999,\"wide\":123456789012345678901234567890,\"zero\":-0e5}");

        // When & Then
        assertThat(getDouble(buffer, "small")).as("underflow exponent").isZero();
    }

    @Test
    @DisplayName("Returns zero for an integer wider than 64 bits")
    void shouldReturnZeroForAnIntegerWiderThan64Bits() {
        // Given
        byte[] buffer = bytes("{\"long\":0.1234567890123456789012345,\"big\":1e999999999,"
                + "\"small\":1e-999999999,\"wide\":123456789012345678901234567890,\"zero\":-0e5}");

        // When & Then
        assertThat(getLong(buffer, "wide")).as("wider than 64 bits").isZero();
    }

    @Test
    @DisplayName("Reads a negative zero exponent as zero")
    void shouldReadANegativeZeroExponentAsZero() {
        // Given
        byte[] buffer = bytes("{\"long\":0.1234567890123456789012345,\"big\":1e999999999,"
                + "\"small\":1e-999999999,\"wide\":123456789012345678901234567890,\"zero\":-0e5}");

        // When & Then
        assertThat(getDouble(buffer, "zero")).isZero();
    }

    @ParameterizedTest(name = "locates \"{0}\"")
    @MethodSource("locations")
    @DisplayName("Locates members and elements or reports their failure")
    void shouldLocateMembersAndElementsOrReportTheirFailure(String path, int expected) {
        // Given
        byte[] buffer = bytes("{\"a\":1,\"b\":[true,{\"c\":null}],\"n\":{}}");

        // When
        int type = Json.type(buffer, buffer.length, path);

        // Then
        assertThat(type).isEqualTo(expected);
    }

    @Test
    @DisplayName("Returns minus one for the size of an object")
    void shouldReturnMinusOneForTheSizeOfAnObject() {
        // Given
        byte[] buffer = bytes("{\"a\":1,\"b\":[true,{\"c\":null}],\"n\":{}}");

        // When & Then
        assertThat(Json.arraySize(buffer, buffer.length, "n")).isEqualTo(-1);
    }

    @Test
    @DisplayName("Returns minus one for the size of a missing value")
    void shouldReturnMinusOneForTheSizeOfAMissingValue() {
        // Given
        byte[] buffer = bytes("{\"a\":1,\"b\":[true,{\"c\":null}],\"n\":{}}");

        // When & Then
        assertThat(Json.arraySize(buffer, buffer.length, "missing")).isEqualTo(-1);
    }

    @ParameterizedTest(name = "rejects {1}")
    @MethodSource("malformedMembersAndArrays")
    @DisplayName("Rejects malformed members and arrays")
    void shouldRejectMalformedMembersAndArrays(String document, String path, String description) {
        // When
        int type = typeOf(document, path);

        // Then
        assertThat(type).isEqualTo(Json.TYPE_INVALID);
    }

    @Test
    @DisplayName("Types an array with whitespace around its tokens")
    void shouldTypeAnArrayWithWhitespaceAroundItsTokens() {
        // Given
        byte[] buffer = bytes("\r\n{\t\"x\"\r\n:\t[ \r\n]\t,\"y\":false\r\n}\r\n");

        // When & Then
        assertThat(Json.type(buffer, buffer.length, "x")).isEqualTo(Json.TYPE_ARRAY);
    }

    @Test
    @DisplayName("Counts zero elements in an empty array with whitespace")
    void shouldCountZeroElementsInAnEmptyArrayWithWhitespace() {
        // Given
        byte[] buffer = bytes("\r\n{\t\"x\"\r\n:\t[ \r\n]\t,\"y\":false\r\n}\r\n");

        // When & Then
        assertThat(Json.arraySize(buffer, buffer.length, "x")).isZero();
    }

    @Test
    @DisplayName("Reads a boolean member past whitespace")
    void shouldReadABooleanMemberPastWhitespace() {
        // Given
        byte[] buffer = bytes("\r\n{\t\"x\"\r\n:\t[ \r\n]\t,\"y\":false\r\n}\r\n");

        // When & Then
        assertThat(Json.getBool(buffer, buffer.length, "y")).isFalse();
    }

    @Test
    @DisplayName("Types the root object past surrounding whitespace")
    void shouldTypeTheRootObjectPastSurroundingWhitespace() {
        // Given
        byte[] buffer = bytes("\r\n{\t\"x\"\r\n:\t[ \r\n]\t,\"y\":false\r\n}\r\n");

        // When & Then
        assertThat(Json.type(buffer, buffer.length, "")).isEqualTo(Json.TYPE_OBJECT);
    }

    private static int type(String path) {
        return Json.type(DOCUMENT, DOCUMENT.length, path);
    }

    private static double getDouble(byte[] buffer, String path) {
        return Json.getDouble(buffer, buffer.length, path);
    }

    private static long getLong(byte[] buffer, String path) {
        return Json.getLong(buffer, buffer.length, path);
    }

    private static int typeOf(String document, String path) {
        byte[] buffer = bytes(document);
        return Json.type(buffer, buffer.length, path);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
