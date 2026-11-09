package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.hardware.device.ir.Infrared;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A regulated brick behind the infrared test double. It answers each request the link writes, and it executes every
 * request even when its reply is lost, corrupted, duplicated or preceded by noise, as the brick does on the air. Its
 * wheels move a few steps toward their targets on each status poll; a power loss clears its session.
 */
final class RegulatedBrick implements Infrared.Brick {
    /** What happens to one reply. */
    enum Fault { NONE, DROP, CORRUPT, DUPLICATE, NOISE }

    private static final int READY = 1;
    private static final int RUNNING = 2;
    private static final int COMPLETE = 3;
    private static final int STOPPED = 4;
    private static final int STEP = 4;
    private static final int OPCODE_TOGGLE = 0x08;
    private static final int ALIVE_REPLY = 0xEF;

    private record Plan(int base, List<Fault> faults) {
    }

    private final Map<Integer, Plan> plans = new HashMap<>();
    private final Map<Integer, Integer> requests = new HashMap<>();
    private final Map<Integer, Integer> accepted = new HashMap<>();
    private int session;
    private int motion;
    private int state;
    private int targetLeft;
    private int targetRight;
    private int pendingLeft;
    private int pendingRight;
    private int positionLeft;
    private int positionRight;

    /** Injects a sequence of faults, one per request of {@code opcode} from the next one on; then no faults. */
    void inject(int opcode, Fault... faults) {
        plans.put(opcode, new Plan(requests.getOrDefault(opcode, 0), List.of(faults)));
    }

    /** How many requests of {@code opcode} the brick has executed, whatever became of their replies. */
    int executed(int opcode) {
        return requests.getOrDefault(opcode, 0);
    }

    /** How many starts the brick accepted for a new motion. */
    int accepted(int opcode) {
        return accepted.getOrDefault(opcode, 0);
    }

    /** A power loss: the brick forgets its session and its motion. */
    void powerLoss() {
        session = 0;
        state = 0;
    }

    @Override
    public List<Integer> answer(List<Integer> sent) {
        int[] request = lastRequest(sent);
        if (request == null) {
            return List.of();
        }
        int opcode = request[0] & ~OPCODE_TOGGLE;
        int occurrence = requests.merge(opcode, 1, Integer::sum) - 1;
        int[] reply = execute(opcode, java.util.Arrays.copyOfRange(request, 1, request.length));
        if (opcode == 0x52 && reply[0] == 0) {
            accepted.merge(opcode, 1, Integer::sum);
        }
        Fault fault = faultFor(opcode, occurrence);
        int complement = ~opcode & 0xFF;
        return switch (fault) {
            case DROP -> List.of();
            case CORRUPT -> frame(complement, reply, true);
            case DUPLICATE -> then(frame(complement, reply, false), frame(complement, reply, false));
            case NOISE -> then(frame(ALIVE_REPLY, new int[0], false), frame(complement, reply, false));
            case NONE -> frame(complement, reply, false);
        };
    }

    private Fault faultFor(int opcode, int occurrence) {
        Plan plan = plans.get(opcode);
        if (plan == null || occurrence < plan.base() || occurrence - plan.base() >= plan.faults().size()) {
            return Fault.NONE;
        }
        return plan.faults().get(occurrence - plan.base());
    }

    /** Executes a request and returns its reply arguments. */
    private int[] execute(int opcode, int[] arguments) {
        return switch (opcode) {
            case 0x63 -> arm(arguments);
            case 0x64, 0x74 -> profile(arguments);
            case 0x33 -> stage(arguments, true);
            case 0x43 -> stage(arguments, false);
            case 0x52 -> start(arguments);
            case 0x62 -> stop(arguments);
            case 0x61 -> status();
            default -> new int[] {0};
        };
    }

    private int[] arm(int[] arguments) {
        session = arguments[0];
        if (arguments[1] != 1) {
            return new int[] {5, 1, 1};
        }
        state = READY;
        motion = 0;
        positionLeft = 0;
        positionRight = 0;
        return new int[] {0, 1, 1};
    }

    private int[] profile(int[] arguments) {
        return arguments[0] == session ? new int[] {0} : new int[] {4};
    }

    /** Stages a side's target for the next start, as the firmware's 0x33 and 0x43 do. */
    private int[] stage(int[] arguments, boolean leftSide) {
        if (arguments[0] != session) {
            return new int[] {4};
        }
        int target = (short) (arguments[1] | arguments[2] << 8);
        if (leftSide) {
            pendingLeft = target;
        } else {
            pendingRight = target;
        }
        return new int[] {0};
    }

    private int[] start(int[] arguments) {
        int requestedSession = arguments[0];
        int requestedMotion = arguments[1];
        int left = pendingLeft;
        int right = pendingRight;
        if (requestedSession != session) {
            return new int[] {4, requestedSession, requestedMotion};
        }
        if (requestedMotion == motion) {
            boolean same = left == targetLeft && right == targetRight;
            return new int[] {same ? 1 : 7, requestedSession, requestedMotion};
        }
        if (requestedMotion < motion) {
            return new int[] {8, requestedSession, requestedMotion};
        }
        if (state == RUNNING) {
            return new int[] {3, requestedSession, requestedMotion};
        }
        motion = requestedMotion;
        targetLeft = left;
        targetRight = right;
        state = RUNNING;
        return new int[] {0, requestedSession, requestedMotion};
    }

    private int[] stop(int[] arguments) {
        int requestedSession = arguments[0];
        int requestedMotion = arguments[1];
        if (requestedSession != session) {
            return new int[] {4, requestedSession, requestedMotion};
        }
        if (requestedMotion != motion) {
            return new int[] {8, requestedSession, requestedMotion};
        }
        if (state == RUNNING) {
            state = STOPPED;
            return new int[] {0, requestedSession, requestedMotion};
        }
        return new int[] {1, requestedSession, requestedMotion};
    }

    /** A status poll: the wheels advance toward their targets, and the motion completes when both arrive. */
    private int[] status() {
        if (state == RUNNING) {
            positionLeft += Math.clamp(targetLeft - positionLeft, -STEP, STEP);
            positionRight += Math.clamp(targetRight - positionRight, -STEP, STEP);
            if (positionLeft == targetLeft && positionRight == targetRight) {
                state = COMPLETE;
            }
        }
        return new int[] {session, motion, state, 0,
            positionLeft & 0xFF, (positionLeft >> 8) & 0xFF, positionRight & 0xFF, (positionRight >> 8) & 0xFF};
    }

    /** The last complete request in the bytes written: its opcode and arguments, or null when there is none. */
    private static int[] lastRequest(List<Integer> sent) {
        int header = -1;
        for (int index = 0; index + 2 < sent.size(); index++) {
            if (sent.get(index) == 0x55 && sent.get(index + 1) == 0xFF && sent.get(index + 2) == 0x00) {
                header = index;
            }
        }
        if (header < 0 || header + 3 >= sent.size()) {
            return null;
        }
        int code = sent.get(header + 3);
        int[] values = new int[(code & 0x07) + 1];
        for (int value = 0; value < values.length; value++) {
            int at = header + 3 + 2 * value;
            if (at >= sent.size()) {
                return null;
            }
            values[value] = sent.get(at);
        }
        return values;
    }

    /** One reply frame: the header, then each byte with its complement, the checksum last; a corrupt one is wrong. */
    private static List<Integer> frame(int opcode, int[] arguments, boolean corrupt) {
        List<Integer> bytes = new ArrayList<>(List.of(0x55, 0xFF, 0x00));
        int sum = opcode;
        pair(bytes, opcode);
        for (int argument : arguments) {
            sum += argument;
            pair(bytes, argument);
        }
        int checksum = (sum & 0xFF) ^ (corrupt ? 0x01 : 0);
        pair(bytes, checksum);
        return bytes;
    }

    /** Two frames on the air: silence separates them, as the receiver needs to see the end of the first. */
    private static List<Integer> then(List<Integer> first, List<Integer> second) {
        List<Integer> both = new ArrayList<>(first);
        both.add(Infrared.TIMEOUT);
        both.addAll(second);
        return both;
    }

    private static void pair(List<Integer> bytes, int value) {
        bytes.add(value);
        bytes.add(~value & 0xFF);
    }
}
