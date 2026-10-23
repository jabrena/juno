package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.backend.runtime.MathRuntime;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.backend.runtime.spi.ShimFeature;
import io.github.jabrena.juno.board.UnoGeneration;
import io.github.jabrena.juno.intrinsic.BigNumberMethods;
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
public final class IntrinsicLowering {
    private final BigNumberMethods bigNumberMethods = new BigNumberMethods();

    private final MathRuntime mathRuntime = new MathRuntime();

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

    /**
     * One declared parameter of a call whose {@code String} parameters may be literals or runtime values. When every
     * string is a literal, the literals sit in {@code literalArguments} and the runtime list skips them; when none is,
     * the runtime list holds every parameter in order.
     */
    private record Parameter(int parameter, List<Integer> stringParameters) implements Operand {
        @Override
        public WordSource resolve(IrInstruction.IntrinsicCall call) {
            boolean literals = !call.literalArguments().isEmpty();
            if (literals && call.literalArguments().size() != stringParameters.size()) {
                throw new CompileException("a call mixes literal and runtime String arguments; pass every String"
                        + " argument as a literal or as a runtime value");
            }
            int rank = stringParameters.indexOf(parameter);
            if (rank >= 0 && literals) {
                return new WordSource.StringAddress(call.literalArguments().get(rank));
            }
            int skipped = literals ? (int) stringParameters.stream().filter(index -> index < parameter).count() : 0;
            return new WordSource.FromValue(call.arguments().get(parameter - skipped));
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

    /** The {@code BoardModel} queries, each true only on boards of its generation. */
    private static final Map<Intrinsic, UnoGeneration> GENERATION_QUERIES = Map.of(
            Intrinsic.BOARD_IS_R3, UnoGeneration.R3,
            Intrinsic.BOARD_IS_R4, UnoGeneration.R4,
            Intrinsic.BOARD_IS_Q, UnoGeneration.Q);

    private static final Operand RECEIVER = new Receiver();
    private static final Operand STRING_ARGUMENT = new StringArgument();

    private final Map<Intrinsic, Lowering> lowerings = new EnumMap<>(Intrinsic.class);
    private final IntrinsicTarget target;
    private final Set<ShimFeature> features;
    private final Set<Intrinsic> usedMath;

    /**
     * @param generation the target board's {@link io.github.jabrena.juno.board.Board#generation() UNO generation}
     * @param features collects every optional shim feature a lowered intrinsic needs
     * @param usedMath collects every {@code java.lang.Math} intrinsic lowered, for {@link MathRuntime#helpers}
     */
    public IntrinsicLowering(IntrinsicTarget target, CoreRuntime coreRuntime, UnoGeneration generation,
                      Set<ShimFeature> features, Set<Intrinsic> usedMath, boolean usesThreads) {
        this.target = target;
        this.features = features;
        this.usedMath = usedMath;
        registerExceptions();
        registerGpio(coreRuntime, generation, usesThreads);
        registerThreads(usesThreads);
        registerAtomics();
        registerStructuredTasks();
        registerScopedValues();
        registerUuid();
        registerLocalDateTime();
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

    public void emit(StringBuilder output, FrameLayout frame, IrInstruction.IntrinsicCall call) {
        if (mathRuntime.isMath(call.intrinsic())) {
            emitMathCall(output, frame, call);
            return;
        }
        if (bigNumberMethods.isBigNumber(call.intrinsic())) {
            emitBigNumberCall(output, frame, call);
            return;
        }
        Lowering lowering = lowerings.get(call.intrinsic());
        if (lowering == null) {
            throw target.unsupported("intrinsic " + call.intrinsic());
        }
        lowering.emit(output, frame, call);
    }

    private void registerExceptions() {
        lowerings.put(Intrinsic.THROWABLE_SET_MESSAGE, (output, frame, call) -> target.storeObjectWord(output, frame,
                call.receiver().orElseThrow(), ThrowableTypes.MESSAGE_OFFSET, call.arguments().get(0)));
        lowerings.put(Intrinsic.THROWABLE_GET_MESSAGE, (output, frame, call) -> target.loadObjectWord(output, frame,
                call.receiver().orElseThrow(), ThrowableTypes.MESSAGE_OFFSET, call.target()));
        shim(Intrinsic.THROW_DISPATCH, "juno_throw_dispatch", Result.WORD, List.of(arg(0), arg(1)),
                ShimFeature.EXCEPTIONS);
        shim(Intrinsic.THROW_RAISE, "juno_throw_raise", Result.NONE, List.of(arg(0)), ShimFeature.EXCEPTIONS);
        shim(Intrinsic.THROW_PENDING, "juno_throw_pending", Result.WORD, List.of(), ShimFeature.EXCEPTIONS);
        shim(Intrinsic.THROW_CATCH, "juno_throw_catch", Result.WORD, List.of(arg(0)), ShimFeature.EXCEPTIONS);
    }

    private void registerUuid() {
        shim(Intrinsic.UUID_NEW, "juno_uuid_new", Result.WORD,
                List.of(new ArgumentLow(0), new ArgumentHigh(0), new ArgumentLow(1), new ArgumentHigh(1)),
                ShimFeature.UUID);
        shim(Intrinsic.UUID_RANDOM, "juno_uuid_random", Result.WORD, List.of(), ShimFeature.UUID,
                ShimFeature.UUID_RANDOM);
        shim(Intrinsic.UUID_GET_MOST_SIGNIFICANT_BITS, "juno_uuid_get_most_significant_bits", Result.WIDE,
                List.of(RECEIVER), ShimFeature.UUID);
        shim(Intrinsic.UUID_GET_LEAST_SIGNIFICANT_BITS, "juno_uuid_get_least_significant_bits", Result.WIDE,
                List.of(RECEIVER), ShimFeature.UUID);
        shim(Intrinsic.UUID_VERSION, "juno_uuid_version", Result.WORD, List.of(RECEIVER), ShimFeature.UUID);
        shim(Intrinsic.UUID_VARIANT, "juno_uuid_variant", Result.WORD, List.of(RECEIVER), ShimFeature.UUID);
        shim(Intrinsic.UUID_EQUALS, "juno_uuid_equals", Result.WORD, List.of(RECEIVER, arg(0)), ShimFeature.UUID);
        shim(Intrinsic.UUID_COMPARE_TO, "juno_uuid_compare_to", Result.WORD, List.of(RECEIVER, arg(0)),
                ShimFeature.UUID);
        shim(Intrinsic.UUID_HASH_CODE, "juno_uuid_hash_code", Result.WORD, List.of(RECEIVER), ShimFeature.UUID);
        shim(Intrinsic.UUID_FROM_STRING, "juno_uuid_from_string", Result.WORD, List.of(STRING_ARGUMENT),
                ShimFeature.UUID, ShimFeature.EXCEPTIONS);
        shim(Intrinsic.UUID_TO_STRING, "juno_uuid_to_string", Result.WORD, List.of(RECEIVER), ShimFeature.UUID);
    }

    private void registerLocalDateTime() {
        shim(Intrinsic.LOCAL_DATE_TIME_CONFIGURE, "juno_local_date_time_configure", Result.WORD, List.of(arg(0)),
                ShimFeature.LOCAL_DATE_TIME, ShimFeature.LOCAL_DATE_TIME_CONFIG, ShimFeature.SD);
        shim(Intrinsic.LOCAL_DATE_TIME_NOW, "juno_local_date_time_now", Result.WORD, List.of(),
                ShimFeature.LOCAL_DATE_TIME, ShimFeature.LOCAL_DATE_TIME_NOW, ShimFeature.WIFI, ShimFeature.SNTP);
        shim(Intrinsic.LOCAL_DATE_TIME_MIN, "juno_local_date_time_min", Result.WORD, List.of(),
                ShimFeature.LOCAL_DATE_TIME);
        shim(Intrinsic.LOCAL_DATE_TIME_GET_YEAR, "juno_local_date_time_get_year", Result.WORD,
                List.of(RECEIVER), ShimFeature.LOCAL_DATE_TIME);
        shim(Intrinsic.LOCAL_DATE_TIME_GET_MONTH_VALUE, "juno_local_date_time_get_month_value", Result.WORD,
                List.of(RECEIVER), ShimFeature.LOCAL_DATE_TIME);
        shim(Intrinsic.LOCAL_DATE_TIME_GET_DAY_OF_MONTH, "juno_local_date_time_get_day_of_month", Result.WORD,
                List.of(RECEIVER), ShimFeature.LOCAL_DATE_TIME);
        shim(Intrinsic.LOCAL_DATE_TIME_GET_HOUR, "juno_local_date_time_get_hour", Result.WORD,
                List.of(RECEIVER), ShimFeature.LOCAL_DATE_TIME);
        shim(Intrinsic.LOCAL_DATE_TIME_GET_MINUTE, "juno_local_date_time_get_minute", Result.WORD,
                List.of(RECEIVER), ShimFeature.LOCAL_DATE_TIME);
        shim(Intrinsic.LOCAL_DATE_TIME_GET_SECOND, "juno_local_date_time_get_second", Result.WORD,
                List.of(RECEIVER), ShimFeature.LOCAL_DATE_TIME);
        shim(Intrinsic.LOCAL_DATE_TIME_EQUALS, "juno_local_date_time_equals", Result.WORD,
                List.of(RECEIVER, arg(0)), ShimFeature.LOCAL_DATE_TIME);
        shim(Intrinsic.LOCAL_DATE_TIME_TO_STRING, "juno_local_date_time_to_string", Result.WORD,
                List.of(RECEIVER), ShimFeature.LOCAL_DATE_TIME);
    }

    /** GPIO, clock, delay, random, and the onboard LED matrix. */
    private void registerGpio(CoreRuntime coreRuntime, UnoGeneration generation, boolean usesThreads) {
        String pinMode = coreRuntime.gpioFunction("pinMode");
        String digitalWrite = coreRuntime.gpioFunction("digitalWrite");
        shim(Intrinsic.DIGITAL_OUTPUT_OF, pinMode, Result.FIRST_ARGUMENT, List.of(arg(0), immediate(1))); // OUTPUT
        shim(Intrinsic.GPIO_PIN_MODE, pinMode, Result.NONE, List.of(arg(0), arg(1)));
        shim(Intrinsic.DIGITAL_OUTPUT_HIGH, digitalWrite, Result.NONE, List.of(RECEIVER, immediate(1)));
        shim(Intrinsic.DIGITAL_OUTPUT_LOW, digitalWrite, Result.NONE, List.of(RECEIVER, immediate(0)));
        shim(Intrinsic.GPIO_DIGITAL_WRITE, digitalWrite, Result.NONE, List.of(arg(0), arg(1)));
        shim(Intrinsic.GPIO_DIGITAL_READ, coreRuntime.gpioFunction("digitalRead"), Result.WORD, List.of(arg(0)));
        shim(Intrinsic.GPIO_ANALOG_READ, coreRuntime.gpioFunction("analogRead"), Result.WORD, List.of(arg(0)));
        shim(Intrinsic.GPIO_ANALOG_WRITE, coreRuntime.gpioFunction("analogWrite"), Result.NONE,
                List.of(arg(0), arg(1)));
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
        lowerings.put(Intrinsic.GPIO_BUILTIN_LED, (output, frame, call) -> call.target().ifPresent(
                value -> target.storeImmediate(output, frame, coreRuntime.builtinLedPin(), value)));
        lowerings.put(Intrinsic.LED_MATRIX_COLUMNS, (output, frame, call) -> call.target().ifPresent(
                value -> target.storeImmediate(output, frame, coreRuntime.ledMatrixColumns(), value)));
        GENERATION_QUERIES.forEach((query, queried) -> lowerings.put(query, (output, frame, call) -> call.target()
                .ifPresent(value -> target.storeImmediate(output, frame, generation == queried ? 1 : 0, value))));
        shim(Intrinsic.LED_MATRIX_BEGIN, "juno_led_matrix_begin", Result.NONE, List.of(), ShimFeature.LED_MATRIX);
        shim(Intrinsic.LED_MATRIX_LOAD_FRAME, "juno_led_matrix_load_frame", Result.NONE,
                List.of(arg(0), arg(1), arg(2), arg(3)), ShimFeature.LED_MATRIX);
        shim(Intrinsic.LED_MATRIX_CLEAR, "juno_led_matrix_clear", Result.NONE, List.of(), ShimFeature.LED_MATRIX);
        shim(Intrinsic.MEMORY_ARENA_USED, "juno_memory_arena_used", Result.WORD, List.of(), ShimFeature.MEMORY);
        shim(Intrinsic.MEMORY_ARENA_CAPACITY, "juno_memory_arena_capacity", Result.WORD, List.of(),
                ShimFeature.MEMORY);
        shim(Intrinsic.MEMORY_FLASH_USED, "juno_memory_flash_used", Result.WORD, List.of(), ShimFeature.FLASH);
        shim(Intrinsic.MEMORY_FLASH_CAPACITY, "juno_memory_flash_capacity", Result.WORD, List.of(),
                ShimFeature.FLASH);
    }

    /**
     * {@code java.lang.Thread}: virtual threads are the only thread the program may create, and each is one scheduler
     * call; sleep and yield alone need no scheduler.
     */
    private void registerThreads(boolean usesThreads) {
        shim(Intrinsic.THREAD_START_VIRTUAL, "juno_thread_start_virtual", Result.WORD, List.of(arg(0)),
                ShimFeature.THREADS, ShimFeature.THREAD_ENTRY);
        shim(Intrinsic.THREAD_JOIN, "juno_thread_join", Result.NONE, List.of(RECEIVER), ShimFeature.THREADS);
        shim(Intrinsic.THREAD_IS_ALIVE, "juno_thread_is_alive", Result.WORD, List.of(RECEIVER), ShimFeature.THREADS);
        ShimFeature runtime = usesThreads ? ShimFeature.THREADS : ShimFeature.THREAD_BASICS;
        shim(Intrinsic.THREAD_SLEEP, "juno_thread_sleep", Result.NONE, wide(0), runtime);
        shim(Intrinsic.THREAD_YIELD, "juno_thread_yield", Result.NONE, List.of(), runtime);
    }

    /** JDK 27 preview {@code StructuredTaskScope} operations; subtasks are cooperative. */
    private void registerStructuredTasks() {
        ShimFeature[] taskRuntime = {ShimFeature.THREADS, ShimFeature.STRUCTURED_TASKS, ShimFeature.EXCEPTIONS};
        shim(Intrinsic.TASK_SCOPE_OPEN_DEFAULT, "juno_task_scope_open_default", Result.WORD, List.of(), taskRuntime);
        shim(Intrinsic.TASK_SCOPE_OPEN, "juno_task_scope_open", Result.WORD, List.of(arg(0)), taskRuntime);
        shim(Intrinsic.TASK_SCOPE_OPEN_CONFIG, "juno_task_scope_open_config", Result.WORD, List.of(arg(0)),
                taskRuntime);
        shim(Intrinsic.TASK_SCOPE_OPEN_WITH_CONFIG, "juno_task_scope_open_with_config", Result.WORD,
                List.of(arg(0), arg(1)), taskRuntime);
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
        shim(Intrinsic.TASK_CONFIGURATION_WITH_TIMEOUT, "juno_task_configuration_with_timeout", Result.WORD,
                List.of(RECEIVER, arg(0)), taskRuntime);
        shim(Intrinsic.DURATION_OF_MILLIS, "juno_duration_of_millis", Result.WORD, wide(0), taskRuntime);
        shim(Intrinsic.DURATION_OF_SECONDS, "juno_duration_of_seconds", Result.WORD, wide(0), taskRuntime);
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

    /** Intrinsic monitors share the scheduler's reentrant monitor table. */
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

    /** Atomic cells: one shim call each; the scheduler never preempts runtime code, so no monitor is needed. */
    private void registerAtomics() {
        shim(Intrinsic.ATOMIC_INT_NEW_DEFAULT, "juno_atomic_int_new_default", Result.WORD, List.of(), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_INT_NEW, "juno_atomic_int_new", Result.WORD, List.of(arg(0)), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_INT_GET, "juno_atomic_int_get", Result.WORD, List.of(RECEIVER), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_INT_SET, "juno_atomic_int_set", Result.NONE, List.of(RECEIVER, arg(0)), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_INT_GET_AND_SET, "juno_atomic_int_get_and_set", Result.WORD, List.of(RECEIVER, arg(0)), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_INT_COMPARE_AND_SET, "juno_atomic_int_compare_and_set", Result.WORD, List.of(RECEIVER, arg(0), arg(1)), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_INT_INCREMENT_AND_GET, "juno_atomic_int_increment_and_get", Result.WORD, List.of(RECEIVER), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_INT_DECREMENT_AND_GET, "juno_atomic_int_decrement_and_get", Result.WORD, List.of(RECEIVER), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_INT_GET_AND_INCREMENT, "juno_atomic_int_get_and_increment", Result.WORD, List.of(RECEIVER), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_INT_GET_AND_DECREMENT, "juno_atomic_int_get_and_decrement", Result.WORD, List.of(RECEIVER), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_INT_ADD_AND_GET, "juno_atomic_int_add_and_get", Result.WORD, List.of(RECEIVER, arg(0)), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_INT_GET_AND_ADD, "juno_atomic_int_get_and_add", Result.WORD, List.of(RECEIVER, arg(0)), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_LONG_NEW_DEFAULT, "juno_atomic_long_new_default", Result.WORD, List.of(), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_LONG_NEW, "juno_atomic_long_new", Result.WORD, wide(0), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_LONG_GET, "juno_atomic_long_get", Result.WIDE, List.of(RECEIVER), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_LONG_SET, "juno_atomic_long_set", Result.NONE, withReceiver(wide(0)),
                ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_LONG_GET_AND_SET, "juno_atomic_long_get_and_set", Result.WIDE,
                withReceiver(wide(0)), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_LONG_COMPARE_AND_SET, "juno_atomic_long_compare_and_set", Result.WORD,
                withReceiver(wide(0), wide(1)), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_LONG_INCREMENT_AND_GET, "juno_atomic_long_increment_and_get", Result.WIDE, List.of(RECEIVER), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_LONG_DECREMENT_AND_GET, "juno_atomic_long_decrement_and_get", Result.WIDE, List.of(RECEIVER), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_LONG_GET_AND_INCREMENT, "juno_atomic_long_get_and_increment", Result.WIDE, List.of(RECEIVER), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_LONG_GET_AND_DECREMENT, "juno_atomic_long_get_and_decrement", Result.WIDE, List.of(RECEIVER), ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_LONG_ADD_AND_GET, "juno_atomic_long_add_and_get", Result.WIDE, withReceiver(wide(0)),
                ShimFeature.ATOMICS);
        shim(Intrinsic.ATOMIC_LONG_GET_AND_ADD, "juno_atomic_long_get_and_add", Result.WIDE, withReceiver(wide(0)),
                ShimFeature.ATOMICS);
    }

    /** {@code receiver} followed by every operand in {@code groups}, each a list of words. */
    @SafeVarargs
    private List<Operand> withReceiver(List<Operand>... groups) {
        List<Operand> operands = new ArrayList<>();
        operands.add(RECEIVER);
        for (List<Operand> group : groups) {
            operands.addAll(group);
        }
        return operands;
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
        shim(Intrinsic.SD_SIZE, "juno_sd_size", Result.WORD, List.of(literal(0)), ShimFeature.SD);
        shim(Intrinsic.SD_OPEN, "juno_sd_open", Result.WORD, List.of(literal(0)), ShimFeature.SD);
        shim(Intrinsic.SD_FILE_AVAILABLE, "juno_sd_file_available", Result.WORD, List.of(RECEIVER),
                ShimFeature.SD);
        shim(Intrinsic.SD_FILE_READ, "juno_sd_file_read", Result.WORD, List.of(RECEIVER), ShimFeature.SD);
        shim(Intrinsic.SD_FILE_CLOSE, "juno_sd_file_close", Result.NONE, List.of(RECEIVER), ShimFeature.SD);
        shim(Intrinsic.SD_APPEND_LINE, "juno_sd_file_append_line", Result.WORD, List.of(literal(0), arg(0)), ShimFeature.SD);
        shim(Intrinsic.SD_APPEND_TEXT, "juno_sd_file_append_text", Result.WORD, List.of(literal(0), arg(0)),
                ShimFeature.SD);
        shim(Intrinsic.SD_REMOVE, "juno_sd_remove", Result.WORD, List.of(literal(0)), ShimFeature.SD);
        shim(Intrinsic.SD_TOTAL_KIB, "juno_sd_total_kib", Result.WORD, List.of(), ShimFeature.SD,
                ShimFeature.SD_CAPACITY);
        shim(Intrinsic.SD_FREE_KIB, "juno_sd_free_kib", Result.WORD, List.of(), ShimFeature.SD,
                ShimFeature.SD_CAPACITY);
        registerRandomAccessFile();
        shim(Intrinsic.PROPERTIES_NEW, "juno_properties_new", Result.WORD, List.of(), ShimFeature.SD);
        shim(Intrinsic.PROPERTIES_LOAD, "juno_properties_load", Result.NONE, List.of(RECEIVER, arg(0)),
                ShimFeature.SD);
        shim(Intrinsic.PROPERTIES_GET, "juno_properties_get", Result.WORD, List.of(RECEIVER, arg(0)),
                ShimFeature.SD);
        shim(Intrinsic.PROPERTIES_GET_DEFAULT, "juno_properties_get_default", Result.WORD,
                List.of(RECEIVER, arg(0), arg(1)), ShimFeature.SD);
        shim(Intrinsic.PROPERTIES_SIZE, "juno_properties_size", Result.WORD, List.of(RECEIVER), ShimFeature.SD);
    }

    /**
     * Read-only {@code java.io.RandomAccessFile} on an SD card file; every call can raise a JDK exception. Buffer
     * reads take the array, its capacity (added by the front end, {@code -1} when unknown), offset and length.
     */
    private void registerRandomAccessFile() {
        ShimFeature[] runtime = {ShimFeature.SD, ShimFeature.EXCEPTIONS, ShimFeature.RANDOM_ACCESS_FILE};
        List<Operand> buffer = List.of(RECEIVER, arg(0), arg(1), arg(2), arg(3));
        shim(Intrinsic.RAF_OPEN, "juno_raf_open", Result.WORD, List.of(literal(0)), runtime);
        shim(Intrinsic.RAF_READ, "juno_raf_read", Result.WORD, List.of(RECEIVER), runtime);
        shim(Intrinsic.RAF_READ_BYTES, "juno_raf_read_bytes", Result.WORD, buffer, runtime);
        shim(Intrinsic.RAF_READ_FULLY, "juno_raf_read_fully", Result.NONE, buffer, runtime);
        shim(Intrinsic.RAF_SEEK, "juno_raf_seek", Result.NONE,
                List.of(RECEIVER, new ArgumentLow(0), new ArgumentHigh(0)), runtime);
        shim(Intrinsic.RAF_GET_FILE_POINTER, "juno_raf_get_file_pointer", Result.WIDE, List.of(RECEIVER), runtime);
        shim(Intrinsic.RAF_LENGTH, "juno_raf_length", Result.WIDE, List.of(RECEIVER), runtime);
        shim(Intrinsic.RAF_SKIP_BYTES, "juno_raf_skip_bytes", Result.WORD, List.of(RECEIVER, arg(0)), runtime);
        shim(Intrinsic.RAF_CLOSE, "juno_raf_close", Result.NONE, List.of(RECEIVER), runtime);
    }

    /** USB mouse, servos, LEGO Powered Up hubs, and the Wi-Fi radio. */
    private void registerPeripherals() {
        shim(Intrinsic.M5_DISPLAY_BEGIN, "juno_m5_display_begin", Result.NONE, List.of(),
                ShimFeature.M5_DEVICE, ShimFeature.M5_DISPLAY);
        shim(Intrinsic.M5_DISPLAY_WIDTH, "juno_m5_display_width", Result.WORD, List.of(),
                ShimFeature.M5_DEVICE, ShimFeature.M5_DISPLAY);
        shim(Intrinsic.M5_DISPLAY_HEIGHT, "juno_m5_display_height", Result.WORD, List.of(),
                ShimFeature.M5_DEVICE, ShimFeature.M5_DISPLAY);
        shim(Intrinsic.M5_DISPLAY_FILL_SCREEN, "juno_m5_display_fill_screen", Result.NONE, List.of(arg(0)),
                ShimFeature.M5_DEVICE, ShimFeature.M5_DISPLAY);
        shim(Intrinsic.M5_DISPLAY_DRAW_PIXEL, "juno_m5_display_draw_pixel", Result.NONE,
                List.of(arg(0), arg(1), arg(2)), ShimFeature.M5_DEVICE, ShimFeature.M5_DISPLAY);
        shim(Intrinsic.M5_DISPLAY_FILL_RECT, "juno_m5_display_fill_rect", Result.NONE,
                List.of(arg(0), arg(1), arg(2), arg(3), arg(4)), ShimFeature.M5_DEVICE, ShimFeature.M5_DISPLAY);
        shim(Intrinsic.M5_DISPLAY_SET_CURSOR, "juno_m5_display_set_cursor", Result.NONE,
                List.of(arg(0), arg(1)), ShimFeature.M5_DEVICE, ShimFeature.M5_DISPLAY);
        shim(Intrinsic.M5_DISPLAY_SET_TEXT_COLOR, "juno_m5_display_set_text_color", Result.NONE,
                List.of(arg(0), arg(1)), ShimFeature.M5_DEVICE, ShimFeature.M5_DISPLAY);
        shim(Intrinsic.M5_DISPLAY_SET_TEXT_SIZE, "juno_m5_display_set_text_size", Result.NONE, List.of(arg(0)),
                ShimFeature.M5_DEVICE, ShimFeature.M5_DISPLAY);
        shim(Intrinsic.M5_DISPLAY_PRINT_CHAR, "juno_m5_display_print_char", Result.NONE, List.of(arg(0)),
                ShimFeature.M5_DEVICE, ShimFeature.M5_DISPLAY);
        shim(Intrinsic.M5_DISPLAY_PRINT_STRING, "juno_m5_display_print", Result.NONE, List.of(STRING_ARGUMENT),
                ShimFeature.M5_DEVICE, ShimFeature.M5_DISPLAY);
        shim(Intrinsic.M5_DISPLAY_PRINTLN_STRING, "juno_m5_display_println", Result.NONE, List.of(STRING_ARGUMENT),
                ShimFeature.M5_DEVICE, ShimFeature.M5_DISPLAY);
        shim(Intrinsic.M5_KEYBOARD_BEGIN, "juno_m5_keyboard_begin", Result.NONE, List.of(),
                ShimFeature.M5_DEVICE, ShimFeature.M5_KEYBOARD);
        shim(Intrinsic.M5_KEYBOARD_UPDATE, "juno_m5_keyboard_update", Result.NONE, List.of(),
                ShimFeature.M5_DEVICE, ShimFeature.M5_KEYBOARD);
        shim(Intrinsic.M5_KEYBOARD_IS_CHANGED, "juno_m5_keyboard_is_changed", Result.WORD, List.of(),
                ShimFeature.M5_DEVICE, ShimFeature.M5_KEYBOARD);
        shim(Intrinsic.M5_KEYBOARD_IS_PRESSED, "juno_m5_keyboard_is_pressed", Result.WORD, List.of(),
                ShimFeature.M5_DEVICE, ShimFeature.M5_KEYBOARD);
        shim(Intrinsic.M5_KEYBOARD_IS_KEY_PRESSED, "juno_m5_keyboard_is_key_pressed", Result.WORD, List.of(arg(0)),
                ShimFeature.M5_DEVICE, ShimFeature.M5_KEYBOARD);
        shim(Intrinsic.M5_KEYBOARD_KEY_COUNT, "juno_m5_keyboard_key_count", Result.WORD, List.of(),
                ShimFeature.M5_DEVICE, ShimFeature.M5_KEYBOARD);
        shim(Intrinsic.M5_KEYBOARD_KEY_AT, "juno_m5_keyboard_key_at", Result.WORD, List.of(arg(0)),
                ShimFeature.M5_DEVICE, ShimFeature.M5_KEYBOARD);
        shim(Intrinsic.M5_KEYBOARD_IS_SHIFT_PRESSED, "juno_m5_keyboard_is_shift_pressed", Result.WORD, List.of(),
                ShimFeature.M5_DEVICE, ShimFeature.M5_KEYBOARD);
        shim(Intrinsic.M5_KEYBOARD_IS_CONTROL_PRESSED, "juno_m5_keyboard_is_control_pressed", Result.WORD, List.of(),
                ShimFeature.M5_DEVICE, ShimFeature.M5_KEYBOARD);
        shim(Intrinsic.M5_KEYBOARD_IS_ALT_PRESSED, "juno_m5_keyboard_is_alt_pressed", Result.WORD, List.of(),
                ShimFeature.M5_DEVICE, ShimFeature.M5_KEYBOARD);
        shim(Intrinsic.M5_KEYBOARD_IS_FN_PRESSED, "juno_m5_keyboard_is_fn_pressed", Result.WORD, List.of(),
                ShimFeature.M5_DEVICE, ShimFeature.M5_KEYBOARD);
        shim(Intrinsic.M5_KEYBOARD_IS_OPTION_PRESSED, "juno_m5_keyboard_is_option_pressed", Result.WORD, List.of(),
                ShimFeature.M5_DEVICE, ShimFeature.M5_KEYBOARD);
        shim(Intrinsic.M5_AUDIO_TONE, "juno_m5_audio_tone", Result.NONE, List.of(arg(0), arg(1)),
                ShimFeature.M5_DEVICE, ShimFeature.M5_AUDIO);
        shim(Intrinsic.M5_AUDIO_SET_VOLUME, "juno_m5_audio_set_volume", Result.NONE, List.of(arg(0)),
                ShimFeature.M5_DEVICE, ShimFeature.M5_AUDIO);
        shim(Intrinsic.M5_AUDIO_STOP, "juno_m5_audio_stop", Result.NONE, List.of(),
                ShimFeature.M5_DEVICE, ShimFeature.M5_AUDIO);
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
        shim(Intrinsic.LEGO_HUB_HOLD_MOTOR, "juno_lego_hub_hold_motor", Result.NONE, List.of(arg(0)),
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
        shim(Intrinsic.LEGO_HUB_BATTERY_PERCENT, "juno_lego_hub_battery_percent", Result.WORD, List.of(),
                ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_BUTTON_PRESSED, "juno_lego_hub_button_pressed", Result.WORD, List.of(),
                ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_RSSI, "juno_lego_hub_rssi", Result.WORD, List.of(), ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_FIRMWARE_VERSION, "juno_lego_hub_firmware_version", Result.WORD, List.of(),
                ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_HARDWARE_VERSION, "juno_lego_hub_hardware_version", Result.WORD, List.of(),
                ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_PORT_DEVICE, "juno_lego_hub_port_device", Result.WORD, List.of(arg(0)),
                ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_NAME, "juno_lego_hub_name", Result.WORD, List.of(arg(0), arg(1)),
                ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_READ_SENSOR_VALUE, "juno_lego_hub_read_sensor_value", Result.WORD,
                List.of(arg(0), arg(1), arg(2)), ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_SENSOR_REPORT_SIZE, "juno_lego_hub_sensor_report_size", Result.WORD,
                List.of(arg(0)), ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_SENSOR_REPORT_COUNT, "juno_lego_hub_sensor_report_count", Result.WORD,
                List.of(arg(0)), ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_SET_LED_RGB, "juno_lego_hub_set_led_rgb", Result.NONE,
                List.of(arg(0), arg(1), arg(2)), ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_LINK_MOTORS, "juno_lego_hub_link_motors", Result.WORD,
                List.of(arg(0), arg(1)), ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_UNLINK_MOTORS, "juno_lego_hub_unlink_motors", Result.NONE,
                List.of(arg(0)), ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_SET_LINKED_MOTOR_POWER, "juno_lego_hub_set_linked_motor_power", Result.NONE,
                List.of(arg(0), arg(1), arg(2)), ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.LEGO_HUB_BRAKE_LINKED_MOTORS, "juno_lego_hub_brake_linked_motors", Result.NONE,
                List.of(arg(0)), ShimFeature.LEGO_POWERED_UP);
        shim(Intrinsic.PARALLEL_BUS_BEGIN, "juno_parallel_bus_begin", Result.NONE, List.of(arg(0), arg(1)),
                ShimFeature.PARALLEL_BUS);
        shim(Intrinsic.PARALLEL_BUS_WRITE, "juno_parallel_bus_write", Result.NONE, List.of(arg(0)),
                ShimFeature.PARALLEL_BUS);
        shim(Intrinsic.PARALLEL_BUS_REPEAT16, "juno_parallel_bus_repeat16", Result.NONE, List.of(arg(0), arg(1)),
                ShimFeature.PARALLEL_BUS);
        shim(Intrinsic.IR_BEGIN, "juno_ir_begin", Result.NONE, List.of(arg(0), arg(1), arg(2)), ShimFeature.INFRARED);
        shim(Intrinsic.IR_RECEIVING, "juno_ir_receiving", Result.WORD, List.of(), ShimFeature.INFRARED);
        shim(Intrinsic.IR_READ_BYTE, "juno_ir_read_byte", Result.WORD, List.of(arg(0)), ShimFeature.INFRARED);
        shim(Intrinsic.IR_WRITE_BYTE, "juno_ir_write_byte", Result.NONE, List.of(arg(0)), ShimFeature.INFRARED);
        shim(Intrinsic.IR_ECHO_BYTE, "juno_ir_echo_byte", Result.WORD, List.of(arg(0), arg(1)), ShimFeature.INFRARED);
        shim(Intrinsic.IR_MARK, "juno_ir_mark", Result.NONE, List.of(arg(0)), ShimFeature.INFRARED);
        shim(Intrinsic.IR_SPACE, "juno_ir_space", Result.NONE, List.of(arg(0)), ShimFeature.INFRARED);
        shim(Intrinsic.I2C_BEGIN, "juno_i2c_begin", Result.NONE, List.of(), ShimFeature.I2C);
        shim(Intrinsic.I2C_WRITE_REGISTER, "juno_i2c_write_register", Result.NONE,
                List.of(arg(0), arg(1), arg(2)), ShimFeature.I2C);
        shim(Intrinsic.I2C_READ_REGISTER, "juno_i2c_read_register", Result.WORD, List.of(arg(0), arg(1)),
                ShimFeature.I2C);
        shim(Intrinsic.I2C_READ_REGISTER16, "juno_i2c_read_register16", Result.WORD, List.of(arg(0), arg(1)),
                ShimFeature.I2C);
        shim(Intrinsic.WIFI_BEGIN, "juno_wifi_begin", Result.NONE, List.of(arg(0), arg(1)), ShimFeature.WIFI);
        shim(Intrinsic.WIFI_BEGIN_AP, "juno_wifi_begin_ap", Result.NONE, List.of(arg(0), arg(1)), ShimFeature.WIFI);
        shim(Intrinsic.WIFI_STATUS, "juno_wifi_status", Result.WORD, List.of(), ShimFeature.WIFI);
        shim(Intrinsic.WIFI_LOCAL_IP, "juno_wifi_local_ip", Result.NONE, List.of(arg(0)), ShimFeature.WIFI);
        shim(Intrinsic.WIFI_GATEWAY_IP, "juno_wifi_gateway_ip", Result.NONE, List.of(arg(0)), ShimFeature.WIFI);
        shim(Intrinsic.WIFI_MAC_ADDRESS, "juno_wifi_mac_address", Result.NONE, List.of(arg(0)), ShimFeature.WIFI);
        shim(Intrinsic.UDP_LISTEN, "juno_udp_listen", Result.WORD, List.of(arg(0)), ShimFeature.UDP);
        shim(Intrinsic.UDP_SEND, "juno_udp_send", Result.WORD,
                List.of(arg(0), arg(1), arg(2), arg(3)), ShimFeature.UDP);
        shim(Intrinsic.UDP_BROADCAST, "juno_udp_broadcast", Result.WORD,
                List.of(arg(0), arg(1), arg(2)), ShimFeature.UDP);
        shim(Intrinsic.UDP_RECEIVE, "juno_udp_receive", Result.WORD,
                List.of(arg(0), arg(1), arg(2)), ShimFeature.UDP);
        shim(Intrinsic.UDP_STOP, "juno_udp_stop", Result.NONE, List.of(), ShimFeature.UDP);
        shim(Intrinsic.TCP_OPEN, "juno_tcp_open", Result.WORD, List.of(arg(0), arg(1)), ShimFeature.TCP);
        shim(Intrinsic.TCP_WRITE, "juno_tcp_write", Result.WORD, List.of(arg(0), arg(1)), ShimFeature.TCP);
        shim(Intrinsic.TCP_READ, "juno_tcp_read", Result.WORD,
                List.of(arg(0), arg(1), arg(2), arg(3)), ShimFeature.TCP);
        shim(Intrinsic.TCP_CLOSE, "juno_tcp_close", Result.NONE, List.of(), ShimFeature.TCP);
    }

    /**
     * {@code HttpClient}'s plain and TLS requests, which {@code HttpUrlCall} derives from the call's URL: host literal,
     * port, path literal (plus a body literal for requests that carry one), then response/headers buffers and their
     * lengths.
     */
    private void registerHttpClients() {
        List<Operand> bodiless = List.of(literal(0), arg(0), literal(1), arg(1), arg(2), arg(3), arg(4), arg(5));
        List<Operand> withBody = List.of(literal(0), arg(0), literal(1), literal(2),
                arg(1), arg(2), arg(3), arg(4), arg(5));
        shim(Intrinsic.HTTP_GET, "juno_http_get", Result.WORD, bodiless, ShimFeature.HTTP);
        shim(Intrinsic.HTTP_DELETE, "juno_http_delete", Result.WORD, bodiless, ShimFeature.HTTP);
        shim(Intrinsic.HTTP_POST, "juno_http_post", Result.WORD, withBody, ShimFeature.HTTP);
        List<Operand> bufferedPost = List.of(literal(0), arg(0), literal(1),
                arg(1), arg(2), arg(3), arg(4), arg(5), arg(6), arg(7), arg(8), arg(9));
        shim(Intrinsic.HTTP_POST_BUFFER, "juno_http_post_buffer", Result.WORD, bufferedPost, ShimFeature.HTTP);
        shim(Intrinsic.HTTP_PATCH, "juno_http_patch", Result.WORD, withBody, ShimFeature.HTTP);
        shim(Intrinsic.HTTP_QUERY, "juno_http_query", Result.WORD, withBody, ShimFeature.HTTP);
        shim(Intrinsic.HTTPS_GET, "juno_https_get", Result.WORD, bodiless, ShimFeature.HTTPS);
        shim(Intrinsic.HTTPS_DELETE, "juno_https_delete", Result.WORD, bodiless, ShimFeature.HTTPS);
        shim(Intrinsic.HTTP_GET_PATH_BUFFER, "juno_http_get_path_buffer", Result.WORD,
                List.of(literal(0), arg(0), arg(1), arg(2), arg(3), arg(4), arg(5), arg(6), arg(7)),
                ShimFeature.HTTP, ShimFeature.HTTP_PATH_BUFFER);
        shim(Intrinsic.HTTPS_GET_PATH_BUFFER, "juno_https_get_path_buffer", Result.WORD,
                List.of(literal(0), arg(0), arg(1), arg(2), arg(3), arg(4), arg(5), arg(6), arg(7)),
                ShimFeature.HTTPS, ShimFeature.HTTPS_PATH_BUFFER);
        shim(Intrinsic.HTTPS_POST, "juno_https_post", Result.WORD, withBody, ShimFeature.HTTPS);
        shim(Intrinsic.HTTPS_POST_BUFFER, "juno_https_post_buffer", Result.WORD, bufferedPost, ShimFeature.HTTPS);
        shim(Intrinsic.HTTPS_PATCH, "juno_https_patch", Result.WORD, withBody, ShimFeature.HTTPS);
        shim(Intrinsic.HTTPS_QUERY, "juno_https_query", Result.WORD, withBody, ShimFeature.HTTPS);
        shim(Intrinsic.HTTPS_REQUEST_BUFFER, "juno_https_request_buffer", Result.WORD,
                List.of(arg(0), arg(1), arg(2), arg(3), arg(4), arg(5), arg(6), arg(7), arg(8),
                        arg(9), arg(10), arg(11), arg(12), arg(13)),
                ShimFeature.HTTPS, ShimFeature.HTTPS_REQUEST_BUFFER);
    }

    private void registerEmail() {
        List<Integer> smtpStrings = List.of(0, 2, 3, 4, 5, 6, 7);
        shim(Intrinsic.SMTP_SEND, "juno_smtp_send", Result.WORD,
                parameters(smtpStrings, 0, 1, 2, 3, 4, 5, 6, 7), ShimFeature.SMTP);
        shim(Intrinsic.SMTP_SEND_TLS, "juno_smtp_send_tls", Result.WORD,
                parameters(smtpStrings, 0, 1, 2, 3, 4, 5, 6, 7), ShimFeature.SMTP_TLS);
        List<Integer> pop3Strings = List.of(0, 2, 3);
        shim(Intrinsic.POP3_MESSAGE_COUNT, "juno_pop3_message_count", Result.WORD,
                parameters(pop3Strings, 0, 1, 2, 3), ShimFeature.POP3);
        shim(Intrinsic.POP3_READ_LATEST, "juno_pop3_read_latest", Result.WORD,
                parameters(pop3Strings, 0, 1, 2, 3, 4, 5, 6, 7, 8), ShimFeature.POP3);
        shim(Intrinsic.POP3_READ_SUBJECT, "juno_pop3_read_subject", Result.WORD,
                parameters(pop3Strings, 0, 1, 2, 3, 4, 5, 6), ShimFeature.POP3);
    }

    /** {@code Json}: the scanner is compiled Java; only copying a value's raw text into a runtime String is native. */
    private void registerJson() {
        shim(Intrinsic.STRING_FROM_BYTES, "juno_string_from_bytes", Result.WORD, List.of(arg(0), arg(1), arg(2)),
                ShimFeature.RUNTIME_STRINGS, ShimFeature.STRING_FROM_BYTES);
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

    private Operand arg(int index) {
        return new Argument(index);
    }

    private Operand literal(int index) {
        return new Literal(index);
    }

    /**
     * The operands for a call whose declared parameters are {@code 0..n-1}: the {@code stringParameters} are its
     * {@code String} parameters, each passed as a literal or a runtime value; the rest are runtime values. Either every
     * string is a literal, which leaves the literals out of the runtime list, or none is, and a call that mixes the two
     * is rejected.
     */
    private List<Operand> parameters(List<Integer> stringParameters, int... declared) {
        List<Operand> operands = new ArrayList<>(declared.length);
        for (int parameter : declared) {
            operands.add(new Parameter(parameter, stringParameters));
        }
        return List.copyOf(operands);
    }

    private Operand immediate(int value) {
        return new Immediate(value);
    }

    /** A {@code long}/{@code double} argument's two words, low first. */
    private List<Operand> wide(int index) {
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
        target.emitShimCall(output, frame, shimCall.function(), words);
        switch (shimCall.result()) {
            case NONE -> { }
            case WORD -> call.target().ifPresent(value -> target.storeResult(output, frame, value));
            case WIDE -> call.target().ifPresent(value -> target.storeWideResult(output, frame, value));
            case FIRST_ARGUMENT -> call.target().ifPresent(
                    value -> target.copy(output, frame, call.arguments().get(0), value));
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
            if (argument.type().isWide()) {
                if (words.size() % 2 != 0) {
                    words.add(new WordSource.Immediate(0));
                }
                words.add(new WordSource.FromValueLow(argument));
                words.add(new WordSource.FromValueHigh(argument));
            } else {
                words.add(new WordSource.FromValue(argument));
            }
        }
        target.emitShimCall(output, frame, mathRuntime.symbol(call.intrinsic()), words);
        call.target().ifPresent(value -> storeTypedResult(output, frame, value));
    }

    /**
     * Every {@code java.math} intrinsic is a plain shim call named after the intrinsic: the receiver (if any) and
     * each argument in order, a {@code long}/{@code double} as a low/high pair padded to an even word as AAPCS
     * requires, and a one- or two-word result. Handles and {@code RoundingMode} ordinals are single words.
     */
    private void emitBigNumberCall(StringBuilder output, FrameLayout frame, IrInstruction.IntrinsicCall call) {
        features.add(ShimFeature.BIG_NUMBERS);
        features.add(ShimFeature.EXCEPTIONS);
        if (call.intrinsic() == Intrinsic.BIG_DECIMAL_VALUE_OF_DOUBLE) {
            features.add(ShimFeature.BIG_DECIMAL_DOUBLE);
            features.add(ShimFeature.RUNTIME_STRINGS);
        }
        List<WordSource> words = new ArrayList<>();
        call.receiver().ifPresent(receiver -> words.add(new WordSource.FromValue(receiver)));
        for (Value argument : call.arguments()) {
            if (argument.type().isWide()) {
                if (words.size() % 2 != 0) {
                    words.add(new WordSource.Immediate(0));
                }
                words.add(new WordSource.FromValueLow(argument));
                words.add(new WordSource.FromValueHigh(argument));
            } else {
                words.add(new WordSource.FromValue(argument));
            }
        }
        target.emitShimCall(output, frame, bigNumberMethods.shimSymbol(call.intrinsic()), words);
        call.target().ifPresent(value -> storeTypedResult(output, frame, value));
    }

    private void storeTypedResult(StringBuilder output, FrameLayout frame, Value value) {
        if (value.type().isWide()) {
            target.storeWideResult(output, frame, value);
        } else {
            target.storeResult(output, frame, value);
        }
    }
}
