package io.github.jabrena.juno.intrinsic;

/** Every hardware operation Juno understands, independent of its Java method signature or Arduino lowering. */
public enum Intrinsic {
    GPIO_PIN_MODE,
    GPIO_DIGITAL_WRITE,
    GPIO_DIGITAL_READ,
    GPIO_ANALOG_READ,
    GPIO_ANALOG_WRITE,
    GPIO_TOGGLE,
    DELAY_MILLIS,
    DELAY_MICROS,
    CLOCK_MILLIS,
    CLOCK_MICROS,
    DIGITAL_OUTPUT_OF,
    DIGITAL_OUTPUT_HIGH,
    DIGITAL_OUTPUT_LOW,
    DIGITAL_OUTPUT_TOGGLE,
    DIGITAL_OUTPUT_IS_HIGH,
    LED_MATRIX_BEGIN,
    LED_MATRIX_LOAD_FRAME,
    LED_MATRIX_CLEAR,
    SERIAL_BEGIN,
    SERIAL_PRINT,
    SERIAL_PRINTLN,
    SERIAL_PRINT_STRING,
    SERIAL_PRINTLN_STRING,
    MOUSE_BEGIN,
    MOUSE_MOVE,
    WIFI_BEGIN,
    WIFI_STATUS
}
