package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLightSensor;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/** Reads and two-point calibrates an RCX light sensor on input 1 through the Nano Connector Carrier. */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoCarrierRcxLightSensor {
    private static final int CHANNEL = 1;
    private static final int START_MILLIS = 10000;
    private static final int CALIBRATION_WAIT_MILLIS = 3000;
    private static final int CALIBRATION_SAMPLES = 8;
    private static final int CALIBRATION_PERIOD_MILLIS = 250;
    private static final int MIN_CALIBRATION_SPAN = 5;
    private static final int PERIOD_MILLIS = 1000;
    private static final int RETRY_MILLIS = 1000;

    private NanoCarrierRcxLightSensor() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
        RcxLink link = new RcxLink(ArduinoNano.getClock());
        RemoteRcx remote = new RemoteRcx(link, CHANNEL, receiver, emitter, ArduinoNano.getClock());
        RcxLightSensor light = new RcxLightSensor(remote.getSensor1());

        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        Delay.millis(START_MILLIS);
        while (!light.begin()) {
            Serial.println("No RCX response; check channel 1 and IR alignment");
            Delay.millis(RETRY_MILLIS);
        }
        Serial.println("RCX light sensor: input 1, channel 1");

        Serial.println("Cover the sensor for calibration");
        Delay.millis(CALIBRATION_WAIT_MILLIS);
        int covered = sampleAverage(light);
        printReference("covered", covered);

        Serial.println("Expose the sensor to the light for calibration");
        Delay.millis(CALIBRATION_WAIT_MILLIS);
        int exposed = sampleAverage(light);
        printReference("exposed", exposed);
        Serial.println(calibrationReady(covered, exposed) ? "Calibration ready" : "Calibration span too small");

        while (true) {
            printReading(light, covered, exposed);
            Delay.millis(PERIOD_MILLIS);
        }
    }

    private static int sampleAverage(RcxLightSensor light) {
        int sum = 0;
        for (int sample = 0; sample < CALIBRATION_SAMPLES; sample++) {
            int value = light.lightPercent();
            if (value < 0) {
                return -1;
            }
            sum += value;
            Delay.millis(CALIBRATION_PERIOD_MILLIS);
        }
        return sum / CALIBRATION_SAMPLES;
    }

    private static void printReference(String name, int value) {
        Serial.print(name);
        Serial.print(" reference: ");
        if (value < 0) {
            Serial.println("no answer");
        } else {
            Serial.print(value);
            Serial.println(" %");
        }
    }

    private static void printReading(RcxLightSensor light, int covered, int exposed) {
        int raw = light.lightPercent();
        if (raw < 0) {
            Serial.println("light: no answer");
            return;
        }
        Serial.print("light raw ");
        Serial.print(raw);
        if (calibrationReady(covered, exposed)) {
            Serial.print(" %, calibrated ");
            Serial.print(calibratedPercent(raw, covered, exposed));
            Serial.println(" %");
        } else {
            Serial.println(" % (calibration unavailable)");
        }
    }

    private static boolean calibrationReady(int covered, int exposed) {
        int span = covered - exposed;
        if (span < 0) {
            span = -span;
        }
        return covered >= 0 && exposed >= 0 && span >= MIN_CALIBRATION_SPAN;
    }

    private static int calibratedPercent(int raw, int covered, int exposed) {
        int low = covered < exposed ? covered : exposed;
        int high = covered < exposed ? exposed : covered;
        int percent = (raw - low) * 100 / (high - low);
        return percent < 0 ? 0 : percent > 100 ? 100 : percent;
    }
}
