package io.github.jabrena.juno;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.DigitalOutput;
import io.github.jabrena.juno.api.io.Gpio;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/**
 * {@code java.lang.Thread} on the board: a daemon thread blinks the built-in LED while {@code main} starts a worker,
 * waits for it with {@code join()}, and then counts seconds.
 *
 * <p>Threads are cooperative: they switch where the program already pauses (loop iterations, {@code Delay},
 * {@code Thread.sleep}, {@code Thread.yield}, {@code join}), so no thread is ever interrupted in the middle of a
 * statement. At most four threads, the main thread included, can run at once.
 *
 * <p>Expected serial output at 115200 baud, with the LED blinking throughout:
 * <pre>
 * sum 5050
 * tick 1
 * tick 2
 * ...
 * </pre>
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class Threads {

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);

        Threads example = new Threads();
        example.startBlinker();
        example.sumInWorker();
        example.countSeconds();
    }

    /** A daemon thread blinks the built-in LED for as long as the program runs. */
    private void startBlinker() {
        DigitalOutput led = DigitalOutput.of(Gpio.builtinLed());
        Thread blinker = new Thread(() -> {
            while (true) {
                led.high();
                Delay.millis(250);
                led.low();
                Delay.millis(250);
            }
        });
        blinker.setDaemon(true);
        blinker.start();
    }

    /** Starts a worker thread and waits for its result with {@code join()}. */
    private void sumInWorker() throws InterruptedException {
        Summer summer = new Summer(100);
        Thread worker = new Thread(summer);
        worker.start();
        worker.join();
        Serial.print("sum ");
        Serial.println(summer.sum());
    }

    /** Adds 1..limit, yielding on every step so the blinker keeps running. */
    static final class Summer implements Runnable {
        private final int limit;
        private int sum;

        Summer(int limit) {
            this.limit = limit;
        }

        @Override
        public void run() {
            for (int i = 1; i <= limit; i++) {
                sum += i;
                Thread.yield();
            }
        }

        int sum() {
            return sum;
        }
    }

    private void countSeconds() throws InterruptedException {
        int seconds = 0;
        while (true) {
            Thread.sleep(1000);
            seconds++;
            Serial.print("tick ");
            Serial.println(seconds);
        }
    }
}
