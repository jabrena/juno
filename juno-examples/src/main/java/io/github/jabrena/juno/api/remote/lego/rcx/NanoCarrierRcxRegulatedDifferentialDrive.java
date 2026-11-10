package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.net.wifi.WifiCredentials;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.device.imu.Bmi088;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.udp.Syslog;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.rcx.RcxAudio;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxRegulatedDifferentialDrive;
import io.github.jabrena.juno.remote.lego.rcx.RcxRegulatedPair;
import io.github.jabrena.juno.remote.lego.rcx.RcxRegulatedState;
import io.github.jabrena.juno.remote.lego.rcx.RcxTouchSensor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.robotics.navigation.DriveGeometry;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Drives a two-wheel RCX vehicle from a Nano ESP32 with its wheels regulated on the brick, and logs the run over Wi-Fi
 * as UDP syslog so it can be followed across the floor without a USB cable. It waits until the touch sensor on input 2
 * is pressed and released, so the run starts only when the robot is on the floor and the hand is clear; then a
 * "3, 2, 1, go!" countdown on the brick's speaker, 500 millimetres straight forward at 150 millimetres per second, then
 * a stop of both wheels, braked early by the distance they coast so they stop on the target. Turning is left for a separate example, once the straight travel is measured. A stall, a timeout or an unknown link outcome is reported and the program stops both wheels
 * rather than continuing.
 *
 * <p>While each move runs, every poll sends one trace line: elapsed milliseconds, the regulator's state and fault bits,
 * and each wheel's tachometer in degrees since the move's start, e.g. {@code travel t=400 state=RUNNING faults=0
 * left=118 right=-121}. A wheel driven forward whose count falls has its rotation sensor reversed (issue #302). Receive
 * the lines on any computer on the same network with
 * {@code ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.syslog=5514}; the messages are broadcast, so the program
 * needs no computer address. Serial at 115200 baud carries the same reports, without the trace.
 *
 * <p>The start button and the countdown use the brick before the regulated pair is created: the pair owns the infrared
 * link from then on and refuses direct commands such as a sensor read or a tone, which would race its regulated
 * commands.
 *
 * <p>Wiring: the Nano Connector Carrier with the Grove infrared emitter in its A0/A1 socket and the Grove infrared
 * receiver in its A2/A3 socket, both pointed at the RCX's IR window, and the Grove BMI088 6-axis IMU in its I2C socket:
 * its gyro measures the heading during the travel and logs it as {@code heading=}, clockwise degrees, without steering. Set the carrier's VCC switch to 3.3 V, or the
 * Grove modules stay unpowered. The left motor is on output A with its rotation sensor on input 1, the right motor on
 * output C with its sensor on input 3, and a touch sensor on input 2 starts the run. The RCX runs no program of its own; it needs only the regulator firmware, installed
 * by {@link NanoCarrierRcxFirmwareDownload}. The Wi-Fi network comes from {@code application.properties} on the
 * carrier's microSD card ({@code wifi.ssid} and {@code wifi.password}, see {@link WifiCredentials}); without Wi-Fi the
 * program does not drive, since the run could not be followed.
 *
 * <p>The geometry below was measured for one build with 1:1 gearing on both sides. Replace the wheel diameter, the track
 * width and the encoder degrees per wheel revolution with the values {@link RcxRegulatedMotorCalibration} measures for
 * your build before you rely on the distances.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoCarrierRcxRegulatedDifferentialDrive {
    static final int SESSION = 1;
    /** Measured: the rotation sensor sits on the wheel axle, so one sensor turn is one wheel turn, 16 steps. */
    static final int STEPS_PER_MOTOR_REVOLUTION = 16;
    /** Of 7: lower than full, so a wheel that briefly falls behind is not driven hard enough to jerk the robot aside. */
    static final int MAX_POWER_LEVEL = 1;
    /**
     * The ports define the roles, wherever the parts sit on the chassis: output A is the left motor with its rotation
     * sensor on input 1, and output C the right motor with its sensor on input 3. Measured on the reference build: the
     * left motor runs backward for forward, so it is inverted, while the right motor runs forward; input 1 counts up with
     * A forward and input 3 counts down with C forward ({@link NanoCarrierRcxDriveACSyslog}), so both sensors are
     * inverted to count up as the robot moves forward. A turn needs no swapped sides ({@link NanoCarrierRcxRegulatedTurn}
     * measured it). The brake stops a wheel at its target instead of letting it coast. Replace with your build's
     * measurement.
     */
    static final int SENSOR_DIRECTIONS = RcxRegulatedPair.INVERT_LEFT_MOTOR | RcxRegulatedPair.INVERT_LEFT_SENSOR
            | RcxRegulatedPair.INVERT_RIGHT_SENSOR | RcxRegulatedPair.BRAKE_ON_STOP;
    /** LEGO part 2998, measured at 81.6 mm and rounded to whole millimetres for the geometry. */
    static final int WHEEL_DIAMETER_MILLIMETRES = 82;
    /** Measured at 16 cm between the driven wheels; an in-place turn checks the effective track width. */
    static final int TRACK_WIDTH_MILLIMETRES = 160;
    /** Measured: the sensor is on each wheel's axle at 1:1, so one wheel turn is 360 encoder degrees on both sides. */
    static final int LEFT_DEGREES_PER_WHEEL_REVOLUTION = 360;
    static final int RIGHT_DEGREES_PER_WHEEL_REVOLUTION = 360;
    static final int LINEAR_SPEED_MILLIMETRES_PER_SECOND = 150;
    private static final int TRAVEL_MILLIMETRES = 500;
    static final int TOLERANCE_STEPS = 1;
    /**
     * Control cycles of coasting the brick brakes early by (see {@link RcxRegulatedPair#setBrakeLead}): raise it while the
     * {@code rest} line shows the wheels past 31 steps (500 mm), lower it while they stop short; 0 brakes on the target.
     */
    static final int BRAKE_LEAD_CYCLES = RcxRegulatedPair.DEFAULT_BRAKE_LEAD;
    static final int TIMEOUT_SECONDS = 10;
    static final int START_MILLIS = 2000;
    static final int RETRY_MILLIS = 1000;
    static final int IDLE_SECONDS = 5;

    /** A4 for each count, A5 for "go". */
    private static final int COUNT_HZ = 440;
    private static final int GO_HZ = 880;
    private static final int COUNT_CENTISECONDS = 20;
    private static final int GO_CENTISECONDS = 60;
    /** One count per second: the tone plus the silence after it. */
    private static final int COUNT_PERIOD_MILLIS = 1000;
    private static final int COUNT_FROM = 3;

    /** How often the start button is read; each read is one infrared round trip. */
    private static final int TOUCH_POLL_MILLIS = 50;
    /** How often the wait for the start button is reported again. */
    private static final int TOUCH_REMINDER_MILLIS = 5000;

    private static final int WIFI_ATTEMPTS = 20;
    private static final int WIFI_RETRY_MILLIS = 1000;
    static final String HOST_NAME = "nano-rcx";
    private static final String APP_NAME = "regulated-drive";
    /** The board's own UDP port, which the datagrams are sent from; the monitor listens on Syslog.DEFAULT_PORT. */
    static final int SYSLOG_SOURCE_PORT = 5515;
    /** A trace line with the drive diagnostic runs past 128 bytes with the frame's header. */
    static final int SYSLOG_BUFFER_BYTES = 192;
    /** Between trace lines; each poll also spends two status round trips on the infrared link. */
    private static final int TRACE_PERIOD_MILLIS = 50;
    /** The gyro is read this often between trace polls, and for this long after the stop before the final heading. */
    private static final int GYRO_SAMPLE_MILLIS = 10;
    static final int SETTLE_MILLIS = 500;
    /** How long the cycle counter is watched; the infrared round trips at either end add a few percent. */
    private static final int CYCLE_WINDOW_MILLIS = 3000;
    /** Past the brick's own timeout, the outcome is reported as unknown rather than waited for. */
    private static final int TRACE_MARGIN_MILLIS = 2000;
    /** Starting a move is retried this often, a pause apart, to tell a timing problem from a refusal. */
    private static final int START_ATTEMPTS = 3;
    private static final int START_RETRY_MILLIS = 1000;

    private NanoCarrierRcxRegulatedDifferentialDrive() {
    }

    public static void main(String[] args) throws IOException {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(ArduinoNano.getGpio());
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        // The I2C socket (A4/A5) holds the BMI088, so the receiver sits in the A2/A3 socket.
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getGroveA2());
        Clock clock = ArduinoNano.getClock();
        RcxLink link = new RcxLink(emitter, receiver, clock);
        RemoteRcx remote = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        RcxAudio audio = remote.getAudio();
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);

        Serial.println("Joining Wi-Fi from the microSD card");
        if (!connectWifi(ArduinoNano.getWifi(), carrier.getSdCard())) {
            while (true) {
                Serial.println("No Wi-Fi: not driving, since the run could not be followed");
                Delay.seconds(IDLE_SECONDS);
            }
        }
        InetAddress everyone = InetAddress.getByAddress(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255});
        Syslog syslog = new Syslog(new DatagramSocket(SYSLOG_SOURCE_PORT), everyone, Syslog.DEFAULT_PORT, HOST_NAME, APP_NAME,
                new byte[SYSLOG_BUFFER_BYTES]);
        report(syslog, Syslog.NOTICE, "Ready");

        RcxTouchSensor startButton = touchSensor(remote, syslog);
        waitForClick(startButton, syslog);
        // Measured, not used for steering: the robot stands still here, so the gyro's bias is calibrated before it moves.
        Bmi088 imu = new Bmi088(ArduinoNano.getI2c(), clock);
        imu.recalibrateOffset();
        report(syslog, Syslog.NOTICE, "Gyro calibrated");
        countdown(audio, syslog);

        DriveGeometry geometry = new DriveGeometry(WHEEL_DIAMETER_MILLIMETRES, TRACK_WIDTH_MILLIMETRES,
                LEFT_DEGREES_PER_WHEEL_REVOLUTION, RIGHT_DEGREES_PER_WHEEL_REVOLUTION);
        RcxRegulatedDifferentialDrive drive = new RcxRegulatedDifferentialDrive(link, SESSION,
                RcxRegulatedPair.maxPower(MAX_POWER_LEVEL) | SENSOR_DIRECTIONS, STEPS_PER_MOTOR_REVOLUTION, geometry);
        drive.setTolerance(TOLERANCE_STEPS);
        drive.setBrakeLead(BRAKE_LEAD_CYCLES);
        drive.setTimeoutSeconds(TIMEOUT_SECONDS);
        drive.setLinearSpeedMillimetresPerSecond(LINEAR_SPEED_MILLIMETRES_PER_SECOND);
        RcxRegulatedPair pair = drive.pair();

        while (!armed(pair, syslog)) {
            report(syslog, Syslog.WARNING, "The regulator did not answer: is the RCX on and running its firmware?");
            Delay.millis(RETRY_MILLIS);
        }
        report(syslog, Syslog.NOTICE, "Armed");
        measureCycleRate(pair, imu, clock, syslog);

        imu.reset();
        // Each wheel's tachometer just before the move: the trace reports progress from here, as the firmware measures
        // it, since a rotation sensor can count a step or two while it settles after arming.
        pair.status();
        int leftStart = pair.cachedLeftTachoCount();
        int rightStart = pair.cachedRightTachoCount();
        syslog.begin(Syslog.DEBUG).text("travel from left=").number(leftStart).text(" right=").number(rightStart).send();
        if (started(drive, pair, syslog)) {
            traced(pair, imu, clock, syslog, "travel", leftStart, rightStart);
        }
        drive.stop();
        followGyro(imu, SETTLE_MILLIS);
        // The brake can end the move before the wheels stop: where they came to rest is what the brake lead is tuned by.
        pair.status();
        syslog.begin(Syslog.NOTICE).text("rest left=").number(pair.cachedLeftTachoCount() - leftStart).text(" right=")
                .number(pair.cachedRightTachoCount() - rightStart).text(" lead=").number(pair.brakeLead()).send();
        syslog.begin(Syslog.NOTICE).text("Final heading ").number(imu.getAngle()).text(" degrees").send();
        report(syslog, Syslog.NOTICE, "Stopped; the program now idles");
        while (true) {
            Serial.print("Run finished; syslog send failures: ");
            Serial.println(syslog.failures());
            Delay.seconds(IDLE_SECONDS);
        }
    }

    /** Joins the network named on the microSD card; reports the address on Serial once connected. */
    static boolean connectWifi(Wifi wifi, SdCard sdCard) {
        // Opening a file needs an initialized card: SdCard.open does not initialize it.
        if (!sdCard.begin()) {
            Serial.println("microSD card not detected: is a card in the carrier's slot?");
            return false;
        }
        WifiCredentials credentials = WifiCredentials.load(sdCard);
        if (credentials == null) {
            Serial.println("No Wi-Fi settings: put wifi.ssid and wifi.password in application.properties");
            return false;
        }
        Serial.println("Wi-Fi settings read; connecting");
        wifi.begin(credentials.ssid(), credentials.password());
        int attempts = 0;
        while (wifi.status() != Wifi.STATUS_CONNECTED && attempts < WIFI_ATTEMPTS) {
            Delay.millis(WIFI_RETRY_MILLIS);
            attempts++;
        }
        if (wifi.status() != Wifi.STATUS_CONNECTED) {
            return false;
        }
        int[] address = new int[4];
        wifi.localIP(address);
        Serial.print("Wi-Fi connected, ip=");
        for (int i = 0; i < address.length; i++) {
            if (i > 0) {
                Serial.print(".");
            }
            Serial.print(address[i]);
        }
        Serial.println("");
        return true;
    }

    /** Configures the touch sensor on input 2, retrying until the brick acknowledges. */
    static RcxTouchSensor touchSensor(RemoteRcx remote, Syslog syslog) {
        while (true) {
            try {
                return new RcxTouchSensor(remote.getSensor2());
            } catch (IllegalStateException unacknowledged) {
                report(syslog, Syslog.WARNING, "No answer configuring input 2: is the RCX on and facing the IR modules?");
                Delay.millis(RETRY_MILLIS);
            }
        }
    }

    /** Waits for the start button to be pressed and then released, reminding every few seconds while it waits. */
    static void waitForClick(RcxTouchSensor startButton, Syslog syslog) {
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
    static void countdown(RcxAudio audio, Syslog syslog) {
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

    /**
     * Sends one trace line per poll while the move runs: elapsed time, state, fault bits and both tachometers. Each
     * tachometer read refreshes the status, so the state comes with them. Gives up past the brick's own timeout plus a
     * margin, as {@link RcxRegulatedPair#waitComplete()} does.
     */
    static void trace(RcxRegulatedPair pair, Bmi088 imu, Clock clock, Syslog syslog, String move, int leftStart,
            int rightStart) {
        int started = clock.millis();
        int limit = TIMEOUT_SECONDS * 1000 + TRACE_MARGIN_MILLIS;
        RcxRegulatedState state = RcxRegulatedState.RUNNING;
        while (state == RcxRegulatedState.RUNNING || state == RcxRegulatedState.LINK_UNKNOWN) {
            // One status read for both wheels: reading them one after the other, a round trip apart, made the wheel
            // read second look a few steps ahead.
            state = pair.status();
            int left = pair.cachedLeftTachoCount() - leftStart;
            int right = pair.cachedRightTachoCount() - rightStart;
            int elapsed = clock.millis() - started;
            syslog.begin(Syslog.DEBUG).text(move).text(" t=").number(elapsed).text(" state=").text(name(state))
                    .text(" faults=").number(pair.faults()).text(" left=").number(left).text(" right=").number(right)
                    .text(" heading=").number(imu.getAngle());
            // Each side's power this cycle (0 while braked) and the cycles it was held for the other to catch up.
            if (pair.diagnostics().refreshDrive()) {
                syslog.text(" power=").number(pair.diagnostics().leftPower()).text("/").number(pair.diagnostics().rightPower())
                        .text(" held=").number(pair.diagnostics().leftHeldCycles()).text("/").number(pair.diagnostics().rightHeldCycles());
            }
            syslog.send();
            if (elapsed > limit) {
                return;
            }
            followGyro(imu, TRACE_PERIOD_MILLIS);
        }
    }

    /**
     * Starts the travel without waiting, and reports whether it started. A refusal is
     * sent with the exception's message and the brick's last acknowledgement code, since an uncaught exception would
     * stop the program with its report on USB serial only, out of reach on the floor.
     */
    private static boolean started(RcxRegulatedDifferentialDrive drive, RcxRegulatedPair pair, Syslog syslog) {
        String move = "travel";
        for (int attempt = 1; attempt <= START_ATTEMPTS; attempt++) {
            syslog.begin(Syslog.DEBUG).text(move).text(" attempt ").number(attempt).text(" speed=")
                    .number(pair.speed()).text(" tolerance=").number(TOLERANCE_STEPS).text(" timeout=")
                    .number(TIMEOUT_SECONDS).send();
            try {
                drive.travelMillimetres(TRAVEL_MILLIMETRES, true);
                syslog.begin(Syslog.NOTICE).text(move).text(": started on attempt ").number(attempt).send();
                return true;
            } catch (IllegalStateException refused) {
                reportRefusal(syslog, pair, move, refused.getMessage());
            } catch (IllegalArgumentException refused) {
                reportRefusal(syslog, pair, move, refused.getMessage());
                return false;
            }
            // Is the brick still answering after the refusal? A status reply rules out a hung or reset brick.
            RcxRegulatedState after = pair.status();
            syslog.begin(Syslog.DEBUG).text("status after refusal: ").text(name(after)).text(" faults=")
                    .number(pair.faults()).send();
            Delay.millis(START_RETRY_MILLIS);
        }
        return false;
    }

    static void reportRefusal(Syslog syslog, RcxRegulatedPair pair, String move, String reason) {
        String message = reason != null ? reason : "no message";
        Serial.print(move);
        Serial.print(" did not start: ");
        Serial.println(message);
        syslog.begin(Syslog.ERROR).text(move).text(" did not start: ").text(message).text(" ack=")
                .number(pair.lastAcknowledgement()).text(" state=").text(name(pair.cachedState())).send();
    }

    /**
     * Waits {@code millis} while sampling the gyro every few milliseconds, since the heading is the rate integrated
     * between reads: the infrared round trips of a trace poll leave longer gaps, so it is sampled densely in between.
     */
    static void followGyro(Bmi088 imu, int millis) {
        for (int waited = 0; waited < millis; waited += GYRO_SAMPLE_MILLIS) {
            imu.getAngle();
            Delay.millis(GYRO_SAMPLE_MILLIS);
        }
    }

    /**
     * Logs the brick's control cycles per second: its cycle counter read twice {@link #CYCLE_WINDOW_MILLIS} apart, while
     * the robot stands still. The firmware converts profile speeds to per-cycle steps with this rate.
     */
    static void measureCycleRate(RcxRegulatedPair pair, Bmi088 imu, Clock clock, Syslog syslog) {
        int first = pair.diagnostics().controlCycles();
        int started = clock.millis();
        followGyro(imu, CYCLE_WINDOW_MILLIS);
        int second = pair.diagnostics().controlCycles();
        int elapsed = clock.millis() - started;
        if (first < 0 || second < 0) {
            report(syslog, Syslog.WARNING, "Control cycles: no answer from the brick");
            return;
        }
        int cycles = (second - first) & 0xFFFF;
        syslog.begin(Syslog.NOTICE).text("Control cycles: ").number(cycles).text(" in ").number(elapsed)
                .text(" ms, ").number(cycles * 1000 / elapsed).text(" per second").send();
    }

    /** Traces the running move, then reports how it ended; returns whether both wheels completed. */
    private static boolean traced(RcxRegulatedPair pair, Bmi088 imu, Clock clock, Syslog syslog, String move,
            int leftStart, int rightStart) {
        trace(pair, imu, clock, syslog, move, leftStart, rightStart);
        return finishedCleanly(pair, syslog, move);
    }

    /** Arms the session and reports whether the brick accepted it. */
    static boolean armed(RcxRegulatedPair pair, Syslog syslog) {
        try {
            pair.arm();
            return true;
        } catch (IllegalStateException refused) {
            // A brick with older firmware never answers the brake lead: reinstall it with NanoCarrierRcxFirmwareDownload.
            syslog.begin(Syslog.WARNING).text("arm refused, ack=").number(pair.lastAcknowledgement()).text(": ")
                    .text(refused.getMessage() != null ? refused.getMessage() : "no message").send();
            return false;
        }
    }

    /**
     * Reports how a move ended. Returns {@code true} only when both wheels completed; a stall, a timeout or an unknown
     * link outcome is reported as such and returns {@code false}, so the caller does not start the next move.
     */
    static boolean finishedCleanly(RcxRegulatedPair pair, Syslog syslog, String move) {
        RcxRegulatedState state = pair.status();
        if (state == RcxRegulatedState.COMPLETE) {
            Serial.print(move);
            Serial.println(": complete");
            syslog.begin(Syslog.NOTICE).text(move).text(": complete").send();
            return true;
        }
        Serial.print(move);
        Serial.print(": ended in state ");
        Serial.println(name(state));
        syslog.begin(Syslog.ERROR).text(move).text(": ended in state ").text(name(state)).text(" faults=")
                .number(pair.faults()).send();
        if (state == RcxRegulatedState.STALLED) {
            report(syslog, Syslog.ERROR, "A wheel stalled: check the wiring and the load");
        } else if (state == RcxRegulatedState.TIMED_OUT) {
            report(syslog, Syslog.ERROR, "The move timed out before both wheels reached their targets");
        } else if (state == RcxRegulatedState.LINK_UNKNOWN) {
            report(syslog, Syslog.ERROR, "The outcome is unknown: the brick did not answer; both wheels are stopped");
        }
        return false;
    }

    /** Prints {@code message} on Serial and sends it at {@code severity}. */
    static void report(Syslog syslog, int severity, String message) {
        Serial.println(message);
        syslog.log(severity, message);
    }

    /** The state's name as a constant, so a trace line is built without allocating. */
    static String name(RcxRegulatedState state) {
        if (state == RcxRegulatedState.NOT_ARMED) {
            return "NOT_ARMED";
        } else if (state == RcxRegulatedState.READY) {
            return "READY";
        } else if (state == RcxRegulatedState.RUNNING) {
            return "RUNNING";
        } else if (state == RcxRegulatedState.COMPLETE) {
            return "COMPLETE";
        } else if (state == RcxRegulatedState.STOPPED) {
            return "STOPPED";
        } else if (state == RcxRegulatedState.STALLED) {
            return "STALLED";
        } else if (state == RcxRegulatedState.TIMED_OUT) {
            return "TIMED_OUT";
        } else if (state == RcxRegulatedState.FAULT) {
            return "FAULT";
        } else if (state == RcxRegulatedState.EXTERNAL_OVERRIDE) {
            return "EXTERNAL_OVERRIDE";
        }
        return "LINK_UNKNOWN";
    }
}
