package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class H8ConditionTest {

    @Test
    void theOppositeOfEveryConditionIsItsPartnerOpcode() {
        for (H8Condition condition : H8Condition.values()) {
            assertThat(condition.opposite().opcode()).as("opposite of %s", condition).isEqualTo(condition.opcode() ^ 1);
            assertThat(condition.opposite().opposite()).as("twice the opposite of %s", condition).isEqualTo(condition);
        }
    }
}
