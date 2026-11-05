package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.robotics.Accelerometer;
import io.github.jabrena.juno.robotics.Gyroscope;
import io.github.jabrena.juno.robotics.LightDetector;
import io.github.jabrena.juno.robotics.SampleProvider;
import io.github.jabrena.juno.robotics.SensorMode;
import io.github.jabrena.juno.robotics.SensorModes;

/** The sensor contracts in juno.robotics, called through their interfaces with caller-owned buffers. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
public final class SensorContracts {

    static final class Temperature implements SensorMode {
        public String getName() {
            return "Temperature";
        }

        public int sampleSize() {
            return 1;
        }

        public void fetchSample(float[] sample, int offset) {
            sample[offset] = 21.5f;
        }
    }

    static final class Humidity implements SensorMode {
        public String getName() {
            return "Humidity";
        }

        public int sampleSize() {
            return 1;
        }

        public void fetchSample(float[] sample, int offset) {
            sample[offset] = 0.42f;
        }
    }

    static final class Climate implements SensorModes {
        private final SensorMode[] modes = {new Temperature(), new Humidity()};
        private int current;

        public String getName() {
            return modes[current].getName();
        }

        public int sampleSize() {
            return modes[current].sampleSize();
        }

        public void fetchSample(float[] sample, int offset) {
            modes[current].fetchSample(sample, offset);
        }

        public int getModeCount() {
            return modes.length;
        }

        public SensorMode getMode(int mode) {
            return modes[mode];
        }

        public SensorMode getMode(String modeName) {
            return modes[indexOf(modeName)];
        }

        public void setCurrentMode(int mode) {
            current = mode;
        }

        public void setCurrentMode(String modeName) {
            current = indexOf(modeName);
        }

        public int getCurrentMode() {
            return current;
        }

        private int indexOf(String modeName) {
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].getName().equals(modeName)) {
                    return i;
                }
            }
            return 0;
        }
    }

    static final class Light implements LightDetector {
        private float raw = 0.25f;

        public float getLightValue() {
            return (raw - getLow()) / (getHigh() - getLow()) * 100f;
        }

        public float getNormalizedLightValue() {
            return raw;
        }

        public float getHigh() {
            return 0.75f;
        }

        public float getLow() {
            return 0.05f;
        }
    }

    static final class Gyro implements Gyroscope {
        private final float raw = 12.75f;
        private float offset;
        private int angle = 33;

        public int getAngle() {
            return angle;
        }

        public float getAngularVelocity() {
            return raw - offset;
        }

        public void recalibrateOffset() {
            offset = 0.75f;
        }

        public void reset() {
            angle = 0;
        }
    }

    static final class Accel implements Accelerometer, SampleProvider {
        public int getXAccel() {
            return 1;
        }

        public int getYAccel() {
            return -2;
        }

        public int getZAccel() {
            return 9;
        }

        public int sampleSize() {
            return 3;
        }

        public void fetchSample(float[] sample, int offset) {
            sample[offset] = 0.5f;
            sample[offset + 1] = -1.5f;
            sample[offset + 2] = 9.81f;
        }
    }

    private static int hundredths(float value) {
        return Math.round(value * 100f);
    }

    public static void main(String[] args) {
        float[] sample = new float[4];

        SensorModes climate = new Climate();
        Serial.println(climate.getModeCount());
        Serial.println(climate.getName());
        climate.fetchSample(sample, 0);
        Serial.println(hundredths(sample[0]));
        climate.setCurrentMode("Humidity");
        Serial.println(climate.getCurrentMode());
        Serial.println(climate.getName());
        climate.fetchSample(sample, 1);
        Serial.println(hundredths(sample[1]));
        SensorMode temperature = climate.getMode("Temperature");
        Serial.println(temperature.getName());
        climate.setCurrentMode(0);
        Serial.println(climate.getMode(1).getName());
        Serial.println(climate.sampleSize());

        LightDetector light = new Light();
        Serial.println(hundredths(light.getLightValue()));
        Serial.println(hundredths(light.getNormalizedLightValue()));
        Serial.println(hundredths(light.getLow()));
        Serial.println(hundredths(light.getHigh()));


        Gyroscope gyro = new Gyro();
        Serial.println(gyro.getAngle());
        Serial.println(hundredths(gyro.getAngularVelocity()));
        gyro.recalibrateOffset();
        gyro.reset();
        Serial.println(gyro.getAngle());
        Serial.println(hundredths(gyro.getAngularVelocity()));

        Accelerometer accelerometer = new Accel();
        Serial.println(accelerometer.getXAccel() + accelerometer.getYAccel() + accelerometer.getZAccel());
        SampleProvider provider = new Accel();
        provider.fetchSample(sample, 1);
        Serial.println(provider.sampleSize());
        Serial.println(hundredths(sample[1]));
        Serial.println(hundredths(sample[2]));
        Serial.println(hundredths(sample[3]));
    }
}
