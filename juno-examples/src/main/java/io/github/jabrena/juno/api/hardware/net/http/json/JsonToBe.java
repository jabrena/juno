package io.github.jabrena.juno.api.hardware.net.http.json;

import io.github.jabrena.juno.hardware.BoardMemory;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.net.http.JsonView;
import io.github.jabrena.juno.hardware.net.http.JsonWriter;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/**
 * SPIKE (#285), the TO BE half of {@link JsonAsIs}: the same four examples with {@link JsonView} (JEP 540 method
 * names, no path strings) and {@link JsonWriter} (a body written into a buffer allocated once). It prints the same
 * checksums as {@link JsonAsIs}.
 */
public final class JsonToBe {
    private static final BoardMemory MEMORY = MCU.getBoardMemory();
    private static final Clock CLOCK = MCU.getClock();
    private static final int ROUNDS = 200;

    private final byte[] text = new byte[16];
    private final byte[] body = new byte[48];
    private final JsonWriter writer = new JsonWriter(body);
    private int checksum;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        new JsonToBe().run();
    }

    private void run() {
        while (true) {
            Serial.println("JsonToBe");
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
        JsonView root = Json.view(Documents.WEATHER);
        JsonView current = root.get("current");
        if (!current.get("temperature_2m").isNumber()) {
            return;
        }
        add((int) Math.round(current.get("temperature_2m").asDouble()));
        add(current.get("relative_humidity_2m").asInt());
        add((int) Math.round(current.get("wind_speed_10m").asDouble()));
        add(current.get("weather_code").asInt());
        add(current.get("is_day").asInt() == 1 ? 1 : 0);
        add(root.get("utc_offset_seconds").asInt());
    }

    private void puzzle() {
        JsonView puzzle = Json.view(Documents.PUZZLE).get("puzzle");
        addText(puzzle.get("fen").copyString(text));
        add(puzzle.get("rating").asInt());
        JsonView solution = puzzle.get("solution");
        int count = solution.size();
        for (int i = 0; i < count; i++) {
            addText(solution.get(i).copyString(text));
        }
    }

    private void uuids() {
        JsonView list = Json.view(Documents.UUIDS);
        int count = list.size();
        for (int index = 0; index < count; index++) {
            addText(list.get(index).get("uuid").copyString(text));
        }
    }

    private void status(int uptime) {
        writer.reset().beginObject().name("uptime_ms").value(uptime).endObject();
        for (int i = 0; i < writer.length(); i++) {
            add(body[i]);
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
