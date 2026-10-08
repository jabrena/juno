package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.imu.Bno055;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * A Gyro Boy: a two-wheeled robot that balances itself upright. Its two motors are on ports A (left)
 * and B (right) of a Technic Hub, Move Hub or City Hub, which the Arduino drives over Bluetooth LE.
 * The balance loop runs on the Arduino, 50 times a second, from the BNO055 of the Arduino 9 Axis
 * Motion Shield (pitch and gyroscope rate over I2C, wired to the board's SDA/SCL, so reading the
 * angle never waits for Bluetooth). Each tick sends one linked-motor command to the hub, and only when
 * the power changed. The Bluetooth write is the slow part of the loop, so keep the hub close to the board;
 * if the link drops, the program stops balancing and waits for the hub again.
 *
 * <p>Mount the shield on the robot with its X axis along the wheel axle, so that tipping the robot
 * forwards or backwards changes the pitch. Start: hold the robot upright, at the angle where it would
 * balance, and wait for the "Upright" message; the angle held for {@code CALIBRATE_MILLIS} becomes the
 * balance point. Let go and it balances until it tips over more than {@code FALL_DEGREES}, when the
 * motors brake and it waits to be held upright again. Run {@code PoweredUpHubLatencyProbe} first to see
 * how fast your hub answers. If the robot falls faster in the direction it fell, flip {@code
 * ANGLE_SIGN}; if the wheels drive the wrong way, flip {@code MOTOR_SIGN}; if it oscillates, lower
 * {@code KP} or raise {@code KD}; if the wheels do not move until the robot is far over, raise
 * {@code MIN_POWER}; if it creeps in one direction, raise {@code KI}. The numbers are starting points,
 * not verified on a particular chassis. Pick the board with {@code -Djuno.board}.
 */
public class PoweredUpGyroBoy {
    private static final int LEFT = PoweredUpHubRemote.PORT_A;
    private static final int RIGHT = PoweredUpHubRemote.PORT_B;

    /** {@code 1} or {@code -1}: makes a positive angle mean the robot is leaning forwards. */
    private static final int ANGLE_SIGN = 1;
    /** {@code 1} or {@code -1}: swap if the wheels drive the wrong way for a positive power. */
    private static final int MOTOR_SIGN = 1;

    /** The loop period: one Bluetooth write per tick, and the BNO055 updates its output every 10 ms. */
    private static final int LOOP_MILLIS = 20;
    private static final int UNITS_PER_DEGREE = 16;
    private static final int FALL_DEGREES = 30;
    private static final int UPRIGHT_DEGREES = 3;
    private static final int CALIBRATE_MILLIS = 1500;
    private static final int CALIBRATE_SAMPLES = 50;

    /** Controller gains. {@code power = (KP * angle + KD * rate + KI * integral) / GAIN_SCALE}, angle and rate in sixteenths. */
    private static final int KP = 128;
    private static final int KD = 5;
    private static final int KI = 1;
    private static final int GAIN_SCALE = 256;
    private static final int INTEGRAL_LIMIT = 4000;
    private static final int MAX_POWER = 100;
    /** The smallest power in percent that makes the motors turn. */
    private static final int MIN_POWER = 12;

    private int balancePoint;
    private int integral;
    private int motors;
    private int sentPower;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        PoweredUpGyroBoy app = new PoweredUpGyroBoy();
        if (!Bno055.begin()) {
            Serial.println("No BNO055 found on the I2C bus");
            return;
        }

        while (true) {
            if (!PoweredUpHubRemote.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                PoweredUpHubRemote.connect(0);
                app.motors = PoweredUpHubRemote.linkMotors(LEFT, RIGHT);
                app.sentPower = 0;
                if (app.motors < 0) {
                    Serial.println("The hub did not link motors A and B");
                    PoweredUpHubRemote.disconnect();
                    continue;
                }
                PoweredUpHubRemote.setLedColor(PoweredUpHubRemote.COLOR_GREEN);
            }
            app.waitForUpright();
            Serial.println("Balancing");
            app.balance();
            Serial.println("Stopped");
        }
    }

    /**
     * Stops the motors and waits until the robot has been held still near the same angle for {@code
     * CALIBRATE_MILLIS}, then takes the average pitch of the next samples as the balance point.
     */
    private void waitForUpright() {
        drive(0);
        Serial.println("Hold the robot upright...");
        int steadySince = Clock.millis();
        int reference = Bno055.pitchRaw();
        while (Clock.millis() - steadySince < CALIBRATE_MILLIS) {
            Delay.millis(LOOP_MILLIS);
            int pitch = Bno055.pitchRaw();
            if (Math.abs(pitch - reference) > UPRIGHT_DEGREES * UNITS_PER_DEGREE) {
                reference = pitch;
                steadySince = Clock.millis();
            }
        }
        int sum = 0;
        for (int i = 0; i < CALIBRATE_SAMPLES; i++) {
            sum += Bno055.pitchRaw();
            Delay.millis(LOOP_MILLIS);
        }
        balancePoint = sum / CALIBRATE_SAMPLES;
        integral = 0;
        Serial.println("Upright at " + balancePoint / UNITS_PER_DEGREE + " degrees");
    }

    /** Runs the balance loop until the robot tips over, then stops the motors. */
    private void balance() {
        int next = Clock.millis();
        boolean upright = true;
        while (upright) {
            if (!PoweredUpHubRemote.isConnected()) {
                return;
            }
            int angle = ANGLE_SIGN * (Bno055.pitchRaw() - balancePoint);
            int rate = ANGLE_SIGN * Bno055.gyroXRaw();
            if (Math.abs(angle) > FALL_DEGREES * UNITS_PER_DEGREE) {
                upright = false;
            } else {
                int power = controller(angle, rate);
                drive(power);
            }
            next += LOOP_MILLIS;
            while (Clock.millis() - next < 0) {
                // Wait for the next tick; a slow iteration just runs the next one straight away.
            }
        }
        drive(0);
    }

    /** The PD controller with a clamped integral term; returns a motor power in percent, {@code -100..100}. */
    private int controller(int angle, int rate) {
        integral += angle;
        if (integral > INTEGRAL_LIMIT) {
            integral = INTEGRAL_LIMIT;
        } else if (integral < -INTEGRAL_LIMIT) {
            integral = -INTEGRAL_LIMIT;
        }
        int power = (KP * angle + KD * rate + KI * integral) / GAIN_SCALE;
        return power > MAX_POWER ? MAX_POWER : power < -MAX_POWER ? -MAX_POWER : power;
    }

    /** Drives both wheels at a signed power in percent, skipping the Bluetooth write when nothing changed. */
    private void drive(int power) {
        int signed = power > MAX_POWER ? MAX_POWER : power < -MAX_POWER ? -MAX_POWER : power;
        int output = signed == 0 ? 0 : (signed > 0 ? MIN_POWER : -MIN_POWER) + signed * (MAX_POWER - MIN_POWER) / MAX_POWER;
        if (output == sentPower) {
            return;
        }
        sentPower = output;
        if (output == 0) {
            PoweredUpHubRemote.brakeLinkedMotors(motors);
        } else {
            PoweredUpHubRemote.setLinkedMotorPower(motors, MOTOR_SIGN * output, MOTOR_SIGN * output);
        }
    }
}
