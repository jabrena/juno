package io.github.jabrena.juno.ai;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.internal.NativeDecisionModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionModelTest {
    @Test
    void mcuHandsOutTheSharedDecisionModelEngine() {
        assertThat(MCU.getDecisionModel()).isSameAs(NativeDecisionModel.decisionModel());
    }

    @Test
    void evaluationIsAnIntrinsicOverCallerOwnedBuffers() throws NoSuchMethodException {
        var method = NativeDecisionModel.class.getMethod("evaluate", int[].class, int[].class);

        assertThat(Modifier.isNative(method.getModifiers())).isTrue();
        assertThat(DecisionModel.class.getMethod("evaluate", int[].class, int[].class).getReturnType())
                .isEqualTo(int.class);
    }

    @Test
    void modelsAreLoadedFromTheFirmwareOrAnSdCard() throws NoSuchMethodException {
        assertThat(DecisionModel.class.getMethod("begin").getReturnType()).isEqualTo(ModelStatus.class);
        assertThat(DecisionModel.class.getMethod("begin", SdCard.class, String.class).getReturnType())
                .isEqualTo(ModelStatus.class);
        assertThat(DecisionModel.class.getMethod("release").getReturnType()).isEqualTo(void.class);
    }
}
