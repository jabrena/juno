package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrInstruction;

import java.util.List;
import java.util.Optional;

import static io.github.jabrena.juno.lowering.StackValueOps.pop;

/** Lowers {@code monitorenter}/{@code monitorexit} to the cooperative monitor runtime. */
final class MonitorLowering {
    private MonitorLowering() {
    }

    static Lowered lower(int opcode, List<IrInstruction> instructions, int stackBase, int depth,
                         int nextValueId, ValueTracking tracking) {
        Popped monitor = pop(instructions, stackBase, --depth, nextValueId, tracking);
        Intrinsic intrinsic = opcode == 194 ? Intrinsic.MONITOR_ENTER : Intrinsic.MONITOR_EXIT;
        instructions.add(new IrInstruction.IntrinsicCall(Optional.empty(), intrinsic,
                Optional.of(monitor.value()), List.of(), List.of()));
        return new Lowered(monitor.nextValueId(), depth);
    }
}
