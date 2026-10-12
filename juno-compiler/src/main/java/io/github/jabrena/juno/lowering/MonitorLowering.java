package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;

import java.util.List;
import java.util.Optional;

import static io.github.jabrena.juno.bytecode.Opcodes.MONITORENTER;

/** {@code monitorenter}/{@code monitorexit}, backed by the runtime's monitor table. */
final class MonitorLowering implements InstructionLowering {
    private final MethodContext context;

    MonitorLowering(MethodContext context) {
        this.context = context;
    }

    @Override
    public void lower(Instruction instruction) {
        Value monitor = context.stack().pop();
        Intrinsic intrinsic = instruction.opcode() == MONITORENTER ? Intrinsic.MONITOR_ENTER : Intrinsic.MONITOR_EXIT;
        context.emit(new IrInstruction.IntrinsicCall(Optional.empty(), intrinsic, Optional.of(monitor), List.of(),
                List.of()));
    }
}
