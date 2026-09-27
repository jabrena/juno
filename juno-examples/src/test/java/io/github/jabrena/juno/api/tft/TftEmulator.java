package io.github.jabrena.juno.api.tft;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a TFT program's real {@code main()} against the shield emulated by the test doubles in
 * {@code io.github.jabrena.juno.api} (see the test {@code Gpio}), taps the screen on a script and
 * returns what is on the screen at a chosen moment of simulated time.
 *
 * <p>Each run gets its own class loader, so the program, the {@code TftTouchShield} driver and the
 * emulated hardware all start from fresh static state, whatever ran before.
 */
final class TftEmulator {
    /** A finger on the screen at ({@code x}, {@code y}), in the program's own screen coordinates. */
    record Tap(int atMillis, int x, int y) {
    }

    private static final int TAP_MILLIS = 80;

    private TftEmulator() {
    }

    /** Runs {@code mainClass} until {@code stopAtMillis} of simulated time and captures the screen. */
    static BufferedImage run(String mainClass, int stopAtMillis, List<Tap> taps) throws Exception {
        try (URLClassLoader loader = isolatedLoader()) {
            Class<?> clock = loader.loadClass("io.github.jabrena.juno.api.Clock");
            Class<?> delay = loader.loadClass("io.github.jabrena.juno.api.Delay");
            Class<?> gpio = loader.loadClass("io.github.jabrena.juno.api.io.Gpio");
            Class<?> shield = loader.loadClass("io.github.jabrena.juno.api.tft.TftTouchShield");
            Method elapsed = clock.getMethod("elapsed");
            Field touch = gpio.getField("touch");
            Field rotation = shield.getDeclaredField("rotation");
            rotation.setAccessible(true);

            Runnable script = () -> {
                try {
                    int now = (int) elapsed.invoke(null);
                    Tap current = null;
                    for (Tap tap : taps) {
                        if (now >= tap.atMillis() && now < tap.atMillis() + TAP_MILLIS) {
                            current = tap;
                        }
                    }
                    touch.set(null, current == null ? null : toPanel(current, rotation.getInt(null)));
                    if (now >= stopAtMillis) {
                        throw new Stop();
                    }
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            };
            delay.getField("afterAdvance").set(null, script);
            try {
                loader.loadClass(mainClass).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            } catch (InvocationTargetException exception) {
                if (!(exception.getCause() instanceof Stop)) {
                    throw exception;
                }
            }
            int width = (int) shield.getMethod("width").invoke(null);
            int height = (int) shield.getMethod("height").invoke(null);
            int stride = gpio.getField("STRIDE").getInt(null);
            int[] framebuffer = (int[]) gpio.getField("FRAMEBUFFER").get(null);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setRGB(x, y, toRgb(framebuffer[y * stride + x]));
                }
            }
            return image;
        }
    }

    /** Converts a screen position in the given rotation back to portrait panel coordinates. */
    private static int[] toPanel(Tap tap, int rotation) {
        int x = tap.x();
        int y = tap.y();
        if (rotation == TftTouchShield.LANDSCAPE) {
            return new int[] {239 - y, x};
        }
        if (rotation == TftTouchShield.PORTRAIT_FLIPPED) {
            return new int[] {239 - x, 319 - y};
        }
        if (rotation == TftTouchShield.LANDSCAPE_FLIPPED) {
            return new int[] {y, 319 - x};
        }
        return new int[] {x, y};
    }

    private static int toRgb(int color) {
        int red = (color >> 11) & 31;
        int green = (color >> 5) & 63;
        int blue = color & 31;
        return (red * 255 / 31) << 16 | (green * 255 / 63) << 8 | (blue * 255 / 31);
    }

    /** The test classpath (test classes, so the hardware doubles, first) with no parent but the JDK. */
    private static URLClassLoader isolatedLoader() throws Exception {
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<URL> urls = new ArrayList<>();
        for (String entry : classpath.split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                urls.add(new File(entry).toURI().toURL());
            }
        }
        return new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
    }

    /** Unwinds the program once the script's time is up. */
    private static final class Stop extends Error {
        Stop() {
            super(null, null, false, false);
        }
    }
}
