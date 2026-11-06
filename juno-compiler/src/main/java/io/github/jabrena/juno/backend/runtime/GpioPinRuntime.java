package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.linker.ThrowableTypes;

/** Emits the allocation-free GPIO name parser and its exception path. */
final class GpioPinRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();

    String helpers(CoreRuntime core, int illegalArgumentClassId) {
        return runtimeSource.load("common/gpio-pins.cpp")
                .replace("${JUNO_GPIO_DIGITAL_PINS}", core.gpioDigitalPins())
                .replace("${JUNO_GPIO_ANALOG_PINS}", core.gpioAnalogPins())
                .replace("${JUNO_GPIO_ADDITIONAL_DIGITAL_PINS}", core.gpioAdditionalDigitalPins())
                .replace("${JUNO_GPIO_ADDITIONAL_ANALOG_PINS}", core.gpioAdditionalAnalogPins())
                .replace("${JUNO_THROWABLE_BYTES}", Integer.toString(ThrowableTypes.HEADER_BYTES))
                .replace("${JUNO_CLASS_ID_WORD}", Integer.toString(ThrowableTypes.CLASS_ID_OFFSET / Integer.BYTES))
                .replace("${JUNO_MESSAGE_WORD}", Integer.toString(ThrowableTypes.MESSAGE_OFFSET / Integer.BYTES))
                .replace("${JUNO_ILLEGAL_ARGUMENT_EXCEPTION_CLASS_ID}", Integer.toString(illegalArgumentClassId));
    }
}
