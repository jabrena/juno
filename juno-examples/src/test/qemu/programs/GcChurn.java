package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.Serial;

/** allocates far more than the 8 KiB arena holds while keeping a few objects live, forcing collections. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class GcChurn {
    record Node(int value, Node next) {
    }

    static Node prepend(Node head, int value) {
        return new Node(value, head);
    }

    static int length(Node head) {
        int count = 0;
        for (Node node = head; node != null; node = node.next()) {
            count++;
        }
        return count;
    }

    static int churn(int rounds) {
        int checksum = 0;
        for (int round = 0; round < rounds; round++) {
            Node list = null;
            for (int i = 0; i < 20; i++) {
                list = prepend(list, round + i);
            }
            checksum += length(list) + list.value();
        }
        return checksum;
    }

    public static void main(String[] args) {
        Node keep = null;
        for (int i = 0; i < 10; i++) {
            keep = prepend(keep, i * i);
        }
        Serial.println(churn(500));
        int sum = 0;
        for (Node node = keep; node != null; node = node.next()) {
            sum += node.value();
        }
        Serial.println(sum);
        Serial.println(length(keep));
    }
}
