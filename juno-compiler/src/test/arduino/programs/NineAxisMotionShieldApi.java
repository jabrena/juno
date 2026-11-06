package demo;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.imu.NineAxisMotionShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/** The BNO055 orientation sensor over I2C. */
public final class NineAxisMotionShieldApi {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        Gpio gpio = ArduinoUno.getGpio();
        I2c i2c = ArduinoUno.getI2c();
        NineAxisMotionShield imu = new NineAxisMotionShield(gpio, i2c);
        imu.reset();

        Serial.println(imu.getAngle());
        Serial.println(imu.getXAccel());
        Serial.println((int) imu.getAngularVelocity());
    }
}
