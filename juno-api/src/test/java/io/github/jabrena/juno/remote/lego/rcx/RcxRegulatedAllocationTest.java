package io.github.jabrena.juno.remote.lego.rcx;

import com.sun.management.ThreadMXBean;
import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Motion polling must allocate nothing on the board, and the pair adds nothing to the infrared exchange it is built on.
 * The test measures the bytes a thread allocates for the same number of exchanges made through the link and through the
 * pair. The test's own infrared stand-in boxes every byte it records, so the link's cost is the baseline, and the pair
 * is held to it.
 */
@DisplayName("RCX regulated pair allocation")
class RcxRegulatedAllocationTest {
    private static final int POLLS = 1_000;
    private static final int STATUS = 0x61;
    private static final int READY = 1;

    private final ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private RcxLink link;
    private RcxRegulatedPair pair;

    @BeforeEach
    void startWithAnArmedPair() {
        Wire.resetBoard();
        Infrared.reset();
        link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2), SimulatedTime.clock());
        pair = new RcxRegulatedPair(link, 1, RcxRegulatedPair.maxPower(7), 16);
    }

    /** Queues one status reply per poll, so the measured polls never run out of replies. */
    private static void queueStatusReplies(int polls) {
        for (int poll = 0; poll < polls; poll++) {
            List<Integer> bytes = new ArrayList<>(List.of(0x55, 0xFF, 0x00));
            int opcode = ~STATUS & 0xFF;
            int[] arguments = {1, 0, READY, 0, 0, 0, 0, 0};
            int sum = opcode;
            bytes.add(opcode);
            bytes.add(~opcode & 0xFF);
            for (int argument : arguments) {
                sum += argument;
                bytes.add(argument);
                bytes.add(~argument & 0xFF);
            }
            bytes.add(sum & 0xFF);
            bytes.add(~sum & 0xFF);
            Infrared.replies.addAll(bytes);
            Infrared.replies.add(Infrared.TIMEOUT);
        }
    }

    private long allocatedBytesDuring(Runnable work) {
        long before = threads.getThreadAllocatedBytes(Thread.currentThread().threadId());
        work.run();
        return threads.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
    }

    @Test
    @DisplayName("Status polling allocates no more than the infrared exchange it is built on")
    void shouldPollWithoutAllocatingBeyondTheLink() {
        // Given: the link's exchange, timed once to warm the JVM and the test's buffers, then measured
        int[] arguments = {1};
        int[] reply = new int[8];
        Runnable linkPolls = () -> {
            for (int poll = 0; poll < POLLS; poll++) {
                link.exchange(STATUS, arguments, 1, reply);
            }
        };
        Runnable pairPolls = () -> {
            for (int poll = 0; poll < POLLS; poll++) {
                pair.status();
            }
        };
        queueStatusReplies(POLLS);
        allocatedBytesDuring(linkPolls);
        queueStatusReplies(POLLS);
        Infrared.written.clear();
        long linkBytes = allocatedBytesDuring(() -> {
            Infrared.written.clear();
            linkPolls.run();
        });
        queueStatusReplies(POLLS);
        long pairBytes = allocatedBytesDuring(() -> {
            Infrared.written.clear();
            pairPolls.run();
        });

        // Then: the measurement is meaningful only if this JVM counts a thread's allocations
        assertThat(threads.isThreadAllocatedMemorySupported()).isTrue();
        assertThat(linkBytes).isPositive();
        assertThat(pairBytes).isLessThanOrEqualTo(linkBytes);
    }
}
