package io.github.jabrena.juno.games.ledger;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.games.blockchain.Factorizer;
import io.github.jabrena.juno.games.blockchain.GameScreen;
import io.github.jabrena.juno.games.blockchain.NodeIo;

/**
 * The WORKER role of {@link LedgerGame}: factors numbers for the board playing {@link LeaderFlow}.
 * Meant for UNO R4 WiFi boards.
 *
 * <p>Until a leader answers, it broadcasts {@code JOIN} once a second. Each {@code TASK} is
 * factored and answered with a {@code RESULT}, which is resent once a second until the leader's next
 * {@code TASK} arrives. That next task is the acknowledgement, so a lost datagram in either direction
 * only costs a resend. If the leader asks again for a task already answered, the stored answer is
 * resent instead of factoring the number again.
 */
final class WorkerFlow {
    private static final int RESEND_MILLIS = 1000;

    private WorkerFlow() {
    }

    /** Works for a leader until the MENU button is tapped. */
    static void run(byte[] incoming, byte[] outgoing, int[] source) {
        int[] leader = new int[Udp.ENDPOINT_SIZE];
        long[] primes = new long[FactorProtocol.MAX_FACTORS];
        int[] exponents = new int[FactorProtocol.MAX_FACTORS];
        long[] stack = new long[Factorizer.STACK_SIZE];

        int nodeId = Clock.micros();
        if (nodeId == 0) {
            nodeId = 1;
        }
        Serial.println("Role: worker, node " + nodeId);
        GameScreen.role("WORKER", TftTouchShield.CYAN);
        showStatus("Finding leader", TftTouchShield.YELLOW, 0);

        boolean answered = false;
        int answeredTask = 0;
        int solved = 0;
        FactorProtocol.write(outgoing, FactorProtocol.JOIN, nodeId, 0, 0);
        int lastSent = Clock.millis() - RESEND_MILLIS;
        while (!GameScreen.menuTapped()) {
            int now = Clock.millis();
            if (now - lastSent >= RESEND_MILLIS) {
                if (answered) {
                    Udp.send(leader, leader[Udp.PORT], outgoing, FactorProtocol.PACKET_SIZE);
                } else {
                    Udp.broadcast(NodeIo.PORT, outgoing, FactorProtocol.PACKET_SIZE);
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
                showTask(task, number);
                int started = Clock.millis();
                int count = Factorizer.factorize(number, primes, exponents, stack);
                if (count < 0) {
                    Serial.println("  out of range, skipped");
                    answered = false;
                    FactorProtocol.write(outgoing, FactorProtocol.JOIN, nodeId, 0, 0);
                    continue;
                }
                int elapsed = Clock.millis() - started;
                FactorProtocol.write(outgoing, FactorProtocol.RESULT, nodeId, task, number);
                FactorProtocol.writeFactors(outgoing, primes, exponents, count);
                Serial.print("  = ");
                for (int i = 0; i < count; i++) {
                    NodeIo.printFactor(primes[i], exponents[i], i == 0);
                }
                Serial.println(" (" + elapsed + " ms)");
                answered = true;
                answeredTask = task;
                solved++;
                showStatus("Working", TftTouchShield.GREEN, solved);
                showResult(primes, exponents, count, elapsed);
            }
            Udp.send(leader, leader[Udp.PORT], outgoing, FactorProtocol.PACKET_SIZE);
            lastSent = Clock.millis();
        }
    }

    private static void showStatus(String status, int color, int solved) {
        GameScreen.row(0, 2, color);
        TftTouchShield.print(status);
        GameScreen.row(1, 2, TftTouchShield.WHITE);
        TftTouchShield.print("Solved " + solved);
    }

    private static void showTask(int task, long number) {
        GameScreen.row(3, 1, TftTouchShield.CYAN);
        TftTouchShield.print("Task #" + task);
        GameScreen.row(4, 1, TftTouchShield.WHITE);
        TftTouchShield.print("n = " + number);
        GameScreen.row(5, 1, TftTouchShield.GRAY);
        TftTouchShield.print("factoring...");
        GameScreen.row(6, 1, TftTouchShield.GRAY);
    }

    private static void showResult(long[] primes, int[] exponents, int count, int elapsed) {
        GameScreen.row(5, 1, TftTouchShield.WHITE);
        TftTouchShield.print("= ");
        for (int i = 0; i < count; i++) {
            GameScreen.factor(primes[i], exponents[i], i == 0);
        }
        GameScreen.row(6, 1, TftTouchShield.GRAY);
        TftTouchShield.print("in " + elapsed + " ms");
    }
}
