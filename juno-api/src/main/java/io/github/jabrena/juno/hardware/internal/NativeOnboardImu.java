package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.device.imu.Lsm6ds3;
import io.github.jabrena.juno.robotics.Accelerometer;
import io.github.jabrena.juno.robotics.Gyroscope;
import org.jspecify.annotations.Nullable;

/** Owns the one lazily initialized onboard IMU without putting mutable state on the public MCU facade. */
public final class NativeOnboardImu {
    private static @Nullable Lsm6ds3 imu;

    private NativeOnboardImu() {
    }

    /** The selected board's onboard accelerometer. */
    public static Accelerometer accelerometer() {
        return onboardImu();
    }

    /** The selected board's onboard gyroscope. */
    public static Gyroscope gyroscope() {
        return onboardImu();
    }

    private static Lsm6ds3 onboardImu() {
        Lsm6ds3 sensor = imu;
        if (sensor == null) {
            sensor = new Lsm6ds3(NativeGpio.gpio(), NativeSpi.spi(), NativeClock.clock());
            imu = sensor;
        }
        return sensor;
    }
}
