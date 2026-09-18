package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Eliminates a {@link IrInstruction.LoadLocal} when an earlier {@link IrInstruction.StoreLocal} in the same
 * basic block determines the slot's value. Uses of the load's target are rewritten to the stored value.
 *
 * <p>Knowledge is deliberately discarded at every block boundary. JVM operand-stack positions are represented
 * as local slots in Juno IR, and different predecessor blocks may store different values into the same slot
 * before a merge. Propagating through such a merge requires a separate CFG data-flow analysis; limiting this
 * pass to one block keeps it sound while still removing the store/load round trips around most lowered JVM
 * instructions.
 */
public final class CopyPropagation implements CompilerPass {
    @Override
    public IrProgram apply(IrProgram program) {
        List<IrMethod> methods = new ArrayList<>();
        for (IrMethod method : program.methods()) {
            methods.add(propagateMethod(method));
        }
        return new IrProgram(program.entryPoint(), List.copyOf(methods));
    }

    private IrMethod propagateMethod(IrMethod method) {
        List<IrBasicBlock> blocks = new ArrayList<>();
        for (IrBasicBlock block : method.blocks()) {
            blocks.add(propagateBlock(block));
        }
        return new IrMethod(method.reference(), method.maxLocals(), method.values(),
                method.arrayDeclarations(), List.copyOf(blocks));
    }

    private IrBasicBlock propagateBlock(IrBasicBlock block) {
        Map<Integer, Value> storedValues = new HashMap<>();
        Map<Value, Value> replacements = new HashMap<>();
        List<IrInstruction> instructions = new ArrayList<>();

        for (IrInstruction instruction : block.instructions()) {
            if (instruction instanceof IrInstruction.LoadLocal load) {
                Value stored = storedValues.get(load.local());
                if (stored != null && stored.type() == load.target().type()) {
                    replacements.put(load.target(), resolve(replacements, stored));
                    continue;
                }
                instructions.add(load);
                continue;
            }

            IrInstruction rewritten = rewriteInstruction(instruction, replacements);
            instructions.add(rewritten);
            if (rewritten instanceof IrInstruction.StoreLocal store) {
                storedValues.put(store.local(), store.value());
            }
        }

        return new IrBasicBlock(block.start(), List.copyOf(instructions),
                rewriteTerminator(block.terminator(), replacements));
    }

    private IrInstruction rewriteInstruction(IrInstruction instruction, Map<Value, Value> replacements) {
        return switch (instruction) {
            case IrInstruction.Const constant -> constant;
            case IrInstruction.FloatConst constant -> constant;
            case IrInstruction.LoadLocal load -> load;
            case IrInstruction.StoreLocal store ->
                    new IrInstruction.StoreLocal(store.local(), resolve(replacements, store.value()));
            case IrInstruction.Binary binary -> new IrInstruction.Binary(binary.target(), binary.operation(),
                    resolve(replacements, binary.left()), resolve(replacements, binary.right()));
            case IrInstruction.Unary unary -> new IrInstruction.Unary(unary.target(), unary.operation(),
                    resolve(replacements, unary.value()));
            case IrInstruction.Compare compare -> new IrInstruction.Compare(compare.target(), compare.condition(),
                    resolve(replacements, compare.left()), resolve(replacements, compare.right()));
            case IrInstruction.Call call -> new IrInstruction.Call(call.target(), call.method(),
                    rewriteValues(call.arguments(), replacements));
            case IrInstruction.IntrinsicCall call -> new IrInstruction.IntrinsicCall(call.target(), call.intrinsic(),
                    rewriteOptional(call.receiver(), replacements), rewriteValues(call.arguments(), replacements));
            case IrInstruction.NewArray newArray -> newArray;
            case IrInstruction.ArrayLoad load -> new IrInstruction.ArrayLoad(load.target(), load.elementType(),
                    resolve(replacements, load.array()), resolve(replacements, load.index()));
            case IrInstruction.ArrayStore store -> new IrInstruction.ArrayStore(store.elementType(),
                    resolve(replacements, store.array()), resolve(replacements, store.index()),
                    resolve(replacements, store.value()));
            case IrInstruction.BoundsCheck check ->
                    new IrInstruction.BoundsCheck(resolve(replacements, check.index()), check.length());
            case IrInstruction.LongConst constant -> constant;
            case IrInstruction.LongBinary binary -> new IrInstruction.LongBinary(
                    binary.targetLow(), binary.targetHigh(), binary.operation(),
                    resolve(replacements, binary.leftLow()), resolve(replacements, binary.leftHigh()),
                    resolve(replacements, binary.rightLow()), resolve(replacements, binary.rightHigh()));
            case IrInstruction.LongShift shift -> new IrInstruction.LongShift(
                    shift.targetLow(), shift.targetHigh(), shift.operation(),
                    resolve(replacements, shift.valueLow()), resolve(replacements, shift.valueHigh()),
                    resolve(replacements, shift.shiftAmount()));
            case IrInstruction.LongNegate negate -> new IrInstruction.LongNegate(
                    negate.targetLow(), negate.targetHigh(), resolve(replacements, negate.valueLow()),
                    resolve(replacements, negate.valueHigh()));
            case IrInstruction.LongCompare compare -> new IrInstruction.LongCompare(compare.target(),
                    resolve(replacements, compare.leftLow()), resolve(replacements, compare.leftHigh()),
                    resolve(replacements, compare.rightLow()), resolve(replacements, compare.rightHigh()));
            case IrInstruction.IntToLong widen -> new IrInstruction.IntToLong(
                    widen.targetLow(), widen.targetHigh(), resolve(replacements, widen.value()));
            case IrInstruction.LongToInt narrow -> new IrInstruction.LongToInt(narrow.target(),
                    resolve(replacements, narrow.valueLow()), resolve(replacements, narrow.valueHigh()));
            case IrInstruction.FloatBinary binary -> new IrInstruction.FloatBinary(binary.target(), binary.operation(),
                    resolve(replacements, binary.left()), resolve(replacements, binary.right()));
            case IrInstruction.FloatNegate negate -> new IrInstruction.FloatNegate(negate.target(),
                    resolve(replacements, negate.value()));
            case IrInstruction.FloatCompare compare -> new IrInstruction.FloatCompare(compare.target(),
                    resolve(replacements, compare.left()), resolve(replacements, compare.right()), compare.nanResult());
            case IrInstruction.IntToFloat conversion -> new IrInstruction.IntToFloat(conversion.target(),
                    resolve(replacements, conversion.value()));
            case IrInstruction.FloatToInt conversion -> new IrInstruction.FloatToInt(conversion.target(),
                    resolve(replacements, conversion.value()));
        };
    }

    private IrTerminator rewriteTerminator(IrTerminator terminator, Map<Value, Value> replacements) {
        return switch (terminator) {
            case IrTerminator.Jump jump -> jump;
            case IrTerminator.Branch branch -> new IrTerminator.Branch(
                    resolve(replacements, branch.condition()), branch.trueTarget(), branch.falseTarget());
            case IrTerminator.Return returned -> new IrTerminator.Return(
                    rewriteOptional(returned.value(), replacements));
        };
    }

    private List<Value> rewriteValues(List<Value> values, Map<Value, Value> replacements) {
        return values.stream().map(value -> resolve(replacements, value)).toList();
    }

    private Optional<Value> rewriteOptional(Optional<Value> value, Map<Value, Value> replacements) {
        return value.map(item -> resolve(replacements, item));
    }

    private Value resolve(Map<Value, Value> replacements, Value value) {
        Value current = value;
        Value replacement;
        while ((replacement = replacements.get(current)) != null) {
            current = replacement;
        }
        return current;
    }
}
