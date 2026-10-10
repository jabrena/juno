package demo;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.io.Gpio;
import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.io.serial.Serial;
import java.io.IOException;
import java.io.InputStream;

/**
 * SD card use cases (SPI chip select on D10): a state file that is rewritten and read back, a log file that
 * only grows, and a CSV file of samples.
 */
public final class SdCardApi {
    // Constants are inlined as literals, which is what SdCard's compile-time path rule needs.
    private static final String STATE_FILE = "STATE.TXT";
    private static final String LOG_FILE = "LOG.TXT";
    private static final int MAX_LOG_BYTES = 4096;
    private static final String CSV_FILE = "DATA.CSV";
    private static final int SAMPLES = 3;

    public static void main(String[] args) throws IOException {
        SdCard.begin(Gpio.D10);

        SdCardApi app = new SdCardApi();
        app.saveAndReadState();
        app.logEvents();
        app.writeCsv();
    }

    /**
     * State: the file holds one record that is replaced on every save. There is no overwrite call, so
     * remove() it first and rebuild it with appendText(), which adds raw text and no newline.
     */
    private void saveAndReadState() throws IOException {
        SdCard.remove(STATE_FILE);
        SdCard.appendText(STATE_FILE, "level=3\n");
        // One record written piece by piece: each runtime string stays under 32 bytes, whatever the data type.
        int score = 42;
        long uptime = Clock.millis() * 1000L;
        float ratio = 0.75f;
        double temperature = 21.5;
        boolean armed = score > 0;
        char grade = 'A';
        SdCard.appendText(STATE_FILE, "score=" + String.valueOf(score) + ";");
        SdCard.appendText(STATE_FILE, "uptime=" + uptime + ";");
        SdCard.appendText(STATE_FILE, "ratio=" + ratio + ";");
        SdCard.appendText(STATE_FILE, "temp=" + String.valueOf(temperature) + ";");
        SdCard.appendText(STATE_FILE, "armed=" + armed + ";");
        SdCard.appendText(STATE_FILE, "grade=" + grade + "\n");

        Serial.println(SdCard.exists(STATE_FILE));
        try (InputStream state = SdCard.open(STATE_FILE)) {
            while (state.available() > 0) {
                Serial.print(state.read());
            }
        }
        SdCard.remove(STATE_FILE);
    }

    /** Log: one entry per call with appendLine(), which ends each entry with a newline; the file only grows. */
    private void logEvents() {
        SdCard.appendLine(LOG_FILE, "boot");
        SdCard.appendLine(LOG_FILE, "uptime=" + Clock.millis());
        SdCard.appendLine(LOG_FILE, "done");
        // size() is -1 when the file is missing; a log that grew past a limit can be started afresh.
        int logBytes = SdCard.size(LOG_FILE);
        Serial.println(logBytes);
        if (logBytes > MAX_LOG_BYTES) {
            SdCard.remove(LOG_FILE);
        }
    }

    /**
     * CSV: a header written once, then one row per sample, kept across runs so a new run adds rows after the ones
     * already there. Each row is written field by field: appendText() adds a field and its comma as given, and the
     * last field goes in with appendLine(), which ends the row with the newline. Each piece is a runtime string, so
     * it stays under 32 bytes whatever its data type.
     */
    private void writeCsv() {
        if (!SdCard.exists(CSV_FILE)) {
            SdCard.appendLine(CSV_FILE, "sample,millis,raw,volts,armed");
        }
        for (int sample = 0; sample < SAMPLES; sample++) {
            int millis = Clock.millis();
            int raw = Gpio.analogRead(Gpio.A0);
            double volts = raw * 5.0 / 1023.0;
            boolean armed = raw > 512;
            SdCard.appendText(CSV_FILE, String.valueOf(sample) + ",");
            SdCard.appendText(CSV_FILE, millis + ",");
            SdCard.appendText(CSV_FILE, raw + ",");
            SdCard.appendText(CSV_FILE, String.valueOf(volts) + ",");
            SdCard.appendLine(CSV_FILE, "" + armed);
        }
        Serial.println(SdCard.size(CSV_FILE));
    }
}
