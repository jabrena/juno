package demo;

import io.github.jabrena.juno.api.io.i2c.I2c;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/** Register access on an I2C device. */
public final class I2cApi {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        I2c.begin();
        
        I2c.writeRegister(0x28, 0x3D, 0x0C);
        Serial.println(I2c.readRegister(0x28, 0x00));
        Serial.println(I2c.readRegister16(0x28, 0x1A));
    }
}
