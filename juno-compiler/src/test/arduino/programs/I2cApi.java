package demo;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Register access on an I2C device. */
public final class I2cApi {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        I2c i2c = MCU.getI2c();
        i2c.begin();

        i2c.writeRegister(0x28, 0x3D, 0x0C);
        Serial.println(i2c.readRegister(0x28, 0x00));
        Serial.println(i2c.readRegister16(0x28, 0x1A));
    }
}
