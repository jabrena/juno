package io.github.jabrena.juno.jdk.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionalTest {

    @Test
    @DisplayName("a present Optional answers its value")
    void presentValue() {
        Optional<String> present = Optional.of("board");

        assertThat(present.isPresent()).isTrue();
        assertThat(present.isEmpty()).isFalse();
        assertThat(present.get()).isEqualTo("board");
        assertThat(present.orElseThrow()).isEqualTo("board");
        assertThat(present.orElse("other")).isEqualTo("board");
        assertThat(Optional.ofNullable("board").get()).isEqualTo("board");
    }

    @Test
    @DisplayName("an empty Optional is the one shared instance, and answers the alternative")
    void emptyValue() {
        Optional<String> empty = Optional.ofNullable(null);

        assertThat(empty).isSameAs(Optional.empty());
        assertThat(empty.isPresent()).isFalse();
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.orElse("other")).isEqualTo("other");
        assertThatThrownBy(empty::get).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(empty::orElseThrow).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("of rejects null, as the JDK does")
    void ofRejectsNull() {
        assertThatThrownBy(() -> Optional.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("an OptionalLong answers its value, or the alternative when empty")
    void optionalLong() {
        OptionalLong present = OptionalLong.of(42L);
        OptionalLong empty = OptionalLong.empty();

        assertThat(present.isPresent()).isTrue();
        assertThat(present.isEmpty()).isFalse();
        assertThat(present.getAsLong()).isEqualTo(42L);
        assertThat(present.orElse(-1L)).isEqualTo(42L);
        assertThat(empty.isPresent()).isFalse();
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.orElse(-1L)).isEqualTo(-1L);
        assertThatThrownBy(empty::getAsLong).isInstanceOf(NoSuchElementException.class);
    }
}
