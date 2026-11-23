package io.github.jabrena.juno.backend.thumb2;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.backend.WordSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Thumb-2 shim calls")
class AsmEmitterTest {
    private final AsmEmitter asm = new AsmEmitter(Map.of());
    private final FrameLayout frame = new FrameLayout(8, new int[] {0, 4}, new int[] {8, -1}, Map.of());

    private static List<WordSource> words(int count) {
        return IntStream.range(0, count).<WordSource>mapToObj(WordSource.Immediate::new).toList();
    }

    @Test
    @DisplayName("Calls through a dispatcher with the function's address in r12 and the arguments as for a direct call")
    void shouldCallThroughTheDispatcher() {
        // Given
        StringBuilder direct = new StringBuilder();
        StringBuilder dispatched = new StringBuilder();

        // When
        asm.emitShimCall(direct, frame, "juno_work", words(6));
        asm.emitShimCallThrough(dispatched, frame, "juno_native_dispatch", "juno_work", words(6));

        // Then
        assertThat(dispatched.toString()).isEqualTo(direct.toString()
                .replace("    bl juno_work\n", "    ldr r12, =juno_work\n    bl juno_native_dispatch\n"));
    }

    @Test
    @DisplayName("Refuses a dispatched call with more stack words than the dispatcher copies")
    void shouldRefuseTooManyDispatchedWords() {
        // Then
        assertThatThrownBy(() -> asm.emitShimCallThrough(new StringBuilder(), frame, "juno_native_dispatch",
                "juno_work", words(4 + AsmEmitter.DISPATCHED_STACK_WORDS + 1)))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("juno_work takes 21 argument words")
                .hasMessageContaining("at most 20");
    }
}
