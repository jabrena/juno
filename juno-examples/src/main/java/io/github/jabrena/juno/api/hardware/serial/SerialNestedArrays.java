package io.github.jabrena.juno.api.hardware.serial;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.Watchdog;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Fills a three- and a four-dimensional array on the Arduino Nano ESP32 and prints their checksums over the native
 * USB serial port at 115200 baud, once every second, so the output can be compared line by line with a desktop JVM
 * run of the same source. Both arrays are allocated once at startup; every round rewrites each element from the round
 * counter and its indexes, then sums them back, so a wrong stride in any dimension changes the checksum.
 *
 * <p>The class also carries {@code @Watchdog(timeoutMillis = 3000)}, which the task watchdog rounds up to three
 * seconds. Round {@value #PANIC_ROUND} reads one element past the end of the innermost array on purpose: the
 * uncaught bounds failure halts the program in {@code juno_panic()}, which never refreshes the watchdog, so the board
 * resets itself and the output starts over from {@code round 0}. A desktop JVM run ends with the exception instead.
 *
 * <p>The board's USB port re-enumerates when the program starts, so it waits two seconds before the first line.
 */
@Board(ArduinoNanoEsp32Board.class)
@Watchdog(timeoutMillis = 3000)
@SuppressWarnings("java:S2189")
public final class SerialNestedArrays {
    private static final int PANIC_ROUND = 5;

    private SerialNestedArrays() {
    }

    static void fill(int[][][] cube, int round) {
        for (int i = 0; i < cube.length; i++) {
            for (int j = 0; j < cube[i].length; j++) {
                for (int k = 0; k < cube[i][j].length; k++) {
                    cube[i][j][k] = round * 1000 + i * 100 + j * 10 + k;
                }
            }
        }
    }

    static int weightedSum(int[][][] cube) {
        int sum = 0;
        for (int i = 0; i < cube.length; i++) {
            for (int j = 0; j < cube[i].length; j++) {
                for (int k = 0; k < cube[i][j].length; k++) {
                    sum = sum + cube[i][j][k] * (i + 2 * j + 3 * k + 1);
                }
            }
        }
        return sum;
    }

    static void fill(byte[][][][] tesseract, int round) {
        for (int a = 0; a < tesseract.length; a++) {
            for (int b = 0; b < tesseract[a].length; b++) {
                for (int c = 0; c < tesseract[a][b].length; c++) {
                    for (int d = 0; d < tesseract[a][b][c].length; d++) {
                        tesseract[a][b][c][d] = (byte) (round + a * 27 + b * 9 + c * 3 + d);
                    }
                }
            }
        }
    }

    static int sum(byte[][][][] tesseract) {
        int sum = 0;
        for (int a = 0; a < tesseract.length; a++) {
            for (int b = 0; b < tesseract[a].length; b++) {
                for (int c = 0; c < tesseract[a][b].length; c++) {
                    for (int d = 0; d < tesseract[a][b][c].length; d++) {
                        sum = sum + tesseract[a][b][c][d] * (d + 1);
                    }
                }
            }
        }
        return sum;
    }

    /**
     * Opens the serial port and prints the checksums once per second until the deliberate out-of-bounds read.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        int[][][] cube = new int[2][3][4];
        byte[][][][] tesseract = new byte[2][2][3][3];

        int round = 0;
        while (true) {
            fill(cube, round);
            fill(tesseract, round);
            Serial.print("round ");
            Serial.println(round);
            Serial.print("cube ");
            Serial.println(weightedSum(cube));
            Serial.print("corner ");
            Serial.println(cube[1][2][3]);
            Serial.print("tesseract ");
            Serial.println(sum(tesseract));
            if (round == PANIC_ROUND) {
                Serial.println("reading past the end");
                Serial.println(cube[1][2][round - 1]);
            }

            Delay.millis(1000);
            round = round + 1;
        }
    }
}
