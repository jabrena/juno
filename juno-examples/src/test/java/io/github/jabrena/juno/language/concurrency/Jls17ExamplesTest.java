package io.github.jabrena.juno.language.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Deterministic JVM checks for the focused JLS 17 examples. */
class Jls17ExamplesTest {

    @Test
    void monitorUnlockPublishesTheGuardedValue() throws InterruptedException {
        assertThat(new MonitorHappensBefore().verify()).isEqualTo(42);
    }

    @Test
    void volatileFlagPublishesTheEarlierWrite() throws InterruptedException {
        assertThat(new VolatileHappensBefore().verify()).isEqualTo(42);
    }

    @Test
    void startAndJoinPublishInBothDirections() throws InterruptedException {
        assertThat(new ThreadStartAndJoin().verify()).isEqualTo(42);
    }

    @Test
    void sleepAndYieldRetainTheMonitor() throws InterruptedException {
        assertThat(new SleepAndYield().verify()).isEqualTo(1);
    }

    @Test
    void observedFinalFieldHasItsConstructedValue() throws InterruptedException {
        assertThat(new FinalFieldPublication().verify()).isIn(-1, 42);
    }

    @Test
    void causalityForbidsAnOutOfThinAirValue() throws InterruptedException {
        assertThat(new Causality().verify()).isZero();
    }

    @Test
    void adjacentByteElementsDoNotTear() throws InterruptedException {
        assertThat(new WordTearing().verify()).isZero();
    }

    @Test
    void referenceAccessIsAtomic() throws InterruptedException {
        assertThat(new ReferenceAtomicity().verify()).isZero();
    }

    @Test
    void volatileLongAccessIsAtomic() throws InterruptedException {
        assertThat(new VolatileLongAtomicity().verify()).isZero();
    }

    @Test
    void waitAndNotifyAllExchangeTheGuardedValue() throws InterruptedException {
        WaitNotification exchange = new WaitNotification();
        AtomicInteger observed = new AtomicInteger();
        Thread reader = Thread.startVirtualThread(() -> {
            try {
                observed.set(exchange.await());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });

        exchange.publish(42);
        reader.join();

        assertThat(observed).hasValue(42);
    }

    @Test
    void interruptedSleepRestoresTheStatus() throws InterruptedException {
        AtomicBoolean restored = new AtomicBoolean();
        Thread sleeper = Thread.startVirtualThread(() -> restored.set(Interruption.sleepAndRestoreStatus()
                && Thread.currentThread().isInterrupted()));

        sleeper.interrupt();
        sleeper.join();

        assertThat(restored).isTrue();
    }

    @Test
    void concreteReentrantLockProvidesExclusionAndReentrancy() throws InterruptedException {
        assertThat(new Locks().verify()).isEqualTo(201);
    }

    @Test
    void atomicIntegerLongAndBooleanCellsProvideAtomicOperations() throws InterruptedException {
        assertThat(new Atomics().verify()).isEqualTo(600);
    }

    @Test
    void semaphoreBoundsConcurrentWorkers() throws InterruptedException {
        assertThat(Semaphores.run()).isEqualTo(4);
    }

    @Test
    void countDownLatchReleasesTheCoordinatorOnceAllWorkersFinish() throws InterruptedException {
        assertThat(CountDownLatchExample.run()).isEqualTo(42);
    }

    @Test
    void phaserCoordinatesSeveralReusablePhases() throws InterruptedException {
        assertThat(PhaserExample.run()).isEqualTo(4);
    }

    @Test
    void cyclicBarrierRunsItsActionAtEachRound() throws Exception {
        assertThat(CyclicBarrierExample.run()).isEqualTo(2);
    }

    @Test
    void exchangerSwapsValuesAtATwoPartyRendezvous() throws InterruptedException {
        assertThat(ExchangerExample.run()).isEqualTo("right:left");
    }

    @Test
    void readWriteLockPublishesConsistentReads() throws InterruptedException {
        assertThat(ReadWriteLockExample.run()).isEqualTo(84);
    }

    @Test
    void blockingQueueTransfersEveryItem() throws InterruptedException {
        assertThat(BlockingQueueExample.run()).isEqualTo(10);
    }
}
