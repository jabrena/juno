package io.github.jabrena.juno.intrinsic;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.Map;
import java.util.Optional;

/** The single place that resolves a Java {@link MethodRef} to the {@link Intrinsic} it implements. */
public final class IntrinsicRegistry {
    private static final Map<MethodRef, Intrinsic> METHODS = Map.ofEntries(
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Gpio", "pinMode", "(II)V"),
                    Intrinsic.GPIO_PIN_MODE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Gpio", "digitalWrite", "(IZ)V"),
                    Intrinsic.GPIO_DIGITAL_WRITE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Gpio", "digitalRead", "(I)Z"),
                    Intrinsic.GPIO_DIGITAL_READ),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Gpio", "analogRead", "(I)I"),
                    Intrinsic.GPIO_ANALOG_READ),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Gpio", "analogWrite", "(II)V"),
                    Intrinsic.GPIO_ANALOG_WRITE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Gpio", "toggle", "(I)V"),
                    Intrinsic.GPIO_TOGGLE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Delay", "millis", "(I)V"),
                    Intrinsic.DELAY_MILLIS),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Delay", "micros", "(I)V"),
                    Intrinsic.DELAY_MICROS),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Clock", "millis", "()I"),
                    Intrinsic.CLOCK_MILLIS),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Clock", "micros", "()I"),
                    Intrinsic.CLOCK_MICROS),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/DigitalOutput", "of",
                    "(I)Lio/github/jabrena/juno/api/DigitalOutput;"), Intrinsic.DIGITAL_OUTPUT_OF),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/DigitalOutput", "high", "()V"),
                    Intrinsic.DIGITAL_OUTPUT_HIGH),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/DigitalOutput", "low", "()V"),
                    Intrinsic.DIGITAL_OUTPUT_LOW),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/DigitalOutput", "toggle", "()V"),
                    Intrinsic.DIGITAL_OUTPUT_TOGGLE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/DigitalOutput", "isHigh", "()Z"),
                    Intrinsic.DIGITAL_OUTPUT_IS_HIGH),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/led/LedMatrix", "begin", "()V"),
                    Intrinsic.LED_MATRIX_BEGIN),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/led/LedMatrix", "loadFrame", "(III)V"),
                    Intrinsic.LED_MATRIX_LOAD_FRAME),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/led/LedMatrix", "clear", "()V"),
                    Intrinsic.LED_MATRIX_CLEAR),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Serial", "begin", "(I)V"),
                    Intrinsic.SERIAL_BEGIN),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Serial", "print", "(I)V"),
                    Intrinsic.SERIAL_PRINT),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Serial", "println", "(I)V"),
                    Intrinsic.SERIAL_PRINTLN),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Serial", "print", "(Ljava/lang/String;)V"),
                    Intrinsic.SERIAL_PRINT_STRING),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Serial", "println", "(Ljava/lang/String;)V"),
                    Intrinsic.SERIAL_PRINTLN_STRING),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Mouse", "begin", "()V"),
                    Intrinsic.MOUSE_BEGIN),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Mouse", "move", "(II)V"),
                    Intrinsic.MOUSE_MOVE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/net/Wifi", "begin",
                    "(Ljava/lang/String;Ljava/lang/String;)V"), Intrinsic.WIFI_BEGIN),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/net/Wifi", "status", "()I"),
                    Intrinsic.WIFI_STATUS),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/net/HttpClient", "get",
                    "(Ljava/lang/String;ILjava/lang/String;[BI)I"), Intrinsic.HTTP_GET),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/net/HttpClient", "post",
                    "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;[BI)I"), Intrinsic.HTTP_POST),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/net/Json", "getInt",
                    "([BILjava/lang/String;)I"), Intrinsic.JSON_GET_INT),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/net/Json", "getBool",
                    "([BILjava/lang/String;)Z"), Intrinsic.JSON_GET_BOOL),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/net/Json", "getString",
                    "([BILjava/lang/String;[BI)I"), Intrinsic.JSON_GET_STRING));

    private IntrinsicRegistry() {
    }

    public static Optional<Intrinsic> resolve(MethodRef method) {
        return Optional.ofNullable(METHODS.get(method));
    }

    public static boolean isIntrinsic(MethodRef method) {
        return METHODS.containsKey(method);
    }
}
