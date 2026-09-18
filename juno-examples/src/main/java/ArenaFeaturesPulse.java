import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.DigitalOutput;

/** Board test for arena objects, escaping/nested arrays, records, enum switch, and static initialization. */
public final class ArenaFeaturesPulse {
    private static int initialPause = 100;

    private enum Speed {
        FAST,
        SLOW
    }

    private record Step(int millis) {
    }

    private static final class Counter {
        private int value;

        private Counter(int value) {
            this.value = value;
        }

        private int advance(Step step) {
            value = value + step.millis();
            return value;
        }
    }

    private ArenaFeaturesPulse() {
    }

    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(13);
        int[][] adjustment = new int[2][2];
        adjustment[0][0] = 20;
        adjustment[1][1] = 40;

        Step fast = echo(new Step(delayFor(Speed.FAST) + adjustment[0][0]));
        Step slow = echo(new Step(delayFor(Speed.SLOW) + adjustment[1][1]));
        Counter counter = new Counter(initialPause);
        int[] pauses = createPauses(counter.advance(fast), counter.advance(slow));
        int index = 0;

        while (true) {
            led.high();
            Delay.millis(pauses[index]);
            led.low();
            Delay.millis(pauses[index]);
            index = index == 0 ? 1 : 0;
        }
    }

    private static Step echo(Step step) {
        return step;
    }

    private static int[] createPauses(int first, int second) {
        int[] pauses = new int[2];
        pauses[0] = first;
        pauses[1] = second;
        return pauses;
    }

    private static int delayFor(Speed speed) {
        return switch (speed) {
            case FAST -> 40;
            case SLOW -> 160;
        };
    }
}
