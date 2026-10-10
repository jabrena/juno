package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.IrValues;
import io.github.jabrena.juno.ir.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Eliminates a {@link IrInstruction.LoadLocal} when preceding stores determine the slot's value. Uses of the
 * load's target are rewritten to the stored value.
 *
 * <p>A forward CFG data-flow analysis intersects predecessor states: knowledge crosses a block boundary only
 * when every predecessor carries the exact same typed IR value in that local slot. Conflicting branch values
 * become unknown. The transformation repeats until no load can be removed, allowing a propagated load/store
 * chain to expose another fact in the next iteration. Field loads are never treated as memory facts: every
 * {@code volatile} field read remains an actual read across calls and loop backedges.
 */
public final class CopyPropagation implements CompilerPass {
    @Override
    public IrProgram apply(IrProgram program) {
        IrProgram current = program;
        while (true) {
            IrProgram propagated = propagateOnce(current);
            if (propagated.equals(current)) {
                return propagated;
            }
            current = propagated;
        }
    }

    private IrProgram propagateOnce(IrProgram program) {
        List<IrMethod> methods = new ArrayList<>();
        for (IrMethod method : program.methods()) {
            methods.add(propagateMethod(method));
        }
        return program.withMethods(methods);
    }

    private IrMethod propagateMethod(IrMethod method) {
        Map<Integer, Set<Integer>> predecessors = predecessors(method);
        Set<Value> crossBlockValues = crossBlockValues(method);
        Map<Integer, Map<Integer, Value>> outgoing = new LinkedHashMap<>();
        boolean changed;
        do {
            changed = false;
            for (int index = 0; index < method.blocks().size(); index++) {
                IrBasicBlock block = method.blocks().get(index);
                Map<Integer, Value> incoming = index == 0 ? Map.of()
                        : meet(predecessors.getOrDefault(block.start(), Set.of()), outgoing);
                Map<Integer, Value> transferred = propagateBlock(block, incoming, crossBlockValues).outgoing();
                if (!transferred.equals(outgoing.put(block.start(), transferred))) {
                    changed = true;
                }
            }
        } while (changed);

        List<IrBasicBlock> blocks = new ArrayList<>();
        for (int index = 0; index < method.blocks().size(); index++) {
            IrBasicBlock block = method.blocks().get(index);
            Map<Integer, Value> incoming = index == 0 ? Map.of()
                    : meet(predecessors.getOrDefault(block.start(), Set.of()), outgoing);
            blocks.add(propagateBlock(block, incoming, crossBlockValues).block());
        }
        return new IrMethod(method.reference(), method.isStatic(), method.maxLocals(), method.values(),
                method.arrayDeclarations(), List.copyOf(blocks));
    }

    private Set<Value> crossBlockValues(IrMethod method) {
        Map<Value, Integer> blocksSeen = new HashMap<>();
        for (IrBasicBlock block : method.blocks()) {
            Set<Value> values = new LinkedHashSet<>();
            block.instructions().forEach(instruction -> values.addAll(IrValues.of(instruction)));
            values.addAll(IrValues.of(block.terminator()));
            values.forEach(value -> blocksSeen.merge(value, 1, Integer::sum));
        }
        Set<Value> result = new LinkedHashSet<>();
        blocksSeen.forEach((value, count) -> {
            if (count > 1) {
                result.add(value);
            }
        });
        return Set.copyOf(result);
    }

    private Map<Integer, Set<Integer>> predecessors(IrMethod method) {
        Map<Integer, Set<Integer>> predecessors = new LinkedHashMap<>();
        for (IrBasicBlock block : method.blocks()) {
            predecessors.putIfAbsent(block.start(), new LinkedHashSet<>());
        }
        for (IrBasicBlock block : method.blocks()) {
            for (int successor : successors(block.terminator())) {
                Set<Integer> incoming = predecessors.get(successor);
                if (incoming != null) {
                    incoming.add(block.start());
                }
            }
        }
        return predecessors;
    }

    private List<Integer> successors(IrTerminator terminator) {
        return switch (terminator) {
            case IrTerminator.Jump jump -> List.of(jump.target());
            case IrTerminator.Branch branch -> List.of(branch.trueTarget(), branch.falseTarget());
            case IrTerminator.Return ignored -> List.of();
            case IrTerminator.Switch switched -> {
                List<Integer> targets = new ArrayList<>(switched.targets());
                targets.add(switched.defaultTarget());
                yield targets;
            }
        };
    }

    private Map<Integer, Value> meet(Set<Integer> predecessors,
                                     Map<Integer, Map<Integer, Value>> outgoing) {
        Map<Integer, Value> result = null;
        for (int predecessor : predecessors) {
            Map<Integer, Value> state = outgoing.get(predecessor);
            if (state == null) {
                continue;
            }
            if (result == null) {
                result = new HashMap<>(state);
            } else {
                result.entrySet().removeIf(entry -> !entry.getValue().equals(state.get(entry.getKey())));
            }
        }
        return result == null ? Map.of() : Map.copyOf(result);
    }

    /**
     * The block with its determined loads removed, and the slot facts at its end. Those facts must come from the
     * rewritten stores: a store of a load this block removes now stores that load's replacement, and a successor
     * rewritten to the removed load's target would read a value nothing defines any more.
     */
    private Propagated propagateBlock(IrBasicBlock block, Map<Integer, Value> incoming,
                                      Set<Value> crossBlockValues) {
        Map<Integer, Value> storedValues = new HashMap<>(incoming);
        Map<Value, Value> replacements = new HashMap<>();
        List<IrInstruction> instructions = new ArrayList<>();

        for (IrInstruction instruction : block.instructions()) {
            if (instruction instanceof IrInstruction.LoadLocal load) {
                Value stored = storedValues.get(load.local());
                if (stored != null && stored.type() == load.target().type()
                        && !crossBlockValues.contains(load.target())) {
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

        return new Propagated(new IrBasicBlock(block.start(), List.copyOf(instructions),
                rewriteTerminator(block.terminator(), replacements)), Map.copyOf(storedValues));
    }

    private record Propagated(IrBasicBlock block, Map<Integer, Value> outgoing) {
    }

    private IrInstruction rewriteInstruction(IrInstruction instruction, Map<Value, Value> replacements) {
        return switch (instruction) {
            case IrInstruction.Const constant -> constant;
            case IrInstruction.StringConst constant -> constant;
            case IrInstruction.FloatConst constant -> constant;
            case IrInstruction.DoubleConst constant -> constant;
            case IrInstruction.LoadLocal load -> load;
            case IrInstruction.StoreLocal store ->
                    new IrInstruction.StoreLocal(store.local(), resolve(replacements, store.value()));
            case IrInstruction.LoadStatic load -> load;
            case IrInstruction.StoreStatic store ->
                    new IrInstruction.StoreStatic(store.field(), resolve(replacements, store.value()));
            case IrInstruction.NewObject object -> object;
            case IrInstruction.LambdaCreate lambda -> new IrInstruction.LambdaCreate(lambda.target(), lambda.site(),
                    rewriteValues(lambda.captures(), replacements));
            case IrInstruction.LoadField load -> new IrInstruction.LoadField(load.target(), load.field(),
                    resolve(replacements, load.receiver()));
            case IrInstruction.StoreField store -> new IrInstruction.StoreField(store.field(),
                    resolve(replacements, store.receiver()), resolve(replacements, store.value()));
            case IrInstruction.IntArrayConst array -> array;
            case IrInstruction.ConstantTableRef table -> table;
            case IrInstruction.NewMultiArray array -> array;
            case IrInstruction.Panic panic -> panic;
            case IrInstruction.NullCheck check -> new IrInstruction.NullCheck(
                    resolve(replacements, check.value()));
            case IrInstruction.Binary binary -> new IrInstruction.Binary(binary.target(), binary.operation(),
                    resolve(replacements, binary.left()), resolve(replacements, binary.right()));
            case IrInstruction.Unary unary -> new IrInstruction.Unary(unary.target(), unary.operation(),
                    resolve(replacements, unary.value()));
            case IrInstruction.Compare compare -> new IrInstruction.Compare(compare.target(), compare.condition(),
                    resolve(replacements, compare.left()), resolve(replacements, compare.right()));
            case IrInstruction.Call call -> new IrInstruction.Call(call.target(), call.method(),
                    rewriteValues(call.arguments(), replacements));
            case IrInstruction.LambdaCall call -> new IrInstruction.LambdaCall(call.target(), call.site(),
                    rewriteValues(call.arguments(), replacements));
            case IrInstruction.InterfaceCall call -> new IrInstruction.InterfaceCall(call.target(),
                    rewriteValues(call.arguments(), replacements), call.targets());
            case IrInstruction.IntrinsicCall call -> new IrInstruction.IntrinsicCall(call.target(), call.intrinsic(),
                    rewriteOptional(call.receiver(), replacements), rewriteValues(call.arguments(), replacements),
                    call.literalArguments());
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
            case IrInstruction.DoubleBinary binary -> new IrInstruction.DoubleBinary(binary.target(), binary.operation(),
                    resolve(replacements, binary.left()), resolve(replacements, binary.right()));
            case IrInstruction.DoubleNegate negate -> new IrInstruction.DoubleNegate(negate.target(),
                    resolve(replacements, negate.value()));
            case IrInstruction.DoubleCompare compare -> new IrInstruction.DoubleCompare(compare.target(),
                    resolve(replacements, compare.left()), resolve(replacements, compare.right()), compare.nanResult());
            case IrInstruction.IntToDouble conversion -> new IrInstruction.IntToDouble(conversion.target(),
                    resolve(replacements, conversion.value()));
            case IrInstruction.DoubleToInt conversion -> new IrInstruction.DoubleToInt(conversion.target(),
                    resolve(replacements, conversion.value()));
            case IrInstruction.FloatToDouble conversion -> new IrInstruction.FloatToDouble(conversion.target(),
                    resolve(replacements, conversion.value()));
            case IrInstruction.DoubleToFloat conversion -> new IrInstruction.DoubleToFloat(conversion.target(),
                    resolve(replacements, conversion.value()));
            case IrInstruction.LongToDouble conversion -> new IrInstruction.LongToDouble(conversion.target(),
                    resolve(replacements, conversion.valueLow()), resolve(replacements, conversion.valueHigh()));
            case IrInstruction.DoubleToLong conversion -> new IrInstruction.DoubleToLong(
                    conversion.targetLow(), conversion.targetHigh(), resolve(replacements, conversion.value()));
            case IrInstruction.PackLong packed -> new IrInstruction.PackLong(packed.target(),
                    resolve(replacements, packed.valueLow()), resolve(replacements, packed.valueHigh()));
            case IrInstruction.UnpackLong unpacked -> new IrInstruction.UnpackLong(
                    unpacked.targetLow(), unpacked.targetHigh(), resolve(replacements, unpacked.value()));
            case IrInstruction.LongToFloat conversion -> new IrInstruction.LongToFloat(conversion.target(),
                    resolve(replacements, conversion.valueLow()), resolve(replacements, conversion.valueHigh()));
            case IrInstruction.FloatToLong conversion -> new IrInstruction.FloatToLong(
                    conversion.targetLow(), conversion.targetHigh(), resolve(replacements, conversion.value()));
        };
    }

    private IrTerminator rewriteTerminator(IrTerminator terminator, Map<Value, Value> replacements) {
        return switch (terminator) {
            case IrTerminator.Jump jump -> jump;
            case IrTerminator.Branch branch -> new IrTerminator.Branch(
                    resolve(replacements, branch.condition()), branch.trueTarget(), branch.falseTarget());
            case IrTerminator.Return returned -> new IrTerminator.Return(
                    rewriteOptional(returned.value(), replacements));
            case IrTerminator.Switch switched -> new IrTerminator.Switch(
                    resolve(replacements, switched.selector()), switched.keys(), switched.targets(),
                    switched.defaultTarget());
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
