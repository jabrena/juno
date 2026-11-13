package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.board.Capability;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.BigNumberMethods;
import io.github.jabrena.juno.intrinsic.Intrinsic;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Resolves the board capability a reachable intrinsic or public MCU service requires. */
final class CapabilityRequirements {
    private static final String MCU_OWNER = "io/github/jabrena/juno/hardware/MCU";

    private static final Map<String, Capability> MCU_SERVICE_CAPABILITIES = Map.ofEntries(
            Map.entry("Lio/github/jabrena/juno/hardware/hid/Mouse;", Capability.USB_HID),
            Map.entry("Lio/github/jabrena/juno/hardware/led/LedMatrix;", Capability.LED_MATRIX),
            Map.entry("Lio/github/jabrena/juno/hardware/net/Wifi;", Capability.ONBOARD_WIFI),
            Map.entry("Lio/github/jabrena/juno/hardware/net/BluetoothLe;", Capability.BLUETOOTH_LE),
            Map.entry("Lio/github/jabrena/juno/robotics/Accelerometer;", Capability.ONBOARD_IMU),
            Map.entry("Lio/github/jabrena/juno/robotics/Gyroscope;", Capability.ONBOARD_IMU),
            Map.entry("Lio/github/jabrena/juno/hardware/device/camera/Camera;", Capability.CAMERA),
            Map.entry("Lio/github/jabrena/juno/hardware/device/sd/SdCard;", Capability.ONBOARD_SD_CARD),
            Map.entry("Lio/github/jabrena/juno/hardware/display/Display;", Capability.M5_DISPLAY),
            Map.entry("Lio/github/jabrena/juno/hardware/keyboard/Keyboard;", Capability.M5_KEYBOARD),
            Map.entry("Lio/github/jabrena/juno/hardware/audio/Audio;", Capability.M5_AUDIO),
            Map.entry("Lio/github/jabrena/juno/hardware/power/Battery;", Capability.M5_BATTERY));

    private final Map<Intrinsic, Capability> intrinsicCapabilities = intrinsicCapabilities();

    Optional<Capability> forIntrinsic(Intrinsic intrinsic) {
        return Optional.ofNullable(intrinsicCapabilities.get(intrinsic));
    }

    Optional<Capability> forMethod(MethodRef method) {
        if (!method.owner().equals(MCU_OWNER)) {
            return Optional.empty();
        }
        int returnType = method.descriptor().lastIndexOf(')') + 1;
        return Optional.ofNullable(MCU_SERVICE_CAPABILITIES.get(method.descriptor().substring(returnType)));
    }

    private static Map<Intrinsic, Capability> intrinsicCapabilities() {
        Map<Intrinsic, Capability> required = new EnumMap<>(Intrinsic.class);
        require(required, Capability.UUID_RANDOM, Intrinsic.UUID_RANDOM);
        require(required, Capability.LED_MATRIX, Intrinsic.LED_MATRIX_BEGIN, Intrinsic.LED_MATRIX_LOAD_FRAME,
                Intrinsic.LED_MATRIX_COLUMNS, Intrinsic.LED_MATRIX_CLEAR);
        require(required, Capability.M5_DISPLAY, Intrinsic.M5_DISPLAY_BEGIN, Intrinsic.M5_DISPLAY_WIDTH,
                Intrinsic.M5_DISPLAY_HEIGHT, Intrinsic.M5_DISPLAY_FILL_SCREEN, Intrinsic.M5_DISPLAY_DRAW_PIXEL,
                Intrinsic.M5_DISPLAY_FILL_RECT, Intrinsic.M5_DISPLAY_SET_CURSOR, Intrinsic.M5_DISPLAY_SET_TEXT_COLOR,
                Intrinsic.M5_DISPLAY_SET_TEXT_SIZE, Intrinsic.M5_DISPLAY_PRINT_STRING,
                Intrinsic.M5_DISPLAY_PRINTLN_STRING);
        require(required, Capability.M5_KEYBOARD, Intrinsic.M5_KEYBOARD_BEGIN, Intrinsic.M5_KEYBOARD_UPDATE,
                Intrinsic.M5_KEYBOARD_IS_CHANGED, Intrinsic.M5_KEYBOARD_IS_PRESSED,
                Intrinsic.M5_KEYBOARD_IS_KEY_PRESSED, Intrinsic.M5_KEYBOARD_KEY_COUNT, Intrinsic.M5_KEYBOARD_KEY_AT,
                Intrinsic.M5_KEYBOARD_IS_SHIFT_PRESSED, Intrinsic.M5_KEYBOARD_IS_CONTROL_PRESSED,
                Intrinsic.M5_KEYBOARD_IS_ALT_PRESSED, Intrinsic.M5_KEYBOARD_IS_FN_PRESSED,
                Intrinsic.M5_KEYBOARD_IS_OPTION_PRESSED);
        require(required, Capability.M5_AUDIO, Intrinsic.M5_AUDIO_SET_VOLUME, Intrinsic.M5_AUDIO_STOP,
                Intrinsic.M5_AUDIO_TONE);
        require(required, Capability.M5_BATTERY, Intrinsic.M5_BATTERY_LEVEL_PERCENT,
                Intrinsic.M5_BATTERY_VOLTAGE_MILLIVOLTS);
        require(required, Capability.WIFI_ACCESS_POINT, Intrinsic.WIFI_BEGIN_AP);
        require(required, Capability.ONBOARD_SD_CARD, Intrinsic.BOARD_ONBOARD_SD_CHIP_SELECT);
        require(required, Capability.WIFI, Intrinsic.LOCAL_DATE_TIME_NOW);
        require(required, Capability.WIFI, Intrinsic.WIFI_BEGIN, Intrinsic.WIFI_STATUS, Intrinsic.WIFI_LOCAL_IP,
                Intrinsic.WIFI_GATEWAY_IP, Intrinsic.WIFI_MAC_ADDRESS, Intrinsic.DATAGRAM_SOCKET_NEW,
                Intrinsic.DATAGRAM_SOCKET_SEND, Intrinsic.DATAGRAM_SOCKET_RECEIVE,
                Intrinsic.DATAGRAM_SOCKET_SET_SO_TIMEOUT, Intrinsic.DATAGRAM_SOCKET_CLOSE, Intrinsic.SOCKET_NEW,
                Intrinsic.SOCKET_GET_OUTPUT_STREAM, Intrinsic.SOCKET_GET_INPUT_STREAM,
                Intrinsic.SOCKET_SET_SO_TIMEOUT, Intrinsic.SOCKET_CLOSE, Intrinsic.SOCKET_OUTPUT_STREAM_WRITE);
        require(required, Capability.HTTPS_CLIENT, Intrinsic.HTTPS_GET, Intrinsic.HTTPS_GET_PATH_BUFFER,
                Intrinsic.HTTPS_POST, Intrinsic.HTTPS_POST_BUFFER, Intrinsic.HTTPS_DELETE, Intrinsic.HTTPS_PATCH,
                Intrinsic.HTTPS_QUERY, Intrinsic.HTTPS_REQUEST_BUFFER);
        require(required, Capability.HTTP_CLIENT, Intrinsic.HTTP_REQUEST_BUFFER);
        require(required, Capability.WIFI, Intrinsic.HTTP_SET_TIMEOUT);
        require(required, Capability.WIFI, Intrinsic.HTTP_URL_GET, Intrinsic.HTTP_URL_GET_PATH_BUFFER,
                Intrinsic.HTTP_URL_POST, Intrinsic.HTTP_URL_POST_BUFFER, Intrinsic.HTTP_URL_DELETE,
                Intrinsic.HTTP_URL_PATCH, Intrinsic.HTTP_URL_QUERY);
        require(required, Capability.TLS_EMAIL, Intrinsic.SMTP_SEND_TLS, Intrinsic.POP3_MESSAGE_COUNT,
                Intrinsic.POP3_READ_LATEST, Intrinsic.POP3_READ_SUBJECT);
        require(required, Capability.HTTP_CLIENT, Intrinsic.HTTP_GET, Intrinsic.HTTP_GET_PATH_BUFFER,
                Intrinsic.HTTP_POST, Intrinsic.HTTP_POST_BUFFER, Intrinsic.HTTP_DELETE, Intrinsic.HTTP_PATCH,
                Intrinsic.HTTP_QUERY);
        require(required, Capability.SMTP_STARTTLS, Intrinsic.SMTP_SEND);
        require(required, Capability.USB_HID, Intrinsic.MOUSE_BEGIN, Intrinsic.MOUSE_MOVE);
        require(required, Capability.STRUCTURED_CONCURRENCY, Intrinsic.TASK_JOINER_ALL_SUCCESSFUL,
                Intrinsic.TASK_JOINER_ANY_SUCCESSFUL, Intrinsic.TASK_JOINER_AWAIT_ALL_SUCCESSFUL,
                Intrinsic.TASK_SCOPE_OPEN_DEFAULT, Intrinsic.TASK_SCOPE_OPEN, Intrinsic.TASK_SCOPE_OPEN_CONFIG,
                Intrinsic.TASK_SCOPE_OPEN_WITH_CONFIG, Intrinsic.TASK_SCOPE_FORK_CALLABLE,
                Intrinsic.TASK_SCOPE_FORK_RUNNABLE, Intrinsic.TASK_SCOPE_JOIN, Intrinsic.TASK_SCOPE_IS_CANCELLED,
                Intrinsic.TASK_GET, Intrinsic.TASK_STATE, Intrinsic.TASK_EXCEPTION, Intrinsic.TASK_SCOPE_CLOSE,
                Intrinsic.TASK_CONFIGURATION_WITH_TIMEOUT, Intrinsic.DURATION_OF_MILLIS,
                Intrinsic.DURATION_OF_SECONDS, Intrinsic.SCOPED_VALUE_NEW, Intrinsic.SCOPED_VALUE_WHERE,
                Intrinsic.SCOPED_CARRIER_WHERE, Intrinsic.SCOPED_VALUE_GET, Intrinsic.SCOPED_VALUE_IS_BOUND,
                Intrinsic.SCOPED_VALUE_OR_ELSE, Intrinsic.SCOPED_CARRIER_RUN, Intrinsic.SCOPED_CARRIER_CALL,
                Intrinsic.MONITOR_ENTER, Intrinsic.MONITOR_EXIT, Intrinsic.REENTRANT_LOCK_NEW,
                Intrinsic.REENTRANT_LOCK_LOCK, Intrinsic.REENTRANT_LOCK_TRY_LOCK, Intrinsic.REENTRANT_LOCK_UNLOCK);
        require(required, Capability.BIG_NUMBERS, Arrays.stream(Intrinsic.values())
                .filter(new BigNumberMethods()::isBigNumber).toArray(Intrinsic[]::new));
        require(required, Capability.DOUBLE_MATH, Intrinsic.MATH_SIN, Intrinsic.MATH_COS, Intrinsic.MATH_TAN,
                Intrinsic.MATH_ASIN, Intrinsic.MATH_ACOS, Intrinsic.MATH_ATAN, Intrinsic.MATH_ATAN2, Intrinsic.MATH_EXP,
                Intrinsic.MATH_LOG, Intrinsic.MATH_LOG10, Intrinsic.MATH_POW, Intrinsic.MATH_CBRT, Intrinsic.MATH_HYPOT);
        require(required, Capability.HTTP_SERVER, Intrinsic.HTTP_SERVER_BEGIN, Intrinsic.HTTP_SERVER_ACCEPT,
                Intrinsic.HTTP_SERVER_METHOD, Intrinsic.HTTP_SERVER_PATH, Intrinsic.HTTP_SERVER_CONTENT_LENGTH,
                Intrinsic.HTTP_SERVER_HEADER, Intrinsic.HTTP_SERVER_RESPOND, Intrinsic.HTTP_SERVER_RESPOND_BUILDER,
                Intrinsic.HTTP_SERVER_BEGIN_TLS);
        require(required, Capability.HTTPS_SERVER, Intrinsic.HTTP_SERVER_BEGIN_TLS);
        require(required, Capability.CAMERA, Intrinsic.CAMERA_BEGIN, Intrinsic.CAMERA_CAPTURE);
        require(required, Capability.PSRAM, Intrinsic.PSRAM_AVAILABLE, Intrinsic.PSRAM_NOMINAL_CAPACITY,
                Intrinsic.PSRAM_TOTAL_BYTES, Intrinsic.PSRAM_FREE_BYTES, Intrinsic.PSRAM_LARGEST_FREE_BLOCK,
                Intrinsic.PSRAM_ALLOCATE, Intrinsic.PSRAM_CAPACITY, Intrinsic.PSRAM_READ_BYTE,
                Intrinsic.PSRAM_WRITE_BYTE, Intrinsic.PSRAM_READ, Intrinsic.PSRAM_WRITE, Intrinsic.PSRAM_RELEASE);
        require(required, Capability.HTTP_STREAM, Intrinsic.HTTP_SERVER_BEGIN_STREAM,
                Intrinsic.HTTP_SERVER_STREAM_BYTES, Intrinsic.HTTP_SERVER_STREAM_TEXT, Intrinsic.HTTP_SERVER_END_STREAM);
        require(required, Capability.BLUETOOTH_LE, Intrinsic.BLE_CONNECT, Intrinsic.BLE_IS_CONNECTED,
                Intrinsic.BLE_WRITE, Intrinsic.BLE_READ, Intrinsic.BLE_DISCONNECT);
        return Collections.unmodifiableMap(required);
    }

    private static void require(Map<Intrinsic, Capability> required, Capability capability, Intrinsic... intrinsics) {
        for (Intrinsic intrinsic : intrinsics) {
            required.put(intrinsic, capability);
        }
    }
}
