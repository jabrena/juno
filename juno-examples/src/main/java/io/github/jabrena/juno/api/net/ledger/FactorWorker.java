package io.github.jabrena.juno.api.net.ledger;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Udp;

/**
 * Worker of the factorization network led by {@link FactorLedgerLeader}. Meant for an UNO R4 WiFi;
 * it builds for the UNO Q as well.
 *
 * <p>Until a leader answers, it broadcasts {@code JOIN} once a second. Each {@code TASK} is
 * factored and answered with a {@code RESULT}, which is resent once a second until the leader's next
 * {@code TASK} arrives. That next task is the acknowledgement, so a lost datagram in either direction
 * only costs a resend. If the leader asks again for a task already answered, the stored answer is
 * resent instead of factoring the number again.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class FactorWorker {
    private static final int RESEND_MILLIS = 1000;

    private FactorWorker() {
    }

    public static void main(String[] args) {
        byte[] incoming = new byte[FactorProtocol.PACKET_SIZE];
        byte[] outgoing = new byte[FactorProtocol.PACKET_SIZE];
        int[] source = new int[Udp.ENDPOINT_SIZE];
        int[] leader = new int[Udp.ENDPOINT_SIZE];
        long[] primes = new long[FactorProtocol.MAX_FACTORS];
        int[] exponents = new int[FactorProtocol.MAX_FACTORS];
        long[] stack = new long[Factorizer.STACK_SIZE];

        Serial.begin(BaudRate.BAUD_115200);
        Serial.println("Factor worker");
        LedgerIo.connect();
        int nodeId = Clock.micros();
        if (nodeId == 0) {
            nodeId = 1;
        }
        Serial.println("Node " + nodeId + " looking for a leader");

        boolean answered = false;
        int answeredTask = 0;
        FactorProtocol.write(outgoing, FactorProtocol.JOIN, nodeId, 0, 0);
        int lastSent = Clock.millis() - RESEND_MILLIS;
        while (true) {
            int now = Clock.millis();
            if (now - lastSent >= RESEND_MILLIS) {
                if (answered) {
                    Udp.send(leader, leader[Udp.PORT], outgoing, FactorProtocol.PACKET_SIZE);
                } else {
                    Udp.broadcast(LedgerIo.PORT, outgoing, FactorProtocol.PACKET_SIZE);
                }
                lastSent = now;
            }

            int length = Udp.receive(incoming, FactorProtocol.PACKET_SIZE, source);
            if (!FactorProtocol.valid(incoming, length) || FactorProtocol.type(incoming) != FactorProtocol.TASK) {
                Delay.millis(10);
                continue;
            }
            for (int i = 0; i < Udp.ENDPOINT_SIZE; i++) {
                leader[i] = source[i];
            }
            int task = FactorProtocol.taskId(incoming);
            long number = FactorProtocol.number(incoming);
            if (!answered || task != answeredTask) {
                Serial.print("TASK " + task + ": ");
                Serial.println(number);
                int started = Clock.millis();
                int count = Factorizer.factorize(number, primes, exponents, stack);
                if (count < 0) {
                    Serial.println("  out of range, skipped");
                    answered = false;
                    FactorProtocol.write(outgoing, FactorProtocol.JOIN, nodeId, 0, 0);
                    continue;
                }
                FactorProtocol.write(outgoing, FactorProtocol.RESULT, nodeId, task, number);
                FactorProtocol.writeFactors(outgoing, primes, exponents, count);
                Serial.print("  = ");
                for (int i = 0; i < count; i++) {
                    LedgerIo.printFactor(primes[i], exponents[i], i == 0);
                }
                Serial.println(" (" + (Clock.millis() - started) + " ms)");
                answered = true;
                answeredTask = task;
            }
            Udp.send(leader, leader[Udp.PORT], outgoing, FactorProtocol.PACKET_SIZE);
            lastSent = Clock.millis();
        }
    }
}
