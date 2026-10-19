package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Checks representative H8/300 machine-code encodings. */
@DisplayName("H8/300 assembler")
class H8AssemblerTest {
    private final H8Assembler assembler = new H8Assembler(0x8000);

    @Nested
    @DisplayName("Encoding instructions")
    class Encoding {

        @Test
        @DisplayName("Encodes word moves and stack operations")
        void shouldEncodeWordMovesAndStackOperations() {
            // Given
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

            // When
            String encoded = hex(assembler.assemble("start"));

            // Then
            assertThat(encoded).isEqualTo("6df0" + "1b87" + "7900c000" + "6b060000" + "6b86fd90"
                    + "0d76" + "6976" + "0b87" + "6d70");
        }

        @Test
        @DisplayName("Encodes arithmetic and calls")
        void shouldEncodeArithmeticAndCalls() {
            // Given
            assembler.label("start");
            assembler.add(H8Register.R1, H8Register.R2);
            assembler.subtract(H8Register.R1, H8Register.R0);
            assembler.compare(H8Register.R3, H8Register.R4);
            assembler.adds(1, H8Register.R5);
            assembler.call(0x0436);
            assembler.call(H8Register.R6);
            assembler.returnFromSubroutine();

            // When
            String encoded = hex(assembler.assemble("start"));

            // Then
            assertThat(encoded).isEqualTo("0912" + "1910" + "1d34" + "0b05" + "5e000436" + "5d60" + "5470");
        }

        @Test
        @DisplayName("Encodes byte operations")
        void shouldEncodeByteOperations() {
            // Given
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

            // When
            String encoded = hex(assembler.assemble("start"));

            // Then
            assertThat(encoded).isEqualTo("fa80" + "0c92" + "6a08c060" + "6a89c070" + "e8f7"
                    + "c301" + "db80" + "aa40" + "1709" + "51b0");
        }

        @Test
        @DisplayName("Encodes indexed moves and data tables")
        void shouldEncodeIndexedMovesAndDataTables() {
            // Given
            assembler.label("start");
            assembler.loadIndexed(4, H8Register.R4, H8Register.R1);
            assembler.storeIndexed(H8Register.R1, 4, H8Register.R4);
            assembler.loadByteIndexed(6, H8Register.R4, H8ByteRegister.R1L);
            assembler.storeByteIndexed(H8ByteRegister.R1L, 6, H8Register.R4);
            assembler.loadByteIndexed("table", H8Register.R0, H8ByteRegister.R1L);
            assembler.label("table");
            assembler.data(0x20, 0xA0);

            // When
            String encoded = hex(assembler.assemble("start"));

            // Then
            assertThat(encoded).isEqualTo("6f410004" + "6fc10004" + "6e490006" + "6ec90006"
                    + "6e098014" + "20a0");
        }

        @Test
        @DisplayName("Names the halves of a word register and the opposite of a condition")
        void shouldNameTheHalvesOfAWordRegisterAndTheOppositeOfACondition() {
            // When & Then
            assertThat(H8ByteRegister.high(H8Register.R5)).isEqualTo(H8ByteRegister.R5H);
            assertThat(H8ByteRegister.low(H8Register.R5)).isEqualTo(H8ByteRegister.R5L);
            assertThat(H8Condition.EQUAL.opposite()).isEqualTo(H8Condition.NOT_EQUAL);
            assertThat(H8Condition.LESS.opposite()).isEqualTo(H8Condition.GREATER_OR_EQUAL);
        }
    }

    @Nested
    @DisplayName("Resolving labels and data")
    class Labels {

        @Test
        @DisplayName("Resolves backward branches as in FirstS")
        void shouldResolveBackwardBranchesAsInFirstS() {
            // Given
            assembler.label("wait_release");
            assembler.move(H8Register.SP, H8Register.R6);
            assembler.push(H8Register.R6);
            assembler.moveImmediate(0x4000, H8Register.R6);
            assembler.call(0x29F2);
            assembler.adds(2, H8Register.SP);
            assembler.loadIndirect(H8Register.SP, H8Register.R6);
            assembler.branch(H8Condition.EQUAL, "wait_release");

            // When
            String encoded = hex(assembler.assemble("wait_release"));

            // Then
            assertThat(encoded).endsWith("6976" + "47ee");
        }

        @Test
        @DisplayName("Resolves forward labels for branches, jumps and calls")
        void shouldResolveForwardLabelsForBranchesJumpsAndCalls() {
            // Given
            assembler.label("start");
            assembler.branch(H8Condition.ALWAYS, "end");
            assembler.jump("end");
            assembler.call("end");
            assembler.label("end");
            assembler.returnFromSubroutine();

            // When
            String encoded = hex(assembler.assemble("start"));

            // Then
            assertThat(encoded).isEqualTo("4008" + "5a00800a" + "5e00800a" + "5470");
        }

        @Test
        @DisplayName("Writes strings with a terminating zero")
        void shouldWriteStringsWithATerminatingZero() {
            // Given
            assembler.label("start");
            assembler.string("Hi");

            // When
            String encoded = hex(assembler.assemble("start"));

            // Then
            assertThat(encoded).isEqualTo("486900");
        }

        @Test
        @DisplayName("Pads data tables so the code after them stays aligned")
        void shouldPadDataTablesSoTheCodeAfterThemStaysAligned() {
            // Given
            assembler.label("start");
            assembler.data(1, 2, 3);
            assembler.returnFromSubroutine();

            // When
            String encoded = hex(assembler.assemble("start"));

            // Then
            assertThat(encoded).isEqualTo("010203" + "00" + "5470");
        }

        @Test
        @DisplayName("Rejects code labels at odd addresses")
        void shouldRejectCodeLabelsAtOddAddresses() {
            // Given
            // The H8/300 fetches instructions as words: a routine at an odd address never runs as written.
            assembler.label("start");
            assembler.call("routine");
            assembler.string("ab");
            assembler.label("routine");
            assembler.returnFromSubroutine();

            // When & Then
            assertThatThrownBy(() -> assembler.assemble("start")).isInstanceOf(FirmwareGenerationException.class)
                    .hasMessageContaining("odd address: routine");
        }

        @Test
        @DisplayName("Rejects undefined and duplicate labels")
        void shouldRejectUndefinedAndDuplicateLabels() {
            // Given
            assembler.label("start");
            assembler.jump("nowhere");

            // When & Then
            assertThatThrownBy(() -> assembler.assemble("start")).isInstanceOf(FirmwareGenerationException.class)
                    .hasMessageContaining("nowhere");
            assertThatThrownBy(() -> assembler.label("start")).isInstanceOf(FirmwareGenerationException.class)
                    .hasMessageContaining("defined twice");
        }
    }

    @Nested
    @DisplayName("Branch reach")
    class BranchReach {

        @Test
        @DisplayName("Rejects a branch that goes out of range")
        void shouldRejectABranchThatGoesOutOfRange() {
            // Given
            assembler.label("start");
            assembler.branch(H8Condition.ALWAYS, "far");
            for (int i = 0; i < 64; i++) {
                assembler.returnFromSubroutine();
            }
            assembler.label("far");

            // When & Then
            assertThatThrownBy(() -> assembler.assemble("start")).isInstanceOf(FirmwareGenerationException.class)
                    .hasMessageContaining("out of range");
        }

        @Test
        @DisplayName("Rejects branches that reach further than eight bits, backward and forward")
        void shouldRejectBranchesThatReachFurtherThanEightBits() {
            // Given
            assembler.label("back");
            for (int i = 0; i < 130; i++) {
                assembler.push(H8Register.R0);
            }
            assembler.branch(H8Condition.EQUAL, "back");

            H8Assembler forward = new H8Assembler(0x8000);
            forward.label("start");
            forward.branch(H8Condition.EQUAL, "far");
            for (int i = 0; i < 130; i++) {
                forward.push(H8Register.R0);
            }
            forward.label("far");

            // When & Then
            assertThatThrownBy(() -> assembler.assemble("back"))
                    .isInstanceOf(FirmwareGenerationException.class)
                    .hasMessageContaining("out of range");
            assertThatThrownBy(() -> forward.assemble("start"))
                    .isInstanceOf(FirmwareGenerationException.class)
                    .hasMessageContaining("out of range");
        }
    }

    @Nested
    @DisplayName("Immediate operands")
    class ImmediateOperands {

        @Test
        @DisplayName("Rejects values that do not fit their field")
        void shouldRejectValuesThatDoNotFitTheirField() {
            // When & Then
            assertThatThrownBy(() -> assembler.moveImmediate(0x10000, H8Register.R0))
                    .isInstanceOf(FirmwareGenerationException.class);
            assertThatThrownBy(() -> assembler.adds(4, H8Register.R0))
                    .isInstanceOf(FirmwareGenerationException.class);
        }

        @Test
        @DisplayName("Rejects byte immediates outside eight bits and accepts the lowest one")
        void shouldRejectByteImmediatesOutsideEightBitsAndAcceptTheLowestOne() {
            // When & Then
            assertThatThrownBy(() -> assembler.moveImmediate(0x100, H8ByteRegister.R2L))
                    .isInstanceOf(FirmwareGenerationException.class);
            assertThatThrownBy(() -> assembler.moveImmediate(-0x81, H8ByteRegister.R2L))
                    .isInstanceOf(FirmwareGenerationException.class);

            assembler.label("start");
            assembler.moveImmediate(-0x80, H8ByteRegister.R2L);
            assertThat(hex(assembler.assemble("start"))).isEqualTo("fa80");
        }

        @Test
        @DisplayName("Rejects word immediates outside sixteen bits")
        void shouldRejectWordImmediatesOutsideSixteenBits() {
            // When & Then
            assertThatThrownBy(() -> assembler.moveImmediate(0x10000, H8Register.R0))
                    .isInstanceOf(FirmwareGenerationException.class);
            assertThatThrownBy(() -> assembler.moveImmediate(-0x8001, H8Register.R0))
                    .isInstanceOf(FirmwareGenerationException.class);
        }
    }

    private static String hex(H8Image image) {
        return HexFormat.of().formatHex(image.bytes());
    }
}
