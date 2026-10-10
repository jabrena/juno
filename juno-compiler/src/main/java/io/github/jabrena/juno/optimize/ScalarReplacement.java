package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.classfile.FieldRef;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrValues;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Removes an allocation whose object never escapes the method, keeping its fields in local slots instead of the
 * arena: no {@code juno_alloc}, no garbage for the collector, and field accesses that copy propagation and constant
 * folding can see through.
 *
 * <p>An object escapes unless every use of it is as the receiver of a field load or store, or a null check: passing
 * it to a call, storing it anywhere, returning it, comparing it or capturing it keeps the allocation. Inlining small
 * constructors and accessors first is what makes this common (a record built, read and dropped in a loop). Java
 * zeroes a new object's fields, so the slots are zeroed where the allocation was, which also gives each loop
 * iteration a fresh object. Objects with a {@code long} field (kept as split words in the IR) are left alone.
 */
public final class ScalarReplacement implements CompilerPass {
    @Override
    public IrProgram apply(IrProgram program) {
        List<IrMethod> methods = new ArrayList<>(program.methods().size());
        for (IrMethod method : program.methods()) {
            methods.add(replace(method));
        }
        return program.withMethods(methods);
    }

    private IrMethod replace(IrMethod method) {
        Map<Value, Map<FieldRef, JunoType>> candidates = candidates(method);
        if (candidates.isEmpty()) {
            return method;
        }
        List<Value> values = new ArrayList<>(method.values());
        Map<Value, Map<FieldRef, Integer>> slots = new HashMap<>();
        int maxLocals = method.maxLocals();
        for (Map.Entry<Value, Map<FieldRef, JunoType>> candidate : candidates.entrySet()) {
            Map<FieldRef, Integer> fieldSlots = new LinkedHashMap<>();
            for (Map.Entry<FieldRef, JunoType> field : candidate.getValue().entrySet()) {
                fieldSlots.put(field.getKey(), maxLocals);
                maxLocals += field.getValue().jvmSlots();
            }
            slots.put(candidate.getKey(), fieldSlots);
        }
        List<IrBasicBlock> blocks = new ArrayList<>(method.blocks().size());
        for (IrBasicBlock block : method.blocks()) {
            List<IrInstruction> instructions = new ArrayList<>();
            for (IrInstruction instruction : block.instructions()) {
                rewrite(instruction, candidates, slots, values, instructions);
            }
            blocks.add(new IrBasicBlock(block.start(), List.copyOf(instructions), block.terminator()));
        }
        return new IrMethod(method.reference(), method.isStatic(), maxLocals, values, method.arrayDeclarations(),
                List.copyOf(blocks));
    }

    private static void rewrite(IrInstruction instruction, Map<Value, Map<FieldRef, JunoType>> candidates,
                                Map<Value, Map<FieldRef, Integer>> slots, List<Value> values,
                                List<IrInstruction> out) {
        switch (instruction) {
            case IrInstruction.NewObject object when candidates.containsKey(object.target()) -> {
                Map<FieldRef, JunoType> fields = candidates.get(object.target());
                for (Map.Entry<FieldRef, Integer> slot : slots.get(object.target()).entrySet()) {
                    JunoType type = fields.get(slot.getKey());
                    Value zero = new Value(values.size(), type);
                    values.add(zero);
                    out.add(type == JunoType.FLOAT64 ? new IrInstruction.DoubleConst(zero, 0.0)
                            : type == JunoType.FLOAT32 ? new IrInstruction.FloatConst(zero, 0.0f)
                            : new IrInstruction.Const(zero, 0));
                    out.add(new IrInstruction.StoreLocal(slot.getValue(), zero));
                }
            }
            case IrInstruction.StoreField store when candidates.containsKey(store.receiver()) ->
                    out.add(new IrInstruction.StoreLocal(slots.get(store.receiver()).get(store.field()), store.value()));
            case IrInstruction.LoadField load when candidates.containsKey(load.receiver()) ->
                    out.add(new IrInstruction.LoadLocal(load.target(), slots.get(load.receiver()).get(load.field())));
            case IrInstruction.NullCheck check when candidates.containsKey(check.value()) -> {
                // A freshly allocated object is never null.
            }
            default -> out.add(instruction);
        }
    }

    /** Each non-escaping allocation in the method, with the fields it uses and their value types. */
    private static Map<Value, Map<FieldRef, JunoType>> candidates(IrMethod method) {
        Map<Value, Map<FieldRef, JunoType>> fields = new LinkedHashMap<>();
        for (IrBasicBlock block : method.blocks()) {
            for (IrInstruction instruction : block.instructions()) {
                if (instruction instanceof IrInstruction.NewObject object) {
                    fields.put(object.target(), new LinkedHashMap<>());
                }
            }
        }
        if (fields.isEmpty()) {
            return Map.of();
        }
        Set<Value> escaped = new HashSet<>();
        for (IrBasicBlock block : method.blocks()) {
            for (IrInstruction instruction : block.instructions()) {
                note(instruction, fields, escaped);
            }
            IrValues.of(block.terminator()).stream().filter(fields::containsKey).forEach(escaped::add);
        }
        fields.keySet().removeAll(escaped);
        fields.values().removeIf(ScalarReplacement::hidesAField);
        return fields;
    }

    /** Records how one instruction uses the allocations: as a receiver it adds a field, anything else escapes. */
    private static void note(IrInstruction instruction, Map<Value, Map<FieldRef, JunoType>> fields, Set<Value> escaped) {
        switch (instruction) {
            case IrInstruction.NewObject ignored -> {
            }
            case IrInstruction.LoadField load when fields.containsKey(load.receiver()) ->
                    addField(fields.get(load.receiver()), load.field(), load.target().type(), load.receiver(), escaped);
            case IrInstruction.StoreField store when fields.containsKey(store.receiver()) -> {
                addField(fields.get(store.receiver()), store.field(), store.value().type(), store.receiver(), escaped);
                if (fields.containsKey(store.value())) {
                    escaped.add(store.value());
                }
            }
            case IrInstruction.NullCheck ignored -> {
            }
            default -> IrValues.of(instruction).stream().filter(fields::containsKey).forEach(escaped::add);
        }
    }

    private static void addField(Map<FieldRef, JunoType> fields, FieldRef field, JunoType type, Value object,
                                 Set<Value> escaped) {
        JunoType previous = fields.putIfAbsent(field, type);
        if (type == JunoType.INT64 || previous != null && previous != type) {
            escaped.add(object);
        }
    }

    /** Two fields of one name from different classes: a subclass hiding a superclass field. Left alone. */
    private static boolean hidesAField(Map<FieldRef, JunoType> fields) {
        Set<String> names = new HashSet<>();
        for (FieldRef field : fields.keySet()) {
            if (!names.add(field.name())) {
                return true;
            }
        }
        return false;
    }
}
