package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLightSensor;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxSensor;
import io.github.jabrena.juno.remote.lego.rcx.RcxTemperatureSensor;
import io.github.jabrena.juno.remote.lego.rcx.RcxTouchSensor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/**
 * Reads the three RCX inputs from the Arduino over infrared twice a second and prints them: a temperature sensor on
 * input 1 in degrees Celsius, a touch sensor on input 2 (1 while pressed) and a light sensor on input 3 in percent.
 * The brick converts each reading, so it works with LEGO's firmware or Juno's own.
 *
 * <p>Wiring: the IR transmitter module's DAT to pin 3 and the IR receiver module's OUT to pin 2, both with
 * VCC on 5V and GND, pointed at the RCX's IR window. Pick the board with {@code -Djuno.board}.
 */
public class RcxSensors {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int PERIOD_MILLIS = 500;
    private static final int START_MILLIS = 2000;

    public static void main(String[] args) {
        RcxLink link = new RcxLink(new HX53InfraredEmitter(TRANSMIT_PIN), new HXM121InfraredReceiver(RECEIVE_PIN), ArduinoUno.getClock());
        RemoteRcx remote = new RemoteRcx(link, RcxChannel.BROADCAST);
        RcxTemperatureSensor temperature = new RcxTemperatureSensor(remote.getSensor1());
        RcxSensor touch = remote.getSensor2();
        RcxTouchSensor touchSensor = new RcxTouchSensor(touch);
        RcxLightSensor light = new RcxLightSensor(remote.getSensor3());
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        Serial.println("Sensors set: temperature 1, touch 2, light 3");

        while (true) {
            printTemperature(temperature.temperatureTenths());
            Serial.print("  touch ");
            Serial.print(touch.readValue());
            Serial.print("  light ");
            Serial.print(light.lightPercent());
            Serial.println(" %");
            Delay.millis(PERIOD_MILLIS);
        }
    }

    /** Prints tenths of a degree as degrees with one decimal, in pieces, so no string is allocated. */
    private static void printTemperature(int tenths) {
        Serial.print("temperature ");
        if (tenths < 0) {
            Serial.print("-");
            tenths = -tenths;
        }
        Serial.print(tenths / 10);
        Serial.print(".");
        Serial.print(tenths % 10);
        Serial.print(" C");
    }
}
