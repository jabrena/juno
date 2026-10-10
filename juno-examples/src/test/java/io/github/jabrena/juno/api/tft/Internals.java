package io.github.jabrena.juno.api.tft;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.Gpio;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Reaches into a game's private static methods and fields, so rule tests can exercise the same
 * code the board runs without widening its visibility, and scripts taps on the emulated panel.
 */
public final class Internals {
    private Internals() {
    }

    /** Calls the private static method {@code name} taking {@code args.length} parameters. */
    public static Object call(Class<?> type, String name, Object... args) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                try {
                    return method.invoke(null, args);
                } catch (InvocationTargetException exception) {
                    if (exception.getCause() instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw new IllegalStateException(exception.getCause());
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        }
        throw new IllegalArgumentException(type.getSimpleName() + " has no method " + name + "/" + args.length);
    }

    public static int callInt(Class<?> type, String name, Object... args) {
        return (int) call(type, name, args);
    }

    public static boolean callBoolean(Class<?> type, String name, Object... args) {
        return (boolean) call(type, name, args);
    }

    public static Object get(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static int getInt(Class<?> type, String name) {
        return (int) get(type, name);
    }

    public static void set(Class<?> type, String name, Object value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * Initializes the driver (which also loads the touch calibration), puts the display in portrait
     * rotation (screen coordinates equal panel coordinates) and presses
     * the panel at {@code taps.get()} on every {@code period}-th step of simulated time, releasing
     * it in between so each press registers as a separate tap. A null position skips that press.
     */
    public static void tapEvery(int period, Supplier<int[]> taps) {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT);
        int[] step = {0};
        Delay.afterAdvance = () -> {
            Gpio.touch = step[0] % period == 0 ? taps.get() : null;
            step[0] = step[0] + 1;
        };
    }

    public static void stopTapping() {
        Delay.afterAdvance = () -> { };
        Gpio.touch = null;
    }
}
