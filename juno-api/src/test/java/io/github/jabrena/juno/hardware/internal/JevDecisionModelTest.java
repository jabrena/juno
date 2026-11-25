package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("JEV1 decision-model engine")
class JevDecisionModelTest {
    private static final String PATH = "/MODEL.JEV";

    private final FakeRuntime runtime = new FakeRuntime();
    private final JevDecisionModel engine = new JevDecisionModel(runtime);
    private final SdCard card = mock(SdCard.class);

    @Test
    @DisplayName("Uses the linked model and reports the runtime's status")
    void shouldUseTheLinkedModel() {
        assertThat(engine.begin()).isEqualTo(ModelStatus.OK);
        assertThat(engine.isLoaded()).isTrue();
        assertThat(engine.actions()).isEqualTo(9);
        assertThat(engine.features()).isEqualTo(4520);
        assertThat(runtime.calls).containsExactly("unload", "beginLinked");

        runtime.beginStatus = 2;
        assertThat(engine.begin()).isEqualTo(ModelStatus.INVALID_MODEL);
        runtime.beginStatus = 1;
        assertThat(engine.begin()).isEqualTo(ModelStatus.INVALID_HANDLE);
    }

    @Test
    @DisplayName("Copies an SD-card file into PSRAM in chunks and hands the buffer to the runtime")
    void shouldLoadAModelFromAnSdCard() {
        byte[] file = new byte[2500];
        Arrays.fill(file, (byte) 7);
        when(card.size(PATH)).thenReturn(file.length);
        when(card.open(PATH)).thenReturn(new ByteArrayInputStream(file));

        assertThat(engine.begin(card, PATH)).isEqualTo(ModelStatus.OK);

        assertThat(runtime.buffer).isEqualTo(file);
        assertThat(runtime.calls).containsExactly("unload", "allocate 2500", "write 0+1024", "write 1024+1024",
                "write 2048+452", "beginPsram 2500");
        assertThat(runtime.freed).isEmpty();
    }

    @Test
    @DisplayName("Reports a missing or empty file as unreadable without allocating")
    void shouldRejectAMissingFile() {
        when(card.size(PATH)).thenReturn(0);

        assertThat(engine.begin(card, PATH)).isEqualTo(ModelStatus.UNREADABLE);
        assertThat(runtime.calls).containsExactly("unload");
    }

    @Test
    @DisplayName("Reports a model that does not fit in PSRAM as exhausted")
    void shouldReportExhaustedPsram() {
        when(card.size(PATH)).thenReturn(64);
        runtime.handle = 0;

        assertThat(engine.begin(card, PATH)).isEqualTo(ModelStatus.EXHAUSTED);
        assertThat(runtime.freed).isEmpty();
    }

    @Test
    @DisplayName("Frees the buffer when the file cannot be opened, read or written")
    void shouldFreeTheBufferWhenTheCopyFails() {
        when(card.size(PATH)).thenReturn(64);

        when(card.open(PATH)).thenReturn(null);
        assertThat(engine.begin(card, PATH)).isEqualTo(ModelStatus.UNREADABLE);

        when(card.open(PATH)).thenReturn(new ByteArrayInputStream(new byte[10]));
        assertThat(engine.begin(card, PATH)).isEqualTo(ModelStatus.UNREADABLE);

        when(card.open(PATH)).thenReturn(new FailingInputStream());
        assertThat(engine.begin(card, PATH)).isEqualTo(ModelStatus.UNREADABLE);

        when(card.open(PATH)).thenReturn(new ByteArrayInputStream(new byte[64]));
        runtime.failWrites = true;
        assertThat(engine.begin(card, PATH)).isEqualTo(ModelStatus.UNREADABLE);

        assertThat(runtime.freed).containsExactly(42, 42, 42, 42);
    }

    @Test
    @DisplayName("Frees the buffer the runtime refuses as a model")
    void shouldFreeARefusedModel() {
        when(card.size(PATH)).thenReturn(64);
        when(card.open(PATH)).thenReturn(new ByteArrayInputStream(new byte[64]));
        runtime.beginStatus = 2;

        assertThat(engine.begin(card, PATH)).isEqualTo(ModelStatus.INVALID_MODEL);
        assertThat(runtime.freed).containsExactly(42);
    }

    @Test
    @DisplayName("Delegates evaluation and unloads on release")
    void shouldEvaluateAndRelease() {
        int[] state = new int[4520];
        int[] legalActions = {4, 8};
        engine.begin();

        assertThat(engine.evaluate(state, legalActions)).isEqualTo(8);
        engine.release();

        assertThat(engine.isLoaded()).isFalse();
        assertThat(engine.actions()).isZero();
        assertThat(engine.features()).isZero();
    }

    /** A runtime that records its calls and holds one PSRAM buffer in a Java array. */
    private static final class FakeRuntime implements DecisionModelRuntime {
        private final List<String> calls = new ArrayList<>();
        private final List<Integer> freed = new ArrayList<>();
        private int beginStatus;
        private int handle = 42;
        private boolean failWrites;
        private boolean loaded;
        private byte[] buffer = new byte[0];

        @Override
        public int beginLinked() {
            calls.add("beginLinked");
            loaded = beginStatus == 0;
            return beginStatus;
        }

        @Override
        public int beginPsram(int handle, int size) {
            calls.add("beginPsram " + size);
            loaded = beginStatus == 0;
            return beginStatus;
        }

        @Override
        public int actionCount() {
            return loaded ? 9 : 0;
        }

        @Override
        public int featureCount() {
            return loaded ? 4520 : 0;
        }

        @Override
        public int evaluate(int[] state, int[] legalActions) {
            return legalActions[legalActions.length - 1];
        }

        @Override
        public void unload() {
            calls.add("unload");
            loaded = false;
        }

        @Override
        public int allocate(int bytes) {
            calls.add("allocate " + bytes);
            buffer = new byte[bytes];
            return handle;
        }

        @Override
        public int write(int handle, int offset, byte[] source, int length) {
            calls.add("write " + offset + "+" + length);
            if (failWrites) {
                return -5;
            }
            System.arraycopy(source, 0, buffer, offset, length);
            return length;
        }

        @Override
        public void free(int handle) {
            freed.add(handle);
        }
    }

    private static final class FailingInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("card removed");
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            throw new IOException("card removed");
        }
    }
}
