package io.github.jabrena.juno.backend.h8300;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encodes H8/300 instructions into machine code placed from a fixed origin, the subset a replacement RCX firmware
 * needs so far: word and byte moves between registers, immediates, absolute addresses and the stack; word arithmetic;
 * byte logic, compares and an unsigned divide; conditional branches; and calls, including calls into the brick's ROM
 * at fixed addresses or through a register. Labels may be used before
 * they are defined; {@link #assemble} resolves them. The H8/300 is big-endian: every 16-bit field is written high
 * byte first.
 */
public final class H8Assembler {
    private final int origin;
    private final ByteArrayOutputStream code = new ByteArrayOutputStream();
    private final Map<String, Integer> labels = new HashMap<>();
    private final List<Fixup> fixups = new ArrayList<>();
    /** Labels that execution reaches, which the H8/300 needs at even addresses. */
    private final List<String> codeTargets = new ArrayList<>();

    public H8Assembler(int origin) {
        this.origin = checkedWord(origin, "origin");
    }

    /** The address the next instruction is placed at. */
    public int address() {
        return origin + code.size();
    }

    /** Defines {@code name} at the current address. */
    public void label(String name) {
        if (labels.putIfAbsent(name, address()) != null) {
            throw new FirmwareGenerationException("H8/300 label defined twice: " + name);
        }
    }

    /** {@code mov.w #value,rd}. */
    public void moveImmediate(int value, H8Register destination) {
        bytes(0x79, destination.number());
        word(checkedWord(value, "immediate"));
    }

    /** {@code mov.w rs,rd}. */
    public void move(H8Register source, H8Register destination) {
        bytes(0x0D, source.number() << 4 | destination.number());
    }

    /** {@code mov.w @address:16,rd}. */
    public void load(int address, H8Register destination) {
        bytes(0x6B, destination.number());
        word(checkedWord(address, "address"));
    }

    /** {@code mov.w rs,@address:16}. */
    public void store(H8Register source, int address) {
        bytes(0x6B, 0x80 | source.number());
        word(checkedWord(address, "address"));
    }

    /** {@code mov.w @rs,rd}. */
    public void loadIndirect(H8Register pointer, H8Register destination) {
        bytes(0x69, pointer.number() << 4 | destination.number());
    }

    /** {@code mov.w rs,@-r7}, which the H8/300 assemblers also spell {@code push}. */
    public void push(H8Register source) {
        bytes(0x6D, 0xF0 | source.number());
    }

    /** {@code mov.w @r7+,rd}, which the H8/300 assemblers also spell {@code pop}. */
    public void pop(H8Register destination) {
        bytes(0x6D, 0x70 | destination.number());
    }

    /** {@code add.w rs,rd}. */
    public void add(H8Register source, H8Register destination) {
        bytes(0x09, source.number() << 4 | destination.number());
    }

    /** {@code sub.w rs,rd}; unlike {@link #subs}, it sets the condition codes. */
    public void subtract(H8Register source, H8Register destination) {
        bytes(0x19, source.number() << 4 | destination.number());
    }

    /** {@code cmp.w rs,rd}: the condition codes of {@code rd - rs}. */
    public void compare(H8Register source, H8Register destination) {
        bytes(0x1D, source.number() << 4 | destination.number());
    }

    /** {@code adds #amount,rd} for an amount of 1 or 2; leaves the condition codes alone. */
    public void adds(int amount, H8Register destination) {
        bytes(0x0B, smallAmount(amount) | destination.number());
    }

    /** {@code subs #amount,rd} for an amount of 1 or 2; leaves the condition codes alone. */
    public void subs(int amount, H8Register destination) {
        bytes(0x1B, smallAmount(amount) | destination.number());
    }

    /** {@code mov.b #value,rd}. */
    public void moveImmediate(int value, H8ByteRegister destination) {
        bytes(0xF0 | destination.number(), checkedByte(value));
    }

    /** {@code mov.b rs,rd}; moving a register to itself sets the condition codes from it. */
    public void move(H8ByteRegister source, H8ByteRegister destination) {
        bytes(0x0C, source.number() << 4 | destination.number());
    }

    /** {@code mov.b @address:16,rd}. */
    public void loadByte(int address, H8ByteRegister destination) {
        bytes(0x6A, destination.number());
        word(checkedWord(address, "address"));
    }

    /** {@code mov.b rs,@address:16}. */
    public void storeByte(H8ByteRegister source, int address) {
        bytes(0x6A, 0x80 | source.number());
        word(checkedWord(address, "address"));
    }

    /** {@code mov.w @(displacement:16,rs),rd}. */
    public void loadIndexed(int displacement, H8Register base, H8Register destination) {
        bytes(0x6F, base.number() << 4 | destination.number());
        word(checkedWord(displacement, "displacement"));
    }

    /** {@code mov.w rs,@(displacement:16,rd)}. */
    public void storeIndexed(H8Register source, int displacement, H8Register base) {
        bytes(0x6F, 0x80 | base.number() << 4 | source.number());
        word(checkedWord(displacement, "displacement"));
    }

    /** {@code mov.b @(displacement:16,rs),rd}. */
    public void loadByteIndexed(int displacement, H8Register base, H8ByteRegister destination) {
        bytes(0x6E, base.number() << 4 | destination.number());
        word(checkedWord(displacement, "displacement"));
    }

    /** {@code mov.b @(table:16,rs),rd}: the byte {@code rs} bytes into the table at {@code table}. */
    public void loadByteIndexed(String table, H8Register index, H8ByteRegister destination) {
        bytes(0x6E, index.number() << 4 | destination.number());
        absolute(table);
    }

    /** {@code mov.b rs,@(displacement:16,rd)}. */
    public void storeByteIndexed(H8ByteRegister source, int displacement, H8Register base) {
        bytes(0x6E, 0x80 | base.number() << 4 | source.number());
        word(checkedWord(displacement, "displacement"));
    }

    /** {@code and.b #mask,rd}. */
    public void and(int mask, H8ByteRegister destination) {
        bytes(0xE0 | destination.number(), checkedByte(mask));
    }

    /** {@code or.b #mask,rd}. */
    public void or(int mask, H8ByteRegister destination) {
        bytes(0xC0 | destination.number(), checkedByte(mask));
    }

    /** {@code xor.b #mask,rd}. */
    public void xor(int mask, H8ByteRegister destination) {
        bytes(0xD0 | destination.number(), checkedByte(mask));
    }

    /** {@code cmp.b #value,rd}: the condition codes of {@code rd - value}. */
    public void compareImmediate(int value, H8ByteRegister destination) {
        bytes(0xA0 | destination.number(), checkedByte(value));
    }

    /** {@code not.b rd}. */
    public void not(H8ByteRegister destination) {
        bytes(0x17, destination.number());
    }

    /**
     * {@code divxu.b rs,rd}: divides the 16-bit {@code rd} by the 8-bit {@code rs}, leaving the quotient in the low
     * byte of {@code rd} and the remainder in its high byte; the quotient must fit eight bits.
     */
    public void divideUnsigned(H8ByteRegister divisor, H8Register dividend) {
        bytes(0x51, divisor.number() << 4 | dividend.number());
    }

    /** {@code bcc label}, reaching -128 to +127 bytes from the next instruction. */
    public void branch(H8Condition condition, String target) {
        codeTargets.add(target);
        bytes(condition.opcode(), 0);
        fixups.add(new Fixup(code.size() - 1, target, FixupKind.RELATIVE_8));
    }

    /** {@code jsr @address:16}, for a routine at a fixed address such as an RCX ROM entry point. */
    public void call(int address) {
        bytes(0x5E, 0);
        word(checkedWord(address, "address"));
    }

    /** {@code jsr @register}, used to enter a ROM vector loaded from memory. */
    public void call(H8Register register) {
        bytes(0x5D, register.number() << 4);
    }

    /** {@code jsr @label:16}. */
    public void call(String target) {
        codeTargets.add(target);
        bytes(0x5E, 0);
        absolute(target);
    }

    /** {@code jmp @label:16}. */
    public void jump(String target) {
        codeTargets.add(target);
        bytes(0x5A, 0);
        absolute(target);
    }

    /** {@code rts}. */
    public void returnFromSubroutine() {
        bytes(0x54, 0x70);
    }

    /**
     * Constant bytes, as the assemblers' {@code .byte}, padded with a zero to an even length: instructions placed
     * after a table must start at an even address, or the H8/300 cannot execute them.
     */
    public void data(int... values) {
        for (int value : values) {
            code.write(checkedByte(value));
        }
        if (code.size() % 2 != 0) {
            code.write(0);
        }
    }

    /** The bytes of {@code text} in ASCII followed by a zero byte, as the assemblers' {@code .string}. */
    public void string(String text) {
        code.writeBytes(text.getBytes(StandardCharsets.US_ASCII));
        code.write(0);
    }

    /** The machine code with every label resolved, starting at {@code entry}. */
    public H8Image assemble(String entry) {
        byte[] bytes = code.toByteArray();
        codeTargets.add(entry);
        for (String target : codeTargets) {
            if (addressOf(target) % 2 != 0) {
                throw new FirmwareGenerationException(
                        "H8/300 code label at an odd address: " + target + " (0x" + Integer.toHexString(addressOf(target)) + ")");
            }
        }
        for (Fixup fixup : fixups) {
            fixup.kind().patch(bytes, fixup.offset(), origin, addressOf(fixup.label()));
        }
        return new H8Image(origin, addressOf(entry), bytes);
    }

    private int addressOf(String label) {
        Integer address = labels.get(label);
        if (address == null) {
            throw new FirmwareGenerationException("Undefined H8/300 label: " + label);
        }
        return address;
    }

    private void absolute(String target) {
        fixups.add(new Fixup(code.size(), target, FixupKind.ABSOLUTE_16));
        word(0);
    }

    private void bytes(int first, int second) {
        code.write(first);
        code.write(second);
    }

    private void word(int value) {
        bytes(value >> 8 & 0xFF, value & 0xFF);
    }

    private static int smallAmount(int amount) {
        return switch (amount) {
            case 1 -> 0x00;
            case 2 -> 0x80;
            default -> throw new FirmwareGenerationException("adds/subs take #1 or #2 on the H8/300, not #" + amount);
        };
    }

    private static int checkedByte(int value) {
        if (value < -0x80 || value > 0xFF) {
            throw new FirmwareGenerationException("H8/300 byte immediate does not fit 8 bits: " + value);
        }
        return value & 0xFF;
    }

    private static int checkedWord(int value, String what) {
        if (value < -0x8000 || value > 0xFFFF) {
            throw new FirmwareGenerationException("H8/300 " + what + " does not fit 16 bits: " + value);
        }
        return value & 0xFFFF;
    }

    private record Fixup(int offset, String label, FixupKind kind) {
    }

    /** How a label's address is written into an instruction that referred to it before it was defined. */
    private enum FixupKind {
        /** A 16-bit absolute address. */
        ABSOLUTE_16 {
            @Override
            void patch(byte[] bytes, int offset, int origin, int target) {
                bytes[offset] = (byte) (target >> 8);
                bytes[offset + 1] = (byte) target;
            }
        },
        /** An 8-bit displacement from the end of the two-byte branch, whose last byte is at {@code offset}. */
        RELATIVE_8 {
            @Override
            void patch(byte[] bytes, int offset, int origin, int target) {
                int displacement = target - (origin + offset + 1);
                if (displacement < -128 || displacement > 127) {
                    throw new FirmwareGenerationException("H8/300 branch target out of range (" + displacement + " bytes)");
                }
                bytes[offset] = (byte) displacement;
            }
        };

        abstract void patch(byte[] bytes, int offset, int origin, int target);
    }
}
