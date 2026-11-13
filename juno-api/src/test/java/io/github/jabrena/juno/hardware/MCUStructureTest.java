package io.github.jabrena.juno.hardware;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MCU holds no state and allocates nothing per call")
class MCUStructureTest {

    @Test
    @DisplayName("MCU is a final, non-instantiable facade with no fields")
    void shouldBeAFinalNonInstantiableFacadeWithoutFields() throws NoSuchMethodException {
        Constructor<MCU> constructor = MCU.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(MCU.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
        assertThat(MCU.class.getDeclaredFields()).isEmpty();
    }

    @Test
    @DisplayName("Every public MCU method is a static getter with no parameters")
    void shouldExposeOnlyStaticParameterlessGetters() {
        for (Method method : MCU.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            assertThat(Modifier.isStatic(method.getModifiers())).as(method.getName()).isTrue();
            assertThat(method.getParameterCount()).as(method.getName()).isZero();
            assertThat(method.getName()).startsWith("get");
        }
    }
}
