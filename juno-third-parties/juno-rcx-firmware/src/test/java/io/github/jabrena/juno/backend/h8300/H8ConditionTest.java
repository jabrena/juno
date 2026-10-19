package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("H8 branch conditions")
class H8ConditionTest {

    @ParameterizedTest
    @EnumSource(H8Condition.class)
    @DisplayName("Pairs every condition with its opposite, whose opcode differs in the low bit")
    void shouldPairEveryConditionWithItsOppositeWhoseOpcodeDiffersInTheLowBit(H8Condition condition) {
        // Given
        H8Condition opposite = condition.opposite();

        // When
        H8Condition twiceOpposite = opposite.opposite();

        // Then
        assertThat(opposite.opcode()).as("opposite of %s", condition).isEqualTo(condition.opcode() ^ 1);
        assertThat(twiceOpposite).as("twice the opposite of %s", condition).isEqualTo(condition);
    }
}
