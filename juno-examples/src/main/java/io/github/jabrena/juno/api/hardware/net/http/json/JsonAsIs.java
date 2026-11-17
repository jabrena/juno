package io.github.jabrena.juno.api.hardware.net.http.json;

import io.github.jabrena.juno.hardware.BoardMemory;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/**
 * SPIKE (#285), the AS IS half of a pair: the four examples of issue #285 written with today's path-based {@link Json}
 * and a {@link StringBuilder} body, over fixed documents so it runs on every board without a network. {@link JsonToBe}
 * does the same work with {@code JsonView} and {@code JsonWriter}; both print the same results, so comparing the two
 * builds' flash and RAM, and the arena bytes each scenario allocates, measures the move from AS IS to TO BE.
 */
public final class JsonAsIs {
    private static final BoardMemory MEMORY = MCU.getBoardMemory();
    private static final Clock CLOCK = MCU.getClock();
    private static final int ROUNDS = 200;

    private final byte[] text = new byte[16];
    private int checksum;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        new JsonAsIs().run();
    }

    private void run() {
        while (true) {
            Serial.println("JsonAsIs");
            measure(1);
            measure(2);
            measure(3);
            measure(4);
            int start = CLOCK.micros();
            for (int round = 0; round < ROUNDS; round++) {
                checksum = 0;
                scenario(1);
                scenario(2);
                scenario(3);
                scenario(4);
            }
            Serial.print("rounds ");
            Serial.print(ROUNDS);
            Serial.print(" checksum ");
            Serial.print(checksum);
            Serial.print(" micros ");
            Serial.println(CLOCK.micros() - start);
            Delay.millis(5000);
        }
    }

    /** Runs one scenario and prints its result checksum and the arena bytes it left allocated. */
    private void measure(int scenario) {
        checksum = 0;
        int before = MEMORY.arenaUsedBytes();
        scenario(scenario);
        int allocated = MEMORY.arenaUsedBytes() - before;
        Serial.print("scenario ");
        Serial.print(scenario);
        Serial.print(" checksum ");
        Serial.print(checksum);
        Serial.print(" arena ");
        Serial.println(allocated);
    }

    private void scenario(int scenario) {
        if (scenario == 1) {
            weather();
        } else if (scenario == 2) {
            puzzle();
        } else if (scenario == 3) {
            uuids();
        } else {
            status(123456789);
        }
    }

    private void weather() {
        String body = Documents.WEATHER;
        if (Json.type(body, "current.temperature_2m") != Json.TYPE_NUMBER) {
            return;
        }
        add((int) Math.round(Json.getDouble(body, "current.temperature_2m")));
        add(Json.getInt(body, "current.relative_humidity_2m"));
        add((int) Math.round(Json.getDouble(body, "current.wind_speed_10m")));
        add(Json.getInt(body, "current.weather_code"));
        add(Json.getInt(body, "current.is_day") == 1 ? 1 : 0);
        add(Json.getInt(body, "utc_offset_seconds"));
    }

    private void puzzle() {
        String response = Documents.PUZZLE;
        addText(Json.getString(response, "puzzle.fen", text, text.length));
        add(Json.getInt(response, "puzzle.rating"));
        int count = Json.arraySize(response, "puzzle.solution");
        for (int i = 0; i < count; i++) {
            addText(Json.getString(response, solutionPath(i), text, text.length));
        }
    }

    private static String solutionPath(int index) {
        return switch (index) {
            case 0 -> "puzzle.solution[0]";
            case 1 -> "puzzle.solution[1]";
            case 2 -> "puzzle.solution[2]";
            case 3 -> "puzzle.solution[3]";
            default -> "puzzle.solution[4]";
        };
    }

    private void uuids() {
        String list = Documents.UUIDS;
        int count = Json.arraySize(list, "");
        for (int index = 0; index < count; index++) {
            addText(Json.getString(list, "[" + index + "].uuid", text, text.length));
        }
    }

    private void status(int uptime) {
        StringBuilder json = new StringBuilder(48);
        json.append("{\"uptime_ms\":");
        json.append(String.valueOf(uptime));
        json.append('}');
        String body = json.toString();
        for (int i = 0; i < body.length(); i++) {
            add(body.charAt(i));
        }
    }

    private void addText(int length) {
        for (int i = 0; i < length; i++) {
            add(text[i]);
        }
    }

    private void add(int value) {
        checksum = checksum * 31 + value;
    }
}
