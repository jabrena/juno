package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.net.wifi.WifiCredentials;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.udp.Syslog;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.rcx.RcxAudio;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxMotor;
import io.github.jabrena.juno.remote.lego.rcx.RcxRotationSensor;
import io.github.jabrena.juno.remote.lego.rcx.RcxTouchSensor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Drives outputs A and C of an RCX from a Nano ESP32 with the brick's direct motor commands, without the regulator, and
 * logs over Wi-Fi as UDP syslog what each wheel's rotation sensor counts meanwhile: A alone, C alone, both forward and
 * both backward, each at full power for a moment with a stop in between. It tells a motor or wiring fault from a
 * regulator one: a motor that turns here but not under regulation points at the regulator.
 *
 * <p>Every move logs a line about every 100 milliseconds, e.g. {@code A forward t=400 input1=6 input3=0}: the elapsed
 * milliseconds and each rotation sensor's count in steps (16 per turn) since the move began. Receive the lines with
 * {@code ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.syslog=5514}. The run starts when the touch sensor on input 2
 * is pressed and released, after a "3, 2, 1, go!" countdown on the brick's speaker.
 *
 * <p>Wiring: the Nano Connector Carrier with the Grove infrared emitter in its A0/A1 socket and the Grove infrared
 * receiver in its I2C socket (SCL/A5), both pointed at the RCX's IR window, and the carrier's VCC switch on 3.3 V. The
 * motors are on outputs A and C, their rotation sensors on inputs 1 and 3, and a touch sensor on input 2. The Wi-Fi
 * network comes from {@code application.properties} on the carrier's microSD card ({@code wifi.ssid} and
 * {@code wifi.password}, see {@link WifiCredentials}).
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoCarrierRcxDriveACSyslog {
    private static final int SPEED = RcxMotor.MAX_LEVEL;
    private static final int MOVE_MILLIS = 1500;
    private static final int STOP_MILLIS = 700;
    private static final int SAMPLE_MILLIS = 100;
    private static final int START_MILLIS = 2000;
    private static final int RETRY_MILLIS = 1000;
    private static final int IDLE_SECONDS = 5;

    private static final int COUNT_HZ = 440;
    private static final int GO_HZ = 880;
    private static final int COUNT_CENTISECONDS = 20;
    private static final int GO_CENTISECONDS = 60;
    private static final int COUNT_PERIOD_MILLIS = 1000;
    private static final int COUNT_FROM = 3;
    private static final int TOUCH_POLL_MILLIS = 50;
    private static final int TOUCH_REMINDER_MILLIS = 5000;

    private static final int WIFI_ATTEMPTS = 20;
    private static final int WIFI_RETRY_MILLIS = 1000;
    private static final String HOST_NAME = "nano-rcx";
    private static final String APP_NAME = "drive-ac";
    private static final int SYSLOG_SOURCE_PORT = 5515;
    private static final int SYSLOG_BUFFER_BYTES = 128;

    private final Syslog syslog;
    private final Clock clock;
    private final RcxMotor motorA;
    private final RcxMotor motorC;
    private final RcxMotor both;
    private final RcxRotationSensor input1;
    private final RcxRotationSensor input3;

    private NanoCarrierRcxDriveACSyslog(Syslog syslog, Clock clock, RemoteRcx remote, RcxRotationSensor input1,
            RcxRotationSensor input3) {
        this.syslog = syslog;
        this.clock = clock;
        this.motorA = remote.getMotorA();
        this.motorC = remote.getMotorC();
        this.both = motorA.and(motorC);
        this.input1 = input1;
        this.input3 = input3;
    }

    public static void main(String[] args) throws IOException {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(ArduinoNano.getGpio());
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
        Clock clock = ArduinoNano.getClock();
        RcxLink link = new RcxLink(emitter, receiver, clock);
        RemoteRcx remote = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);

        while (!connectWifi(ArduinoNano.getWifi(), carrier.getSdCard())) {
            Serial.println("No Wi-Fi: not driving, since the run could not be followed");
            Delay.seconds(IDLE_SECONDS);
        }
        InetAddress everyone = InetAddress.getByAddress(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255});
        Syslog syslog = new Syslog(new DatagramSocket(SYSLOG_SOURCE_PORT), everyone, Syslog.DEFAULT_PORT, HOST_NAME,
                APP_NAME, new byte[SYSLOG_BUFFER_BYTES]);
        report(syslog, Syslog.NOTICE, "Ready");

        int millivolts = remote.getBattery().millivolts();
        while (millivolts < 0) {
            report(syslog, Syslog.WARNING, "The RCX did not answer: is it on and facing the IR modules?");
            Delay.millis(RETRY_MILLIS);
            millivolts = remote.getBattery().millivolts();
        }
        syslog.begin(Syslog.NOTICE).text("RCX battery ").number(millivolts).text(" mV").send();

        RcxTouchSensor startButton = touchSensor(remote, syslog);
        RcxRotationSensor input1 = rotationSensor(remote, syslog, true);
        RcxRotationSensor input3 = rotationSensor(remote, syslog, false);
        NanoCarrierRcxDriveACSyslog drive = new NanoCarrierRcxDriveACSyslog(syslog, clock, remote, input1, input3);
        drive.both.stop();

        waitForClick(startButton, syslog);
        countdown(remote.getAudio(), syslog);

        drive.move("A forward", SPEED, 0);
        drive.move("C forward", 0, SPEED);
        drive.move("A+C forward", SPEED, SPEED);
        drive.move("A+C backward", -SPEED, -SPEED);
        report(syslog, Syslog.NOTICE, "Done; the program now idles");
        while (true) {
            Serial.println("Run finished");
            Delay.seconds(IDLE_SECONDS);
        }
    }

    /**
     * Runs A and C at the given levels ({@code -7..7}, 0 leaves a motor stopped) for a moment, logging both rotation
     * sensors about every {@link #SAMPLE_MILLIS}, then brakes both and logs where they stopped.
     */
    private void move(String name, int speedA, int speedC) {
        input1.reset();
        input3.reset();
        syslog.begin(Syslog.NOTICE).text(name).text(": A=").number(speedA).text(" C=").number(speedC).send();
        if (speedA == speedC) {
            both.drive(speedA);
        } else {
            motorA.drive(speedA);
            motorC.drive(speedC);
        }
        int started = clock.millis();
        int elapsed = 0;
        while (elapsed < MOVE_MILLIS) {
            sample(name, elapsed);
            Delay.millis(SAMPLE_MILLIS);
            elapsed = clock.millis() - started;
        }
        both.drive(0);
        Delay.millis(STOP_MILLIS);
        sample(name, clock.millis() - started);
    }

    private void sample(String name, int elapsed) {
        int steps1 = input1.steps();
        int steps3 = input3.steps();
        syslog.begin(Syslog.DEBUG).text(name).text(" t=").number(elapsed).text(" input1=").number(steps1)
                .text(" input3=").number(steps3).send();
    }

    /** Configures the rotation sensor on input 1 ({@code first}) or 3, retrying until the brick acknowledges. */
    private static RcxRotationSensor rotationSensor(RemoteRcx remote, Syslog syslog, boolean first) {
        while (true) {
            try {
                return new RcxRotationSensor(first ? remote.getSensor1() : remote.getSensor3());
            } catch (IllegalStateException unacknowledged) {
                report(syslog, Syslog.WARNING, first ? "No answer configuring input 1" : "No answer configuring input 3");
                Delay.millis(RETRY_MILLIS);
            }
        }
    }

    /** Configures the touch sensor on input 2, retrying until the brick acknowledges. */
    private static RcxTouchSensor touchSensor(RemoteRcx remote, Syslog syslog) {
        while (true) {
            try {
                return new RcxTouchSensor(remote.getSensor2());
            } catch (IllegalStateException unacknowledged) {
                report(syslog, Syslog.WARNING, "No answer configuring input 2");
                Delay.millis(RETRY_MILLIS);
            }
        }
    }

    /** Waits for the start button to be pressed and then released, reminding every few seconds while it waits. */
    private static void waitForClick(RcxTouchSensor startButton, Syslog syslog) {
        report(syslog, Syslog.NOTICE, "Press and release the touch sensor on input 2 to start");
        int waited = 0;
        while (!startButton.isPressed()) {
            Delay.millis(TOUCH_POLL_MILLIS);
            waited += TOUCH_POLL_MILLIS;
            if (waited >= TOUCH_REMINDER_MILLIS) {
                report(syslog, Syslog.INFO, "Waiting for the touch sensor on input 2");
                waited = 0;
            }
        }
        syslog.info("Touch sensor pressed; release it to start");
        while (startButton.isPressed()) {
            Delay.millis(TOUCH_POLL_MILLIS);
        }
        report(syslog, Syslog.NOTICE, "Starting");
    }

    /** Beeps "3, 2, 1" a second apart on the brick, then a longer, higher "go"; each step is also reported. */
    private static void countdown(RcxAudio audio, Syslog syslog) {
        for (int count = COUNT_FROM; count > 0; count--) {
            Serial.println(count);
            syslog.begin(Syslog.INFO).text("countdown ").number(count).send();
            audio.playTone(COUNT_HZ, COUNT_CENTISECONDS);
            Delay.millis(COUNT_PERIOD_MILLIS);
        }
        report(syslog, Syslog.INFO, "Go!");
        audio.playTone(GO_HZ, GO_CENTISECONDS);
        Delay.millis(GO_CENTISECONDS * 10);
    }

    /** Joins the network named on the microSD card; reports the address on Serial once connected. */
    private static boolean connectWifi(Wifi wifi, SdCard sdCard) {
        // Opening a file needs an initialized card: SdCard.open does not initialize it.
        if (!sdCard.begin()) {
            Serial.println("microSD card not detected: is a card in the carrier's slot?");
            return false;
        }
        WifiCredentials credentials = WifiCredentials.load(sdCard);
        if (credentials == null) {
            return false;
        }
        wifi.begin(credentials.ssid(), credentials.password());
        int attempts = 0;
        while (wifi.status() != Wifi.STATUS_CONNECTED && attempts < WIFI_ATTEMPTS) {
            Delay.millis(WIFI_RETRY_MILLIS);
            attempts++;
        }
        return wifi.status() == Wifi.STATUS_CONNECTED;
    }

    /** Prints {@code message} on Serial and sends it at {@code severity}. */
    private static void report(Syslog syslog, int severity, String message) {
        Serial.println(message);
        syslog.log(severity, message);
    }
}
