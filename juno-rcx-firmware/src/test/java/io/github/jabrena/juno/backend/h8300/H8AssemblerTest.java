package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Checks representative H8/300 machine-code encodings. */
class H8AssemblerTest {
    private final H8Assembler assembler = new H8Assembler(0x8000);

    @Test
    void encodesWordMovesAndStackOperations() {
        assembler.label("start");
        assembler.push(H8Register.R0);
        assembler.subs(2, H8Register.SP);
        assembler.moveImmediate(0xC000, H8Register.R0);
        assembler.load(0x0000, H8Register.R6);
        assembler.store(H8Register.R6, 0xFD90);
        assembler.move(H8Register.SP, H8Register.R6);
        assembler.loadIndirect(H8Register.SP, H8Register.R6);
        assembler.adds(2, H8Register.SP);
        assembler.pop(H8Register.R0);

        assertThat(hex(assembler.assemble("start"))).isEqualTo("6df0" + "1b87" + "7900c000" + "6b060000" + "6b86fd90"
                + "0d76" + "6976" + "0b87" + "6d70");
    }

    @Test
    void encodesArithmeticAndCalls() {
        assembler.label("start");
        assembler.add(H8Register.R1, H8Register.R2);
        assembler.subtract(H8Register.R1, H8Register.R0);
        assembler.compare(H8Register.R3, H8Register.R4);
        assembler.adds(1, H8Register.R5);
        assembler.call(0x0436);
        assembler.returnFromSubroutine();

        assertThat(hex(assembler.assemble("start"))).isEqualTo("0912" + "1910" + "1d34" + "0b05" + "5e000436" + "5470");
    }

    @Test
    void encodesByteOperations() {
        assembler.label("start");
        assembler.moveImmediate(0x80, H8ByteRegister.R2L);
        assembler.move(H8ByteRegister.R1L, H8ByteRegister.R2H);
        assembler.loadByte(0xC060, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R1L, 0xC070);
        assembler.and(0xF7, H8ByteRegister.R0L);
        assembler.or(0x01, H8ByteRegister.R3H);
        assembler.xor(0x80, H8ByteRegister.R3L);
        assembler.compareImmediate(0x40, H8ByteRegister.R2L);
        assembler.not(H8ByteRegister.R1L);
        assembler.divideUnsigned(H8ByteRegister.R3L, H8Register.R0);

        assertThat(hex(assembler.assemble("start"))).isEqualTo("fa80" + "0c92" + "6a08c060" + "6a89c070" + "e8f7"
                + "c301" + "db80" + "aa40" + "1709" + "51b0");
    }

    @Test
    void encodesIndexedMovesAndDataTables() {
        assembler.label("start");
        assembler.loadIndexed(4, H8Register.R4, H8Register.R1);
        assembler.storeIndexed(H8Register.R1, 4, H8Register.R4);
        assembler.loadByteIndexed(6, H8Register.R4, H8ByteRegister.R1L);
        assembler.storeByteIndexed(H8ByteRegister.R1L, 6, H8Register.R4);
        assembler.loadByteIndexed("table", H8Register.R0, H8ByteRegister.R1L);
        assembler.label("table");
        assembler.data(0x20, 0xA0);

        assertThat(hex(assembler.assemble("start"))).isEqualTo("6f410004" + "6fc10004" + "6e490006" + "6ec90006"
                + "6e098014" + "20a0");
    }

    @Test
    void namesTheHalvesOfAWordRegister() {
        assertThat(H8ByteRegister.high(H8Register.R5)).isEqualTo(H8ByteRegister.R5H);
        assertThat(H8ByteRegister.low(H8Register.R5)).isEqualTo(H8ByteRegister.R5L);
        assertThat(H8Condition.EQUAL.opposite()).isEqualTo(H8Condition.NOT_EQUAL);
        assertThat(H8Condition.LESS.opposite()).isEqualTo(H8Condition.GREATER_OR_EQUAL);
    }

    @Test
    void resolvesBackwardBranchesAsInFirstS() {
        assembler.label("wait_release");
        assembler.move(H8Register.SP, H8Register.R6);
        assembler.push(H8Register.R6);
        assembler.moveImmediate(0x4000, H8Register.R6);
        assembler.call(0x29F2);
        assembler.adds(2, H8Register.SP);
        assembler.loadIndirect(H8Register.SP, H8Register.R6);
        assembler.branch(H8Condition.EQUAL, "wait_release");

        assertThat(hex(assembler.assemble("wait_release"))).endsWith("6976" + "47ee");
    }

    @Test
    void resolvesForwardLabelsForBranchesJumpsAndCalls() {
        assembler.label("start");
        assembler.branch(H8Condition.ALWAYS, "end");
        assembler.jump("end");
        assembler.call("end");
        assembler.label("end");
        assembler.returnFromSubroutine();

        assertThat(hex(assembler.assemble("start"))).isEqualTo("4008" + "5a00800a" + "5e00800a" + "5470");
    }

    @Test
    void writesStringsWithATerminatingZero() {
        assembler.label("start");
        assembler.string("Hi");

        assertThat(hex(assembler.assemble("start"))).isEqualTo("486900");
    }

    @Test
    void rejectsUndefinedAndDuplicateLabels() {
        assembler.label("start");
        assembler.jump("nowhere");

        assertThatThrownBy(() -> assembler.assemble("start")).isInstanceOf(FirmwareGenerationException.class)
                .hasMessageContaining("nowhere");
        assertThatThrownBy(() -> assembler.label("start")).isInstanceOf(FirmwareGenerationException.class)
                .hasMessageContaining("defined twice");
    }

    @Test
    void rejectsBranchesOutOfRange() {
        assembler.label("start");
        assembler.branch(H8Condition.ALWAYS, "far");
        for (int i = 0; i < 64; i++) {
            assembler.returnFromSubroutine();
        }
        assembler.label("far");

        assertThatThrownBy(() -> assembler.assemble("start")).isInstanceOf(FirmwareGenerationException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void rejectsValuesThatDoNotFitTheirField() {
        assertThatThrownBy(() -> assembler.moveImmediate(0x10000, H8Register.R0))
                .isInstanceOf(FirmwareGenerationException.class);
        assertThatThrownBy(() -> assembler.adds(4, H8Register.R0))
                .isInstanceOf(FirmwareGenerationException.class);
    }

    private static String hex(H8Image image) {
        return HexFormat.of().formatHex(image.bytes());
    }
}
