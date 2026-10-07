package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.ThrowableTypes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lowers each {@link IrInstruction.IntrinsicCall} to assembly. Nearly every intrinsic is the same
 * shape — load a few argument words, {@code bl} one Arduino-core or runtime-shim function, store its
 * result — so each is registered here as data (a {@link ShimCall}: the function, where each argument
 * word comes from, what to do with the result, and which optional {@link ShimFeature}s the shim must
 * then include). The few that touch memory directly register a bespoke {@link Lowering}.
 * {@code java.lang.Math} intrinsics are lowered generically from their IR argument types instead.
 */
final class IntrinsicLowering {
    /** Emits the code for one intrinsic call. */
    @FunctionalInterface
    private interface Lowering {
        void emit(StringBuilder output, FrameLayout frame, IrInstruction.IntrinsicCall call);
    }

    /** Where one AAPCS argument word of a {@link ShimCall} comes from, relative to the call. */
    private sealed interface Operand {
        WordSource resolve(IrInstruction.IntrinsicCall call);
    }

    private record Receiver() implements Operand {
        @Override
        public WordSource resolve(IrInstruction.IntrinsicCall call) {
            return new WordSource.FromValue(call.receiver().orElseThrow());
        }
    }

    private record Argument(int index) implements Operand {
        @Override
        public WordSource resolve(IrInstruction.IntrinsicCall call) {
            return new WordSource.FromValue(call.arguments().get(index));
        }
    }

    private record ArgumentLow(int index) implements Operand {
        @Override
        public WordSource resolve(IrInstruction.IntrinsicCall call) {
            return new WordSource.FromValueLow(call.arguments().get(index));
        }
    }

    private record ArgumentHigh(int index) implements Operand {
        @Override
        public WordSource resolve(IrInstruction.IntrinsicCall call) {
            return new WordSource.FromValueHigh(call.arguments().get(index));
        }
    }

    private record Literal(int index) implements Operand {
        @Override
        public WordSource resolve(IrInstruction.IntrinsicCall call) {
            return new WordSource.StringAddress(call.literalArguments().get(index));
        }
    }

    private record Immediate(int value) implements Operand {
        @Override
        public WordSource resolve(IrInstruction.IntrinsicCall call) {
            return new WordSource.Immediate(value);
        }
    }

    /** The first string argument, whether the front end kept it as a literal or a runtime value. */
    private record StringArgument() implements Operand {
        @Override
        public WordSource resolve(IrInstruction.IntrinsicCall call) {
            return call.literalArguments().isEmpty()
                    ? new WordSource.FromValue(call.arguments().get(0))
                    : new WordSource.StringAddress(call.literalArguments().get(0));
        }
    }

    /** What happens to a {@link ShimCall}'s result once the function returns. */
    private enum Result {
        /** {@code void}, or a value the program never reads. */
        NONE,
        /** One word in {@code r0}. */
        WORD,
        /** A {@code long}/{@code double} in {@code r0}/{@code r1}. */
        WIDE,
        /** A pin-backed handle ({@code DigitalOutput}, {@code Servo}): the call's value is its own pin argument. */
        FIRST_ARGUMENT
    }

    /** A plain AAPCS call to {@code function}. */
    private record ShimCall(String function, Result result, List<Operand> operands, Set<ShimFeature> features) {
    }

    private static final Operand RECEIVER = new Receiver();
    private static final Operand STRING_ARGUMENT = new StringArgument();

    private final Map<Intrinsic, Lowering> lowerings = new EnumMap<>(Intrinsic.class);
    private final AsmEmitter asm;
    private final Set<ShimFeature> features;
    private final Set<Intrinsic> usedMath;

    /**
     * @param features collects every optional shim feature a lowered intrinsic needs
     * @param usedMath collects every {@code java.lang.Math} intrinsic lowered, for {@link MathRuntime#helpers}
     */
    IntrinsicLowering(AsmEmitter asm, CoreRuntime coreRuntime, Set<ShimFeature> features, Set<Intrinsic> usedMath,
                      boolean usesThreads) {
        this.asm = asm;
        this.features = features;
        this.usedMath = usedMath;
        registerExceptions();
        registerGpio(coreRuntime, usesThreads);
        registerThreads(usesThreads);
        registerStructuredTasks();
        registerScopedValues();
        registerMonitors();
        registerSerial();
        registerStrings();
        registerStorage();
        registerPeripherals();
        registerHttpClients();
        registerEmail();
        registerJson();
        registerHttpServer();
    }

    void emit(StringBuilder output, FrameLayout frame, IrInstruction.IntrinsicCall call) {
        if (MathRuntime.isMath(call.intrinsic())) {
            emitMathCall(output, frame, call);
            return;
        }
        Lowering lowering = lowerings.get(call.intrinsic());
        if (lowering == null) {
            throw Thumb2AsmBackend.unsupported("intrinsic " + call.intrinsic());
        }
        lowering.emit(output, frame, call);
    }

    private void registerExceptions() {
        lowerings.put(Intrinsic.THROWABLE_SET_MESSAGE, (output, frame, call) -> {
            asm.load(output, frame, "r0", call.receiver().orElseThrow());
            asm.load(output, frame, "r1", call.arguments().get(0));
            output.append("    str r1, [r0, #").append(ThrowableTypes.MESSAGE_OFFSET).append("]\n");
        });
        lowerings.put(Intrinsic.THROWABLE_GET_MESSAGE, (output, frame, call) -> {
            asm.load(output, frame, "r0", call.receiver().orElseThrow());
            output.append("    ldr r0, [r0, #").append(ThrowableTypes.MESSAGE_OFFSET).append("]\n");
            call.target().ifPresent(target -> asm.store(output, frame, "r0", target));
        });
        shim(Intrinsic.THROW_DISPATCH, "juno_throw_dispatch", Result.WORD, List.of(arg(0), arg(1)),
                ShimFeature.EXCEPTIONS);
        shim(Intrinsic.THROW_RAISE, "juno_throw_raise", Result.NONE, List.of(arg(0)), ShimFeature.EXCEPTIONS);
        shim(Intrinsic.THROW_PENDING, "juno_throw_pending", Result.WORD, List.of(), ShimFeature.EXCEPTIONS);
        shim(Intrinsic.THROW_CATCH, "juno_throw_catch", Result.WORD, List.of(arg(0)), ShimFeature.EXCEPTIONS);
    }

    /** GPIO, clock, delay, random, and the onboard LED matrix. */
    private void registerGpio(CoreRuntime coreRuntime, boolean usesThreads) {
        shim(Intrinsic.DIGITAL_OUTPUT_OF, "pinMode", Result.FIRST_ARGUMENT, List.of(arg(0), immediate(1))); // OUTPUT
        shim(Intrinsic.GPIO_PIN_MODE, "pinMode", Result.NONE, List.of(arg(0), arg(1)));
        shim(Intrinsic.DIGITAL_OUTPUT_HIGH, "digitalWrite", Result.NONE, List.of(RECEIVER, immediate(1)));
        shim(Intrinsic.DIGITAL_OUTPUT_LOW, "digitalWrite", Result.NONE, List.of(RECEIVER, immediate(0)));
        shim(Intrinsic.GPIO_DIGITAL_WRITE, "digitalWrite", Result.NONE, List.of(arg(0), arg(1)));
        shim(Intrinsic.GPIO_ANALOG_READ, "analogRead", Result.WORD, List.of(arg(0)));
        shim(Intrinsic.GPIO_ANALOG_WRITE, "analogWrite", Result.NONE, List.of(arg(0), arg(1)));
        shim(Intrinsic.CLOCK_MILLIS, "millis", Result.WORD, List.of());
        shim(Intrinsic.CLOCK_MICROS, "micros", Result.WORD, List.of());
        shim(Intrinsic.RANDOM_SEED, "juno_random_seed", Result.NONE, List.of(arg(0)), ShimFeature.RANDOM);
        shim(Intrinsic.RANDOM_NEXT_BOUND, "juno_random_next_bound", Result.WORD, List.of(arg(0)),
                ShimFeature.RANDOM);
        shim(Intrinsic.RANDOM_NEXT_RANGE, "juno_random_next_range", Result.WORD, List.of(arg(0), arg(1)),
                ShimFeature.RANDOM);
        // With threads a delay is a sleep: the other threads run while this one waits.
        if (usesThreads) {
            shim(Intrinsic.DELAY_MILLIS, "juno_thread_delay", Result.NONE, List.of(arg(0)), ShimFeature.THREADS);
        } else {
            shim(Intrinsic.DELAY_MILLIS, coreRuntime.delayMillisFunction(), Result.NONE, List.of(arg(0)));
        }
        shim(Intrinsic.DELAY_MICROS, coreRuntime.delayMicrosFunction(), Result.NONE, List.of(arg(0)));
        lowerings.put(Intrinsic.GPIO_BUILTIN_LED, (output, frame, call) -> {
            asm.emitLoadImmediate(output, "r0", coreRuntime.builtinLedPin());
            call.target().ifPresent(target -> asm.store(output, frame, "r0", target));
        });
        shim(Intrinsic.LED_MATRIX_BEGIN, "juno_led_matrix_begin", Result.NONE, List.of());
        shim(Intrinsic.LED_MATRIX_LOAD_FRAME, "juno_led_matrix_load_frame", Result.NONE,
                List.of(arg(0), arg(1), arg(2)));
        shim(Intrinsic.LED_MATRIX_CLEAR, "juno_led_matrix_clear", Result.NONE, List.of());
        shim(Intrinsic.MEMORY_ARENA_USED, "juno_memory_arena_used", Result.WORD, List.of(), ShimFeature.MEMORY);
    }

    /** {@code java.lang.Thread}: one scheduler call each; sleep and yield alone need no scheduler. */
    private void registerThreads(boolean usesThreads) {
        shim(Intrinsic.THREAD_NEW, "juno_thread_new", Result.WORD, List.of(arg(0)),
                ShimFeature.THREADS, ShimFeature.THREAD_ENTRY);
        shim(Intrinsic.THREAD_START, "juno_thread_start", Result.NONE, List.of(RECEIVER),
                ShimFeature.THREADS, ShimFeature.THREAD_ENTRY);
        shim(Intrinsic.THREAD_JOIN, "juno_thread_join", Result.NONE, List.of(RECEIVER), ShimFeature.THREADS);
        shim(Intrinsic.THREAD_IS_ALIVE, "juno_thread_is_alive", Result.WORD, List.of(RECEIVER),
                ShimFeature.THREADS);
        shim(Intrinsic.THREAD_SET_DAEMON, "juno_thread_set_daemon", Result.NONE, List.of(RECEIVER, arg(0)),
                ShimFeature.THREADS);
        ShimFeature runtime = usesThreads ? ShimFeature.THREADS : ShimFeature.THREAD_BASICS;
        shim(Intrinsic.THREAD_SLEEP, "juno_thread_sleep", Result.NONE, wide(0), runtime);
        shim(Intrinsic.THREAD_YIELD, "juno_thread_yield", Result.NONE, List.of(), runtime);
    }

    /** JDK 25 preview {@code StructuredTaskScope} operations over the cooperative task scheduler. */
    private void registerStructuredTasks() {
        ShimFeature[] taskRuntime = {ShimFeature.THREADS, ShimFeature.STRUCTURED_TASKS, ShimFeature.EXCEPTIONS};
        shim(Intrinsic.TASK_SCOPE_OPEN_DEFAULT, "juno_task_scope_open_default", Result.WORD, List.of(), taskRuntime);
        shim(Intrinsic.TASK_SCOPE_OPEN, "juno_task_scope_open", Result.WORD, List.of(arg(0)), taskRuntime);
        shim(Intrinsic.TASK_SCOPE_FORK_CALLABLE, "juno_task_scope_fork_callable", Result.WORD,
                List.of(RECEIVER, arg(0)), ShimFeature.THREADS, ShimFeature.STRUCTURED_TASKS,
                ShimFeature.EXCEPTIONS, ShimFeature.TASK_CALLABLE_ENTRY);
        shim(Intrinsic.TASK_SCOPE_FORK_RUNNABLE, "juno_task_scope_fork_runnable", Result.WORD,
                List.of(RECEIVER, arg(0)), ShimFeature.THREADS, ShimFeature.STRUCTURED_TASKS,
                ShimFeature.EXCEPTIONS, ShimFeature.THREAD_ENTRY);
        shim(Intrinsic.TASK_SCOPE_JOIN, "juno_task_scope_join", Result.WORD, List.of(RECEIVER), taskRuntime);
        shim(Intrinsic.TASK_SCOPE_IS_CANCELLED, "juno_task_scope_is_cancelled", Result.WORD,
                List.of(RECEIVER), taskRuntime);
        shim(Intrinsic.TASK_GET, "juno_task_get", Result.WORD, List.of(RECEIVER), taskRuntime);
        shim(Intrinsic.TASK_STATE, "juno_task_state", Result.WORD, List.of(RECEIVER), taskRuntime);
        shim(Intrinsic.TASK_EXCEPTION, "juno_task_exception", Result.WORD, List.of(RECEIVER), taskRuntime);
        shim(Intrinsic.TASK_SCOPE_CLOSE, "juno_task_scope_close", Result.NONE, List.of(RECEIVER), taskRuntime);
    }

    /** {@code java.lang.ScopedValue}: bindings are a chain of arena records entered on the calling thread's stack. */
    private void registerScopedValues() {
        shim(Intrinsic.SCOPED_VALUE_NEW, "juno_scoped_new", Result.WORD, List.of(), ShimFeature.SCOPED_VALUES);
        shim(Intrinsic.SCOPED_VALUE_WHERE, "juno_scoped_where", Result.WORD, List.of(arg(0), arg(1)),
                ShimFeature.SCOPED_VALUES);
        shim(Intrinsic.SCOPED_CARRIER_WHERE, "juno_scoped_carrier_where", Result.WORD,
                List.of(RECEIVER, arg(0), arg(1)), ShimFeature.SCOPED_VALUES);
        shim(Intrinsic.SCOPED_VALUE_GET, "juno_scoped_get", Result.WORD, List.of(RECEIVER),
                ShimFeature.SCOPED_VALUES, ShimFeature.EXCEPTIONS);
        shim(Intrinsic.SCOPED_VALUE_IS_BOUND, "juno_scoped_is_bound", Result.WORD, List.of(RECEIVER),
                ShimFeature.SCOPED_VALUES);
        shim(Intrinsic.SCOPED_VALUE_OR_ELSE, "juno_scoped_or_else", Result.WORD, List.of(RECEIVER, arg(0)),
                ShimFeature.SCOPED_VALUES);
        shim(Intrinsic.SCOPED_CARRIER_RUN, "juno_scoped_run", Result.NONE, List.of(RECEIVER, arg(0)),
                ShimFeature.SCOPED_VALUES, ShimFeature.EXCEPTIONS, ShimFeature.THREAD_ENTRY);
        shim(Intrinsic.SCOPED_CARRIER_CALL, "juno_scoped_call", Result.WORD, List.of(RECEIVER, arg(0)),
                ShimFeature.SCOPED_VALUES, ShimFeature.EXCEPTIONS, ShimFeature.SCOPED_CALL_ENTRY);
    }

    /** Intrinsic monitors share the cooperative scheduler's reentrant monitor table. */
    private void registerMonitors() {
        shim(Intrinsic.MONITOR_ENTER, "juno_monitor_enter", Result.NONE, List.of(RECEIVER), ShimFeature.THREADS);
        shim(Intrinsic.MONITOR_EXIT, "juno_monitor_exit", Result.NONE, List.of(RECEIVER), ShimFeature.THREADS);
        shim(Intrinsic.REENTRANT_LOCK_NEW, "juno_reentrant_lock_new", Result.WORD, List.of(),
                ShimFeature.THREADS);
        shim(Intrinsic.REENTRANT_LOCK_LOCK, "juno_monitor_enter", Result.NONE, List.of(RECEIVER),
                ShimFeature.THREADS);
        shim(Intrinsic.REENTRANT_LOCK_TRY_LOCK, "juno_monitor_try_enter", Result.WORD, List.of(RECEIVER),
                ShimFeature.THREADS);
        shim(Intrinsic.REENTRANT_LOCK_UNLOCK, "juno_monitor_exit", Result.NONE, List.of(RECEIVER),
                ShimFeature.THREADS);
    }

    private void registerSerial() {
        shim(Intrinsic.SERIAL_BEGIN, "juno_serial_begin", Result.NONE, List.of(arg(0)));
        shim(Intrinsic.SERIAL_PRINT, "juno_serial_print", Result.NONE, List.of(arg(0)));
        shim(Intrinsic.SERIAL_PRINTLN, "juno_serial_println", Result.NONE, List.of(arg(0)));
        shim(Intrinsic.SERIAL_PRINT_LONG, "juno_serial_print_long", Result.NONE, wide(0));
        shim(Intrinsic.SERIAL_PRINTLN_LONG, "juno_serial_println_long", Result.NONE, wide(0));
        shim(Intrinsic.SERIAL_PRINT_FLOAT, "juno_serial_print_float", Result.NONE, List.of(arg(0)));
        shim(Intrinsic.SERIAL_PRINTLN_FLOAT, "juno_serial_println_float", Result.NONE, List.of(arg(0)));
        shim(Intrinsic.SERIAL_PRINT_DOUBLE, "juno_serial_print_double", Result.NONE, wide(0));
        shim(Intrinsic.SERIAL_PRINTLN_DOUBLE, "juno_serial_println_double", Result.NONE, wide(0));
        shim(Intrinsic.SERIAL_PRINT_STRING, "juno_serial_print_str", Result.NONE, List.of(STRING_ARGUMENT));
        shim(Intrinsic.SERIAL_PRINTLN_STRING, "juno_serial_println_str", Result.NONE, List.of(STRING_ARGUMENT));
    }

    private void registerStrings() {
        shim(Intrinsic.STRING_VALUE_OF_INT, "juno_string_value_of_int", Result.WORD, List.of(arg(0)),
                ShimFeature.RUNTIME_STRINGS);
        shim(Intrinsic.STRING_VALUE_OF_DOUBLE, "juno_string_value_of_double", Result.WORD, wide(0),
                ShimFeature.RUNTIME_STRINGS);
        shim(Intrinsic.STRING_LENGTH, "juno_string_length", Result.WORD, List.of(RECEIVER),
                ShimFeature.RUNTIME_STRINGS);
        shim(Intrinsic.STRING_CHAR_AT, "juno_string_char_at", Result.WORD, List.of(RECEIVER, arg(0)),
                ShimFeature.RUNTIME_STRINGS);
        shim(Intrinsic.STRING_EQUALS, "juno_string_equals", Result.WORD, List.of(RECEIVER, arg(0)),
                ShimFeature.RUNTIME_STRINGS);
        shim(Intrinsic.STRING_BUILDER_NEW, "juno_string_builder_new", Result.WORD, List.of(arg(0)),
                ShimFeature.STRING_BUILDER);
        shim(Intrinsic.STRING_BUILDER_APPEND_CHAR, "juno_string_builder_append_char", Result.WORD,
                List.of(RECEIVER, arg(0)), ShimFeature.STRING_BUILDER);
        shim(Intrinsic.STRING_BUILDER_APPEND_STRING, "juno_string_builder_append_string", Result.WORD,
                List.of(RECEIVER, literal(0)), ShimFeature.STRING_BUILDER);
        shim(Intrinsic.STRING_BUILDER_TO_STRING, "juno_string_builder_to_string", Result.WORD,
                List.of(RECEIVER), ShimFeature.STRING_BUILDER);
        shim(Intrinsic.STRING_CONCAT_NEW, "juno_string_concat_new", Result.WORD, List.of(),
                ShimFeature.RUNTIME_STRINGS);
        shim(Intrinsic.STRING_CONCAT_APPEND_STRING, "juno_string_concat_append_string", Result.NONE,
                List.of(RECEIVER, arg(0)), ShimFeature.RUNTIME_STRINGS);
        shim(Intrinsic.STRING_CONCAT_APPEND_BOOLEAN, "juno_string_concat_append_boolean", Result.NONE,
                List.of(RECEIVER, arg(0)), ShimFeature.RUNTIME_STRINGS);
        shim(Intrinsic.STRING_CONCAT_APPEND_CHAR, "juno_string_concat_append_char", Result.NONE,
                List.of(RECEIVER, arg(0)), ShimFeature.RUNTIME_STRINGS);
        shim(Intrinsic.STRING_CONCAT_APPEND_INT, "juno_string_concat_append_int", Result.NONE,
                List.of(RECEIVER, arg(0)), ShimFeature.RUNTIME_STRINGS);
        shim(Intrinsic.STRING_CONCAT_APPEND_LONG, "juno_string_concat_append_long", Result.NONE,
                List.of(RECEIVER, immediate(0), new ArgumentLow(0), new ArgumentHigh(0)),
                ShimFeature.RUNTIME_STRINGS);
        shim(Intrinsic.STRING_CONCAT_APPEND_FLOAT, "juno_string_concat_append_float", Result.NONE,
                List.of(RECEIVER, arg(0)), ShimFeature.RUNTIME_STRINGS);
        shim(Intrinsic.STRING_CONCAT_APPEND_DOUBLE, "juno_string_concat_append_double", Result.NONE,
                List.of(RECEIVER, immediate(0), new ArgumentLow(0), new ArgumentHigh(0)),
                ShimFeature.RUNTIME_STRINGS);
    }

    /** SD card files and {@code Properties} parsed from them. */
    private void registerStorage() {
        shim(Intrinsic.SD_BEGIN, "juno_sd_begin", Result.WORD, List.of(arg(0)), ShimFeature.SD);
        shim(Intrinsic.SD_EXISTS, "juno_sd_exists", Result.WORD, List.of(literal(0)), ShimFeature.SD);
        shim(Intrinsic.SD_OPEN, "juno_sd_open", Result.WORD, List.of(literal(0)), ShimFeature.SD);
        shim(Intrinsic.SD_FILE_AVAILABLE, "juno_sd_file_available", Result.WORD, List.of(RECEIVER),
                ShimFeature.SD);
        shim(Intrinsic.SD_FILE_READ, "juno_sd_file_read", Result.WORD, List.of(RECEIVER), ShimFeature.SD);
        shim(Intrinsic.SD_FILE_CLOSE, "juno_sd_file_close", Result.NONE, List.of(RECEIVER), ShimFeature.SD);
        shim(Intrinsic.SD_APPEND, "juno_sd_file_append", Result.WORD, List.of(literal(0), arg(0)), ShimFeature.SD);
        shim(Intrinsic.SD_REMOVE, "juno_sd_remove", Result.WORD, List.of(literal(0)), ShimFeature.SD);
        shim(Intrinsic.PROPERTIES_NEW, "juno_properties_new", Result.WORD, List.of(), ShimFeature.SD);
        shim(Intrinsic.PROPERTIES_LOAD, "juno_properties_load", Result.NONE, List.of(RECEIVER, arg(0)),
                ShimFeature.SD);
        shim(Intrinsic.PROPERTIES_GET, "juno_properties_get", Result.WORD, List.of(RECEIVER, arg(0)),
                ShimFeature.SD);
        shim(Intrinsic.PROPERTIES_GET_DEFAULT, "juno_properties_get_default", Result.WORD,
                List.of(RECEIVER, arg(0), arg(1)), ShimFeature.SD);
        shim(Intrinsic.PROPERTIES_SIZE, "juno_properties_size", Result.WORD, List.of(RECEIVER), ShimFeature.SD);
    }

    /** USB mouse, servos, LEGO Powered Up hubs, and the Wi-Fi radio. */
    private void registerPeripherals() {
        shim(Intrinsic.MOUSE_BEGIN, "juno_mouse_begin", Result.NONE, List.of(), ShimFeature.MOUSE);
        shim(Intrinsic.MOUSE_MOVE, "juno_mouse_move", Result.NONE, List.of(arg(0), arg(1)), ShimFeature.MOUSE);
        shim(Intrinsic.SERVO_OF, "juno_servo_attach", Result.FIRST_ARGUMENT, List.of(arg(0)), ShimFeature.SERVO);
        shim(Intrinsic.SERVO_WRITE, "juno_servo_write", Result.NONE, List.of(RECEIVER, arg(0)), ShimFeature.SERVO);
        shim(Intrinsic.LEGO_HUB_CONNECT, "juno_lego_hub_connect", Result.WORD, List.of(arg(0)),
                ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_IS_CONNECTED, "juno_lego_hub_is_connected", Result.WORD, List.of(),
                ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_TYPE, "juno_lego_hub_type_id", Result.WORD, List.of(), ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_SET_MOTOR_POWER, "juno_lego_hub_set_motor_power", Result.NONE,
                List.of(arg(0), arg(1)), ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_BRAKE_MOTOR, "juno_lego_hub_brake_motor", Result.NONE, List.of(arg(0)),
                ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_SET_LED_COLOR, "juno_lego_hub_set_led_color", Result.NONE, List.of(arg(0)),
                ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_ENABLE_SENSOR, "juno_lego_hub_enable_sensor", Result.NONE, List.of(arg(0), arg(1)),
                ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_READ_SENSOR, "juno_lego_hub_read_sensor", Result.WORD, List.of(arg(0)),
                ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_DISCONNECT, "juno_lego_hub_disconnect", Result.NONE, List.of(),
                ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_SWITCH_OFF, "juno_lego_hub_switch_off", Result.NONE, List.of(),
                ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.WIFI_BEGIN, "juno_wifi_begin", Result.NONE, List.of(arg(0), arg(1)), ShimFeature.WIFI);
        shim(Intrinsic.WIFI_BEGIN_AP, "juno_wifi_begin_ap", Result.NONE, List.of(arg(0), arg(1)), ShimFeature.WIFI);
        shim(Intrinsic.WIFI_STATUS, "juno_wifi_status", Result.WORD, List.of(), ShimFeature.WIFI);
        shim(Intrinsic.WIFI_LOCAL_IP, "juno_wifi_local_ip", Result.NONE, List.of(arg(0)), ShimFeature.WIFI);
        shim(Intrinsic.UDP_LISTEN, "juno_udp_listen", Result.WORD, List.of(arg(0)), ShimFeature.UDP);
        shim(Intrinsic.UDP_SEND, "juno_udp_send", Result.WORD,
                List.of(arg(0), arg(1), arg(2), arg(3)), ShimFeature.UDP);
        shim(Intrinsic.UDP_BROADCAST, "juno_udp_broadcast", Result.WORD,
                List.of(arg(0), arg(1), arg(2)), ShimFeature.UDP);
        shim(Intrinsic.UDP_RECEIVE, "juno_udp_receive", Result.WORD,
                List.of(arg(0), arg(1), arg(2)), ShimFeature.UDP);
        shim(Intrinsic.UDP_STOP, "juno_udp_stop", Result.NONE, List.of(), ShimFeature.UDP);
    }

    /**
     * {@code HttpClient}/{@code HttpsClient}: host literal, port, path literal (plus content-type and
     * body literals for requests that carry a body), then response/headers buffers and their lengths.
     */
    private void registerHttpClients() {
        List<Operand> bodiless = List.of(literal(0), arg(0), literal(1), arg(1), arg(2), arg(3), arg(4), arg(5));
        List<Operand> withBody = List.of(literal(0), arg(0), literal(1), literal(2),
                arg(1), arg(2), arg(3), arg(4), arg(5));
        shim(Intrinsic.HTTP_GET, "juno_http_get", Result.WORD, bodiless, ShimFeature.HTTP);
        shim(Intrinsic.HTTP_DELETE, "juno_http_delete", Result.WORD, bodiless, ShimFeature.HTTP);
        shim(Intrinsic.HTTP_POST, "juno_http_post", Result.WORD, withBody, ShimFeature.HTTP);
        shim(Intrinsic.HTTP_PATCH, "juno_http_patch", Result.WORD, withBody, ShimFeature.HTTP);
        shim(Intrinsic.HTTP_QUERY, "juno_http_query", Result.WORD, withBody, ShimFeature.HTTP);
        shim(Intrinsic.HTTPS_GET, "juno_https_get", Result.WORD, bodiless, ShimFeature.HTTPS);
        shim(Intrinsic.HTTPS_DELETE, "juno_https_delete", Result.WORD, bodiless, ShimFeature.HTTPS);
        shim(Intrinsic.HTTPS_GET_PATH_BUFFER, "juno_https_get_path_buffer", Result.WORD,
                List.of(literal(0), arg(0), arg(1), arg(2), arg(3), arg(4), arg(5), arg(6), arg(7)),
                ShimFeature.HTTPS, ShimFeature.HTTPS_PATH_BUFFER);
        shim(Intrinsic.HTTPS_POST, "juno_https_post", Result.WORD, withBody, ShimFeature.HTTPS);
        shim(Intrinsic.HTTPS_PATCH, "juno_https_patch", Result.WORD, withBody, ShimFeature.HTTPS);
        shim(Intrinsic.HTTPS_QUERY, "juno_https_query", Result.WORD, withBody, ShimFeature.HTTPS);
    }

    private void registerEmail() {
        shim(Intrinsic.SMTP_SEND, "juno_smtp_send", Result.WORD,
                List.of(literal(0), arg(0), literal(1), literal(2), literal(3), literal(4), literal(5), literal(6)),
                ShimFeature.SMTP);
        shim(Intrinsic.SMTP_SEND_TLS, "juno_smtp_send_tls", Result.WORD,
                List.of(literal(0), arg(0), literal(1), literal(2), literal(3), literal(4), literal(5), literal(6)),
                ShimFeature.SMTP_TLS);
        shim(Intrinsic.POP3_MESSAGE_COUNT, "juno_pop3_message_count", Result.WORD,
                List.of(literal(0), arg(0), literal(1), literal(2)), ShimFeature.POP3);
        shim(Intrinsic.POP3_READ_LATEST, "juno_pop3_read_latest", Result.WORD,
                List.of(literal(0), arg(0), literal(1), literal(2), arg(1), arg(2), arg(3), arg(4), arg(5)),
                ShimFeature.POP3);
        shim(Intrinsic.POP3_READ_SUBJECT, "juno_pop3_read_subject", Result.WORD,
                List.of(literal(0), arg(0), literal(1), literal(2), arg(1), arg(2), arg(3)), ShimFeature.POP3);
    }

    /** {@code Json}: every accessor takes the document buffer, its length, and a path literal. */
    private void registerJson() {
        List<Operand> query = List.of(arg(0), arg(1), literal(0));
        shim(Intrinsic.JSON_TYPE, "juno_json_type", Result.WORD, query, ShimFeature.JSON);
        shim(Intrinsic.JSON_GET_INT, "juno_json_get_int", Result.WORD, query, ShimFeature.JSON);
        shim(Intrinsic.JSON_GET_BOOL, "juno_json_get_bool", Result.WORD, query, ShimFeature.JSON);
        shim(Intrinsic.JSON_ARRAY_SIZE, "juno_json_array_size", Result.WORD, query, ShimFeature.JSON);
        shim(Intrinsic.JSON_GET_LONG, "juno_json_get_long", Result.WIDE, query, ShimFeature.JSON);
        shim(Intrinsic.JSON_GET_DOUBLE, "juno_json_get_double", Result.WIDE, query, ShimFeature.JSON);
        shim(Intrinsic.JSON_GET_STRING, "juno_json_get_string", Result.WORD,
                List.of(arg(0), arg(1), literal(0), arg(2), arg(3)), ShimFeature.JSON);
        shim(Intrinsic.JSON_GET_STRING_VALUE, "juno_json_get_string_value", Result.WORD, query,
                ShimFeature.JSON, ShimFeature.RUNTIME_STRINGS, ShimFeature.JSON_STRING_VALUE);
    }

    private void registerHttpServer() {
        ShimFeature[] server = {ShimFeature.HTTP_SERVER, ShimFeature.RUNTIME_STRINGS, ShimFeature.STRING_BUILDER};
        shim(Intrinsic.HTTP_SERVER_BEGIN, "juno_http_server_begin", Result.NONE, List.of(arg(0)), server);
        shim(Intrinsic.HTTP_SERVER_ACCEPT, "juno_http_server_accept", Result.WORD, List.of(arg(0), arg(1)), server);
        shim(Intrinsic.HTTP_SERVER_METHOD, "juno_http_server_method", Result.WORD, List.of(), server);
        shim(Intrinsic.HTTP_SERVER_PATH, "juno_http_server_path", Result.WORD, List.of(), server);
        // body (the second argument) may be a literal's address or a runtime pooled string's address
        // (see IntrinsicRegistry#requiresLiteralStringArgument) — both are plain null-terminated
        // const char* to the shim, so both flow the same way.
        shim(Intrinsic.HTTP_SERVER_RESPOND, "juno_http_server_respond", Result.NONE,
                List.of(arg(0), literal(0), arg(1)), server);
        shim(Intrinsic.HTTP_SERVER_RESPOND_BUILDER, "juno_http_server_respond_builder", Result.NONE,
                List.of(arg(0), literal(0), arg(1)), server);
    }

    private static Operand arg(int index) {
        return new Argument(index);
    }

    private static Operand literal(int index) {
        return new Literal(index);
    }

    private static Operand immediate(int value) {
        return new Immediate(value);
    }

    /** A {@code long}/{@code double} argument's two words, low first. */
    private static List<Operand> wide(int index) {
        return List.of(new ArgumentLow(index), new ArgumentHigh(index));
    }

    private void shim(Intrinsic intrinsic, String function, Result result, List<Operand> operands,
                      ShimFeature... required) {
        ShimCall shimCall = new ShimCall(function, result, operands,
                required.length == 0 ? Set.of() : EnumSet.of(required[0], required));
        lowerings.put(intrinsic, (output, frame, call) -> emitShimCall(output, frame, call, shimCall));
    }

    private void emitShimCall(StringBuilder output, FrameLayout frame, IrInstruction.IntrinsicCall call,
                              ShimCall shimCall) {
        features.addAll(shimCall.features());
        List<WordSource> words = new ArrayList<>(shimCall.operands().size());
        for (Operand operand : shimCall.operands()) {
            words.add(operand.resolve(call));
        }
        asm.emitShimCall(output, frame, shimCall.function(), words);
        switch (shimCall.result()) {
            case NONE -> { }
            case WORD -> call.target().ifPresent(target -> asm.store(output, frame, "r0", target));
            case WIDE -> call.target().ifPresent(target -> asm.store64(output, frame, "r0", "r1", target));
            case FIRST_ARGUMENT -> {
                asm.load(output, frame, "r0", call.arguments().get(0));
                call.target().ifPresent(target -> asm.store(output, frame, "r0", target));
            }
        }
    }

    /**
     * Every {@code java.lang.Math} overload is a plain shim call whose argument words follow each
     * argument's IR type: one word for {@code int}/{@code float}, a low/high pair for
     * {@code long}/{@code double} — padded to an even word, as AAPCS aligns 64-bit arguments.
     */
    private void emitMathCall(StringBuilder output, FrameLayout frame, IrInstruction.IntrinsicCall call) {
        usedMath.add(call.intrinsic());
        List<WordSource> words = new ArrayList<>();
        for (Value argument : call.arguments()) {
            if (FrameLayout.isWide(argument.type())) {
                if (words.size() % 2 != 0) {
                    words.add(new WordSource.Immediate(0));
                }
                words.add(new WordSource.FromValueLow(argument));
                words.add(new WordSource.FromValueHigh(argument));
            } else {
                words.add(new WordSource.FromValue(argument));
            }
        }
        asm.emitShimCall(output, frame, MathRuntime.symbol(call.intrinsic()), words);
        call.target().ifPresent(target -> {
            if (FrameLayout.isWide(target.type())) {
                asm.store64(output, frame, "r0", "r1", target);
            } else {
                asm.store(output, frame, "r0", target);
            }
        });
    }
}
