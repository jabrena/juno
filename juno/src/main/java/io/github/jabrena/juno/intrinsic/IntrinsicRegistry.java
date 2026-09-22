package io.github.jabrena.juno.intrinsic;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The single place that resolves a Java {@link MethodRef} to the {@link Intrinsic} it implements. */
public final class IntrinsicRegistry {
    /**
     * (intrinsic, declared-parameter-index) pairs exempt from the default rule that every
     * intrinsic {@code String} parameter must be a compile-time literal. {@link
     * io.github.jabrena.juno.api.io.net.http.HttpServer#respond(int, String, String)}'s {@code body}
     * (parameter index 2, after {@code status} and {@code contentType}) is the only one: it
     * already works identically whether the argument is a literal (a {@code .asciz} address) or a
     * runtime value (a pooled {@code String}'s address) — both are plain null-terminated {@code
     * const char*} to the generated {@code juno_http_server_respond} shim, so there is nothing
     * literal-specific about it, unlike e.g. {@code Wifi#begin}'s credentials, which must never be
     * a runtime value by design.
     */
    private static final Set<IntrinsicParameter> RUNTIME_STRING_PARAMETERS = Set.of(
            new IntrinsicParameter(Intrinsic.HTTP_SERVER_RESPOND, 2));

    private record IntrinsicParameter(Intrinsic intrinsic, int parameterIndex) {
    }

    private static final Map<MethodRef, Intrinsic> METHODS = Map.ofEntries(
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/Gpio", "pinMode", "(II)V"),
                    Intrinsic.GPIO_PIN_MODE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/Gpio", "digitalWrite", "(IZ)V"),
                    Intrinsic.GPIO_DIGITAL_WRITE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/Gpio", "digitalRead", "(I)Z"),
                    Intrinsic.GPIO_DIGITAL_READ),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/Gpio", "analogRead", "(I)I"),
                    Intrinsic.GPIO_ANALOG_READ),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/Gpio", "analogWrite", "(II)V"),
                    Intrinsic.GPIO_ANALOG_WRITE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/Gpio", "toggle", "(I)V"),
                    Intrinsic.GPIO_TOGGLE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Delay", "millis", "(I)V"),
                    Intrinsic.DELAY_MILLIS),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Delay", "micros", "(I)V"),
                    Intrinsic.DELAY_MICROS),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Clock", "millis", "()I"),
                    Intrinsic.CLOCK_MILLIS),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Clock", "micros", "()I"),
                    Intrinsic.CLOCK_MICROS),
            Map.entry(new MethodRef("java/lang/String", "valueOf", "(I)Ljava/lang/String;"),
                    Intrinsic.STRING_VALUE_OF_INT),
            Map.entry(new MethodRef("java/lang/String", "valueOf", "(D)Ljava/lang/String;"),
                    Intrinsic.STRING_VALUE_OF_DOUBLE),
            Map.entry(new MethodRef("java/lang/String", "length", "()I"),
                    Intrinsic.STRING_LENGTH),
            Map.entry(new MethodRef("java/lang/String", "charAt", "(I)C"),
                    Intrinsic.STRING_CHAR_AT),
            Map.entry(new MethodRef("java/lang/String", "equals", "(Ljava/lang/Object;)Z"),
                    Intrinsic.STRING_EQUALS),
            Map.entry(new MethodRef("java/lang/StringBuilder", "<init>", "(I)V"),
                    Intrinsic.STRING_BUILDER_NEW),
            Map.entry(new MethodRef("java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;"),
                    Intrinsic.STRING_BUILDER_APPEND_CHAR),
            Map.entry(new MethodRef("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"),
                    Intrinsic.STRING_BUILDER_APPEND_STRING),
            Map.entry(new MethodRef("java/lang/StringBuilder", "toString", "()Ljava/lang/String;"),
                    Intrinsic.STRING_BUILDER_TO_STRING),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/DigitalOutput", "of",
                    "(I)Lio/github/jabrena/juno/api/io/DigitalOutput;"), Intrinsic.DIGITAL_OUTPUT_OF),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/DigitalOutput", "high", "()V"),
                    Intrinsic.DIGITAL_OUTPUT_HIGH),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/DigitalOutput", "low", "()V"),
                    Intrinsic.DIGITAL_OUTPUT_LOW),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/DigitalOutput", "toggle", "()V"),
                    Intrinsic.DIGITAL_OUTPUT_TOGGLE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/DigitalOutput", "isHigh", "()Z"),
                    Intrinsic.DIGITAL_OUTPUT_IS_HIGH),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/led/LedMatrix", "begin", "()V"),
                    Intrinsic.LED_MATRIX_BEGIN),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/led/LedMatrix", "loadFrame", "(III)V"),
                    Intrinsic.LED_MATRIX_LOAD_FRAME),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/led/LedMatrix", "clear", "()V"),
                    Intrinsic.LED_MATRIX_CLEAR),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/usb/Serial", "begin", "(I)V"),
                    Intrinsic.SERIAL_BEGIN),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/usb/Serial", "print", "(I)V"),
                    Intrinsic.SERIAL_PRINT),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/usb/Serial", "println", "(I)V"),
                    Intrinsic.SERIAL_PRINTLN),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/usb/Serial", "print", "(Ljava/lang/String;)V"),
                    Intrinsic.SERIAL_PRINT_STRING),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/usb/Serial", "println", "(Ljava/lang/String;)V"),
                    Intrinsic.SERIAL_PRINTLN_STRING),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/hid/Mouse", "begin", "()V"),
                    Intrinsic.MOUSE_BEGIN),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/hid/Mouse", "move", "(II)V"),
                    Intrinsic.MOUSE_MOVE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/Wifi", "begin",
                    "(Ljava/lang/String;Ljava/lang/String;)V"), Intrinsic.WIFI_BEGIN),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/Wifi", "status", "()I"),
                    Intrinsic.WIFI_STATUS),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/Wifi", "localIP", "([I)V"),
                    Intrinsic.WIFI_LOCAL_IP),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpClient", "get",
                    "(Ljava/lang/String;ILjava/lang/String;[BI[BI[I)I"), Intrinsic.HTTP_GET),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpClient", "post",
                    "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;[BI[BI[I)I"), Intrinsic.HTTP_POST),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpClient", "delete",
                    "(Ljava/lang/String;ILjava/lang/String;[BI[BI[I)I"), Intrinsic.HTTP_DELETE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpClient", "patch",
                    "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;[BI[BI[I)I"), Intrinsic.HTTP_PATCH),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpClient", "query",
                    "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;[BI[BI[I)I"), Intrinsic.HTTP_QUERY),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpsClient", "get",
                    "(Ljava/lang/String;ILjava/lang/String;[BI[BI[I)I"), Intrinsic.HTTPS_GET),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpsClient", "post",
                    "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;[BI[BI[I)I"), Intrinsic.HTTPS_POST),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpsClient", "delete",
                    "(Ljava/lang/String;ILjava/lang/String;[BI[BI[I)I"), Intrinsic.HTTPS_DELETE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpsClient", "patch",
                    "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;[BI[BI[I)I"), Intrinsic.HTTPS_PATCH),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpsClient", "query",
                    "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;[BI[BI[I)I"), Intrinsic.HTTPS_QUERY),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/Json", "type",
                    "([BILjava/lang/String;)I"), Intrinsic.JSON_TYPE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/Json", "getInt",
                    "([BILjava/lang/String;)I"), Intrinsic.JSON_GET_INT),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/Json", "getLong",
                    "([BILjava/lang/String;)J"), Intrinsic.JSON_GET_LONG),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/Json", "getDouble",
                    "([BILjava/lang/String;)D"), Intrinsic.JSON_GET_DOUBLE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/Json", "getBool",
                    "([BILjava/lang/String;)Z"), Intrinsic.JSON_GET_BOOL),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/Json", "getString",
                    "([BILjava/lang/String;[BI)I"), Intrinsic.JSON_GET_STRING),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/Json", "getString",
                    "([BILjava/lang/String;)Ljava/lang/String;"), Intrinsic.JSON_GET_STRING_VALUE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/Json", "arraySize",
                    "([BILjava/lang/String;)I"), Intrinsic.JSON_ARRAY_SIZE),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpServer", "begin", "(I)V"),
                    Intrinsic.HTTP_SERVER_BEGIN),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpServer", "accept", "([BI)I"),
                    Intrinsic.HTTP_SERVER_ACCEPT),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpServer", "method", "()Ljava/lang/String;"),
                    Intrinsic.HTTP_SERVER_METHOD),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpServer", "path", "()Ljava/lang/String;"),
                    Intrinsic.HTTP_SERVER_PATH),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpServer", "respond",
                    "(ILjava/lang/String;Ljava/lang/String;)V"), Intrinsic.HTTP_SERVER_RESPOND),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/io/net/http/HttpServer", "respond",
                    "(ILjava/lang/String;Ljava/lang/StringBuilder;)V"), Intrinsic.HTTP_SERVER_RESPOND_BUILDER),
            Map.entry(new MethodRef("io/github/jabrena/juno/api/Memory", "arenaUsedBytes", "()I"),
                    Intrinsic.MEMORY_ARENA_USED));

    private IntrinsicRegistry() {
    }

    public static Optional<Intrinsic> resolve(MethodRef method) {
        return Optional.ofNullable(METHODS.get(method));
    }

    public static boolean isIntrinsic(MethodRef method) {
        return METHODS.containsKey(method);
    }

    /** Whether {@code intrinsic}'s {@code String} parameter at {@code parameterIndex} must be a compile-time literal. */
    public static boolean requiresLiteralStringArgument(Intrinsic intrinsic, int parameterIndex) {
        return !RUNTIME_STRING_PARAMETERS.contains(new IntrinsicParameter(intrinsic, parameterIndex));
    }
}
