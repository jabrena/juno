package io.github.jabrena.juno.backend.h8300;

import java.util.Map;

/**
 * Text on the display of {@link RcxRemoteFirmware}: the four seven-segment digits show {@code JUno} after power-on, a
 * number from command 0x72, or the text the Arduino writes with command 0x82, one character per message. Letters are
 * approximated as seven-segment displays do ({@code VIEW} shows as {@code UIEu}, {@code PRGM} as {@code PrG∩}); a
 * character the font lacks shows blank. While text shows, the display shows nothing else but the IR cones.
 */
final class RcxDisplayText {
    /** Command 0x82: position 0-3, left to right, then an ASCII character; any other position blanks all four. */
    static final String SET_CHARACTER = "set_character";
    /** The bytes of LCD memory the four digits use, from {@link RcxRom#LCD_DISPLAY_MEMORY}. */
    static final int LCD_BYTES = 9;

    /** The four characters shown, ASCII. */
    private static final int TEXT = 0xC110;
    /** Non-zero while the display shows {@link #TEXT}. */
    private static final int TEXT_SHOWN = 0xC114;
    /** Non-zero while the display was explicitly cleared and must remain blank. */
    static final int DISPLAY_BLANK = 0xC11A;
    private static final int POSITIONS = 4;
    private static final String FONT_TABLE = "font";
    private static final String SEGMENTS = "abcdefg";
    /**
     * Each digit's segments, left to right, as {@code {byte offset, bit}} for the top, top right, bottom right,
     * bottom, bottom left, top left and middle segment (brickOS's {@code LCD_4_*} to {@code LCD_1_*}).
     */
    private static final int[][][] DIGITS = {
        {{3, 4}, {7, 1}, {7, 3}, {3, 7}, {7, 7}, {7, 5}, {3, 5}},
        {{3, 0}, {8, 1}, {8, 3}, {3, 3}, {8, 7}, {8, 5}, {3, 1}},
        {{1, 0}, {5, 1}, {5, 3}, {1, 3}, {6, 7}, {6, 5}, {1, 1}},
        {{0, 4}, {4, 1}, {4, 3}, {0, 7}, {5, 7}, {5, 5}, {0, 5}}};
    /**
     * The characters' segments, as the letters of {@link #SEGMENTS} (top, top right, ... middle) that are lit. A
     * lower-case letter missing here shows as its upper case.
     */
    private static final Map<Character, String> FONT = Map.ofEntries(
            Map.entry(' ', ""), Map.entry('-', "g"), Map.entry('_', "d"), Map.entry('=', "dg"),
            Map.entry('0', "abcdef"), Map.entry('1', "bc"), Map.entry('2', "abdeg"), Map.entry('3', "abcdg"),
            Map.entry('4', "bcfg"), Map.entry('5', "acdfg"), Map.entry('6', "acdefg"), Map.entry('7', "abc"),
            Map.entry('8', "abcdefg"), Map.entry('9', "abcdfg"),
            Map.entry('A', "abcefg"), Map.entry('B', "cdefg"), Map.entry('C', "adef"), Map.entry('D', "bcdeg"),
            Map.entry('E', "adefg"), Map.entry('F', "aefg"), Map.entry('G', "acdef"), Map.entry('H', "bcefg"),
            Map.entry('I', "ef"), Map.entry('J', "bcde"), Map.entry('K', "bcefg"), Map.entry('L', "def"),
            Map.entry('M', "abcef"), Map.entry('N', "ceg"), Map.entry('O', "abcdef"), Map.entry('P', "abefg"),
            Map.entry('Q', "abcfg"), Map.entry('R', "eg"), Map.entry('S', "acdfg"), Map.entry('T', "defg"),
            Map.entry('U', "bcdef"), Map.entry('V', "bcdef"), Map.entry('W', "cde"), Map.entry('X', "bcefg"),
            Map.entry('Y', "bcdfg"), Map.entry('Z', "abdeg"),
            Map.entry('c', "deg"), Map.entry('h', "cefg"), Map.entry('i', "e"), Map.entry('o', "cdeg"),
            Map.entry('u', "cde"));
    /** {@code JUno}, written during startup and when no explicit number or text replaces it. */
    private static final int[] BRAND = lcdBytes("JUno");

    private final RcxCode code;
    private final H8Assembler assembler;

    RcxDisplayText(RcxCode code) {
        this.code = code;
        this.assembler = code.assembler();
    }

    /** The segments of {@code character}, bit 0 the top segment to bit 6 the middle one; 0 for a blank. */
    static int segments(char character) {
        String lit = FONT.get(character);
        if (lit == null) {
            lit = FONT.getOrDefault(Character.toUpperCase(character), "");
        }
        int mask = 0;
        for (int index = 0; index < lit.length(); index++) {
            mask |= 1 << SEGMENTS.indexOf(lit.charAt(index));
        }
        return mask;
    }

    /** The nine LCD memory bytes that show the four characters of {@code text}. */
    static int[] lcdBytes(String text) {
        int[] bytes = new int[LCD_BYTES];
        for (int digit = 0; digit < POSITIONS; digit++) {
            int mask = segments(text.charAt(digit));
            for (int segment = 0; segment < SEGMENTS.length(); segment++) {
                if ((mask & 1 << segment) != 0) {
                    int[] bit = DIGITS[digit][segment];
                    bytes[bit[0]] |= 1 << bit[1];
                }
            }
        }
        return bytes;
    }

    /** Writes {@code JUno} straight into the LCD memory. Inline code that changes r6. */
    void drawBrand() {
        for (int i = 0; i < LCD_BYTES; i++) {
            assembler.moveImmediate(BRAND[i], H8ByteRegister.R6L);
            assembler.storeByte(H8ByteRegister.R6L, RcxRom.LCD_DISPLAY_MEMORY + i);
        }
    }

    /**
     * Writes {@link #TEXT} straight into the LCD memory, each character's segments looked up in the font and placed
     * on its digit's bits. Inline code that changes r0-r2, r4 and r6.
     */
    void drawText() {
        assembler.moveImmediate(0, H8ByteRegister.R6L);
        for (int i = 0; i < LCD_BYTES; i++) {
            assembler.storeByte(H8ByteRegister.R6L, RcxRom.LCD_DISPLAY_MEMORY + i);
        }
        for (int digit = 0; digit < POSITIONS; digit++) {
            assembler.loadByte(TEXT + digit, H8ByteRegister.R4L);
            assembler.and(0x7F, H8ByteRegister.R4L);
            assembler.moveImmediate(0, H8ByteRegister.R4H);
            assembler.loadByteIndexed(FONT_TABLE, H8Register.R4, H8ByteRegister.R1L);
            for (int segment = 0; segment < SEGMENTS.length(); segment++) {
                String dark = code.newLabel();
                int[] bit = DIGITS[digit][segment];
                assembler.move(H8ByteRegister.R1L, H8ByteRegister.R0L);
                assembler.and(1 << segment, H8ByteRegister.R0L);
                assembler.branch(H8Condition.EQUAL, dark);
                assembler.loadByte(RcxRom.LCD_DISPLAY_MEMORY + bit[0], H8ByteRegister.R2L);
                assembler.or(1 << bit[1], H8ByteRegister.R2L);
                assembler.storeByte(H8ByteRegister.R2L, RcxRom.LCD_DISPLAY_MEMORY + bit[0]);
                assembler.label(dark);
            }
        }
    }

    /** Branches to {@code target} if text shows. Inline code that changes r0. */
    void branchIfTextShown(String target) {
        assembler.load(TEXT_SHOWN, H8Register.R0);
        assembler.branch(H8Condition.NOT_EQUAL, target);
    }

    /** Jumps to {@code target} while text shows. Inline code that changes r0. */
    void jumpIfTextShown(String target) {
        assembler.load(TEXT_SHOWN, H8Register.R0);
        code.jumpIf(H8Condition.NOT_EQUAL, target);
    }

    /** Stops showing text, when a number replaces it. Changes r6. */
    void hideText() {
        code.clearWord(TEXT_SHOWN);
    }

    /** Stops retaining an explicitly blank display before a number, text, or icon is shown. */
    void hideBlankDisplay() {
        code.clearWord(DISPLAY_BLANK);
    }

    /** Jumps to {@code target} while the display was explicitly cleared. */
    void jumpIfDisplayBlank(String target) {
        assembler.load(DISPLAY_BLANK, H8Register.R0);
        code.jumpIf(H8Condition.NOT_EQUAL, target);
    }

    /** The command handler, which clears the number word {@code numberShown}, and the font table. */
    void handlers(int numberShown) {
        setCharacter(numberShown);
        int[] font = new int[0x80];
        for (char character = 0; character < font.length; character++) {
            font[character] = segments(character);
        }
        assembler.label(FONT_TABLE);
        assembler.data(font);
    }

    private void setCharacter(int numberShown) {
        String blank = code.newLabel();
        String done = code.newLabel();
        String show = code.newLabel();
        assembler.label(SET_CHARACTER);
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R2L);
        assembler.moveImmediate(0, H8ByteRegister.R2H);
        assembler.compareImmediate(POSITIONS, H8ByteRegister.R2L);
        assembler.branch(H8Condition.CARRY_CLEAR, blank);
        assembler.loadByte(RcxCode.REQUEST + 2, H8ByteRegister.R0L);
        assembler.storeByteIndexed(H8ByteRegister.R0L, TEXT, H8Register.R2);
        assembler.branch(H8Condition.ALWAYS, show);
        assembler.label(blank);
        assembler.moveImmediate(' ', H8ByteRegister.R0L);
        for (int position = 0; position < POSITIONS; position++) {
            assembler.storeByte(H8ByteRegister.R0L, TEXT + position);
        }
        code.clearWord(TEXT_SHOWN);
        assembler.moveImmediate(1, H8Register.R0);
        assembler.store(H8Register.R0, DISPLAY_BLANK);
        assembler.branch(H8Condition.ALWAYS, done);
        assembler.label(show);
        hideBlankDisplay();
        assembler.moveImmediate(1, H8Register.R0);
        assembler.store(H8Register.R0, TEXT_SHOWN);
        assembler.label(done);
        code.clearWord(numberShown);
        code.replyWith(1);
    }
}
