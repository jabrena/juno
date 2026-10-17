package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.BytecodeDecoder;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.ConstantPool;
import io.github.jabrena.juno.classfile.FieldInfo;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.classfile.JavaMethod;
import io.github.jabrena.juno.classfile.MethodRef;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static io.github.jabrena.juno.bytecode.Opcodes.INVOKESTATIC;
import static io.github.jabrena.juno.bytecode.Opcodes.LDC;
import static io.github.jabrena.juno.bytecode.Opcodes.LDC2_W;
import static io.github.jabrena.juno.bytecode.Opcodes.LDC_W;
import static io.github.jabrena.juno.bytecode.Opcodes.PUTSTATIC;

/**
 * The Clojure runtime calls Juno lowers, from the AOT-compiled code of a direct-linked namespace. A namespace's
 * {@code <clinit>} interns each var with literal arguments, the {@code gen-class} {@code main} reads the var and
 * applies it through {@code IFn}. Juno models a var as a handle to its function (the function's index in the
 * program's Clojure functions, or 0 when unbound), so those reads are plain words and {@code IFn.applyTo} is a
 * direct call to the one bound function.
 *
 * <p>Any other {@code clojure.*} call is rejected at link time, naming the method that is not yet supported.
 */
public final class ClojureSupport {
    private static final String PACKAGE = "clojure/lang/";
    private static final String CLOJURE = "clojure/";
    private static final String VAR = PACKAGE + "Var";
    private static final String IFN = PACKAGE + "IFn";
    private static final String RT = PACKAGE + "RT";
    private static final String UTIL = PACKAGE + "Util";
    private static final String NUMBERS = PACKAGE + "Numbers";
    private static final String I_TYPE = PACKAGE + "IType";
    private static final String OBJECT = "Ljava/lang/Object;";

    /** {@code Var.internPrivate(ns, name)}: interns a var; its arguments are literals in the {@code <clinit>}. */
    public static final MethodRef VAR_INTERN =
            new MethodRef(VAR, "internPrivate", "(Ljava/lang/String;Ljava/lang/String;)Lclojure/lang/Var;");
    public static final MethodRef VAR_IS_BOUND = new MethodRef(VAR, "isBound", "()Z");
    public static final MethodRef VAR_GET = new MethodRef(VAR, "get", "()" + OBJECT);
    public static final MethodRef VAR_GET_RAW_ROOT = new MethodRef(VAR, "getRawRoot", "()" + OBJECT);
    public static final MethodRef VAR_DEREF = new MethodRef(VAR, "deref", "()" + OBJECT);
    public static final MethodRef RT_SEQ = new MethodRef(RT, "seq", "(" + OBJECT + ")Lclojure/lang/ISeq;");
    /** The class-loading step of a namespace's {@code <clinit>}; Juno's loader does nothing here. */
    public static final MethodRef LOAD_WITH_CLASS =
            new MethodRef(UTIL, "loadWithClass", "(Ljava/lang/String;Ljava/lang/Class;)" + OBJECT);
    public static final MethodRef APPLY_TO = new MethodRef(IFN, "applyTo", "(Lclojure/lang/ISeq;)" + OBJECT);
    /**
     * {@code (int x)} and {@code (unchecked-int x)}: a long narrowed to an int. Juno does not emit the checked form's
     * overflow test, so the examples cast only literals that fit an int.
     */
    public static final MethodRef RT_INT_CAST = new MethodRef(RT, "intCast", "(J)I");
    public static final MethodRef RT_UNCHECKED_INT_CAST = new MethodRef(RT, "uncheckedIntCast", "(J)I");
    /** {@code (float x)} on a double: the {@code d2f} narrowing, unchecked as {@link #RT_INT_CAST} is. */
    public static final MethodRef RT_FLOAT_CAST = new MethodRef(RT, "floatCast", "(D)F");
    /** {@code (float x)} on a long, such as a literal: the {@code l2f} conversion. */
    public static final MethodRef RT_FLOAT_CAST_OF_LONG = new MethodRef(RT, "floatCast", "(J)F");

    /**
     * The {@code Numbers} arithmetic Clojure emits for primitive {@code long} operands. Overflow wraps as it does for
     * {@code LADD}; Clojure's own overflow check is not emitted, so the examples avoid overflowing values.
     */
    public static final MethodRef NUMBERS_ADD = new MethodRef(NUMBERS, "add", "(JJ)J");
    public static final MethodRef NUMBERS_MINUS = new MethodRef(NUMBERS, "minus", "(JJ)J");
    public static final MethodRef NUMBERS_MULTIPLY = new MethodRef(NUMBERS, "multiply", "(JJ)J");
    public static final MethodRef NUMBERS_QUOTIENT = new MethodRef(NUMBERS, "quotient", "(JJ)J");
    public static final MethodRef NUMBERS_REMAINDER = new MethodRef(NUMBERS, "remainder", "(JJ)J");
    public static final MethodRef NUMBERS_INC = new MethodRef(NUMBERS, "inc", "(J)J");
    public static final MethodRef NUMBERS_DEC = new MethodRef(NUMBERS, "dec", "(J)J");
    /** {@code (- x)}: the one-argument {@code Numbers.minus}, a negation. */
    public static final MethodRef NUMBERS_NEGATE = new MethodRef(NUMBERS, "minus", "(J)J");

    /** {@code Long.valueOf(long)}: how Clojure boxes a long constant, lowered only for an int-range literal. */
    public static final MethodRef LONG_VALUE_OF = new MethodRef("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
    /** {@code Numbers.int_array(size)}: {@code (int-array n)}; the size must be a literal. */
    public static final MethodRef NUMBERS_INT_ARRAY = new MethodRef(NUMBERS, "int_array", "(" + OBJECT + ")[I");
    /** {@code RT.aget(int[], int)} and {@code RT.aset(int[], int, int)}: {@code (aget a i)} and {@code (aset a i v)}. */
    public static final MethodRef RT_AGET = new MethodRef(RT, "aget", "([II)I");
    public static final MethodRef RT_ASET = new MethodRef(RT, "aset", "([III)I");

    /** The boolean comparisons Clojure emits for primitive {@code long} operands, such as {@code (< a b)} as a value. */
    public static final MethodRef NUMBERS_LT = new MethodRef(NUMBERS, "lt", "(JJ)Z");
    public static final MethodRef NUMBERS_LTE = new MethodRef(NUMBERS, "lte", "(JJ)Z");
    public static final MethodRef NUMBERS_GT = new MethodRef(NUMBERS, "gt", "(JJ)Z");
    public static final MethodRef NUMBERS_GTE = new MethodRef(NUMBERS, "gte", "(JJ)Z");
    public static final MethodRef NUMBERS_EQUIV = new MethodRef(NUMBERS, "equiv", "(JJ)Z");

    /** The calls {@link #isLowered} accepts: lowered in place of linking their bodies, or never reached. */
    private static final Set<MethodRef> LOWERED = Set.of(VAR_INTERN, VAR_IS_BOUND, VAR_GET, VAR_GET_RAW_ROOT,
            VAR_DEREF, RT_SEQ, LOAD_WITH_CLASS, APPLY_TO, RT_INT_CAST, RT_UNCHECKED_INT_CAST, RT_FLOAT_CAST, RT_FLOAT_CAST_OF_LONG, NUMBERS_ADD,
            NUMBERS_MINUS, NUMBERS_MULTIPLY, NUMBERS_QUOTIENT, NUMBERS_REMAINDER, NUMBERS_INC, NUMBERS_DEC, NUMBERS_NEGATE,
            NUMBERS_LT, NUMBERS_LTE, NUMBERS_GT, NUMBERS_GTE, NUMBERS_EQUIV, LONG_VALUE_OF, NUMBERS_INT_ARRAY,
            RT_AGET, RT_ASET);

    /** The internal name of a function's class: the munged namespace, then the munged var name after a {@code $}. */
    public static String functionClass(String namespace, String name) {
        return munge(namespace).replace('.', '/') + "$" + munge(name);
    }

    /** Whether {@code method} is lowered by {@code ClojureLowering} rather than linked from its bytecode. */
    public static boolean isLowered(MethodRef method) {
        return LOWERED.contains(method) || (method.owner().equals(IFN) && method.name().equals("applyTo"));
    }

    /** Rejects a call into the Clojure runtime that is neither lowered nor a function's own code. */
    public static void validateCall(MethodRef method) {
        if (method.owner().startsWith(CLOJURE) && !isLowered(method)) {
            throw new CompileException("Clojure runtime call not supported by Juno: "
                    + method.displayName());
        }
    }

    /** Whether {@code owner} is a class the Clojure compiler generated, from a function, namespace, or deftype. */
    public static boolean isClojureClass(JavaClass owner) {
        String superClassName = owner.superClassName();
        return (superClassName != null && superClassName.startsWith(PACKAGE))
                || owner.interfaces().contains(I_TYPE);
    }

    /**
     * Whether {@code method} is a {@code deftype} constructor whose parameters exactly mirror its instance fields.
     * Clojure erases reference fields and their constructor parameters to {@code Object}; the exact positional match
     * confines that erasure to storage owned by the final generated type instead of admitting {@code Object} in
     * arbitrary program methods.
     */
    public static boolean acceptsDeftypeConstructor(JavaClass owner, JavaMethod method,
            Set<String> referenceClassNames) {
        if (!method.name().equals("<init>") || !owner.isFinal() || !owner.interfaces().contains(I_TYPE)) {
            return false;
        }
        Descriptor descriptor = Descriptor.parse(method.descriptor());
        List<FieldInfo> fields = owner.fields().stream().filter(field -> !field.isStatic()).toList();
        if (!descriptor.returnsVoid() || descriptor.parameters().size() != fields.size()) {
            return false;
        }
        return IntStream.range(0, fields.size()).allMatch(index -> {
            String parameter = descriptor.parameters().get(index);
            return parameter.equals(fields.get(index).descriptor())
                    && (parameter.equals(OBJECT)
                            || Descriptor.parse("(" + parameter + ")V").usesOnlyV01Types(referenceClassNames));
        });
    }

    /**
     * The value of a {@code static final Object} field a Clojure class sets in its static initializer to a boxed
     * long literal ({@code ldc2_w; Long.valueOf; putstatic}), or empty when it is set to anything else. Clojure keeps
     * a literal constant this way, so reading it yields the literal: the size of {@code (int-array 5)}, for example.
     * Only an int-range literal is returned, since a boxed value is one word.
     */
    public static OptionalLong constantStaticField(JavaClass owner, String name, BytecodeDecoder decoder) {
        FieldInfo field = owner.findField(name, "Ljava/lang/Object;");
        JavaMethod initializer = owner.findMethod("<clinit>", "()V");
        if (field == null || !field.isStatic() || !field.isFinal() || !isClojureClass(owner) || initializer == null) {
            return OptionalLong.empty();
        }
        ConstantPool pool = owner.constantPool();
        List<Instruction> code = decoder.decode(initializer);
        for (int index = 0; index + 2 < code.size(); index++) {
            Instruction load = code.get(index);
            Instruction box = code.get(index + 1);
            Instruction store = code.get(index + 2);
            if (load.opcode() == LDC2_W && box.opcode() == INVOKESTATIC && store.opcode() == PUTSTATIC
                    && pool.methodRef(box.operandA()).equals(LONG_VALUE_OF)
                    && pool.fieldRef(store.operandA()).name().equals(name)) {
                long value = pool.longValue(load.operandA());
                return value == (int) value ? OptionalLong.of(value) : OptionalLong.empty();
            }
        }
        return OptionalLong.empty();
    }

    /** Whether a field of {@code owner} with {@code descriptor} holds a boxed Clojure value (an {@code Object}). */
    public static boolean isClojureObjectField(JavaClass owner, String descriptor) {
        return descriptor.equals(OBJECT) && isClojureClass(owner);
    }

    /** A Clojure value type (an {@code ISeq}, {@code IFn}, {@code Var} or other {@code clojure.lang} object). */
    public static boolean isHandleType(String descriptor) {
        return descriptor.startsWith("L" + PACKAGE) && descriptor.endsWith(";");
    }

    /**
     * Whether {@code descriptor} is acceptable on a function class: its parameters and return are Clojure values,
     * {@code Object} (how the AOT output types most results, including {@code nil}), or a primitive the function's
     * type hints name.
     */
    public static boolean acceptsFunctionDescriptor(@Nullable String superClassName, Descriptor descriptor) {
        return superClassName != null && superClassName.startsWith(PACKAGE)
                && descriptor.parameters().stream().allMatch(ClojureSupport::isValueOrPrimitive)
                && (isValueOrPrimitive(descriptor.returnType()) || descriptor.returnsVoid());
    }

    /** A Clojure value, or a primitive a function's typed arguments and results use ({@code ^long}, {@code ^double}). */
    private static boolean isValueOrPrimitive(String type) {
        return isValue(type) || Descriptor.isIntegerLike(type) || Descriptor.isLong(type) || Descriptor.isDouble(type);
    }

    /**
     * The function a namespace's {@code Var.internPrivate} at {@code index} binds: its {@code invokeStatic(ISeq)} in
     * the class the two literal arguments name. Empty when the interned name is not a function (such as
     * {@code equals} on a {@code gen-class}), or when the arguments are not literals.
     */
    public static Optional<MethodRef> boundFunction(MethodRef called, LinkedMethod linked, int index,
            Map<String, JavaClass> classes) {
        List<Instruction> instructions = linked.instructions();
        if (!called.equals(VAR_INTERN) || index < 2) {
            return Optional.empty();
        }
        String namespace = literalString(linked, instructions.get(index - 2));
        String name = literalString(linked, instructions.get(index - 1));
        if (namespace == null || name == null) {
            return Optional.empty();
        }
        JavaClass function = classes.get(functionClass(namespace, name));
        if (function == null) {
            return Optional.empty();
        }
        MethodRef applyTo = new MethodRef(function.name(), "invokeStatic", "(Lclojure/lang/ISeq;)" + OBJECT);
        return function.findMethod("invokeStatic", applyTo.descriptor()) == null ? Optional.empty()
                : Optional.of(applyTo);
    }

    /**
     * Juno has no {@code Class} objects, so a class literal is admitted only in a namespace's loader, which interns
     * vars and hands the literal to {@code Util.loadWithClass}. Anywhere else it fails the link.
     */
    public static void validateClassLiteral(LinkedMethod linked, Instruction instruction) {
        JavaClass owner = linked.owner();
        boolean classLiteral = (instruction.opcode() == LDC || instruction.opcode() == LDC_W)
                && owner.constantPool().isClass(instruction.operandA());
        if (classLiteral && linked.instructions().stream().noneMatch(internsVar(owner))) {
            throw new CompileException(linked.method().reference().displayName() + ": a class literal is supported "
                    + "only in a Clojure namespace's loader, not in user code");
        }
    }

    private static Predicate<Instruction> internsVar(JavaClass owner) {
        return instruction -> instruction.opcode() == INVOKESTATIC
                && owner.constantPool().methodRef(instruction.operandA()).equals(VAR_INTERN);
    }

    private static @Nullable String literalString(LinkedMethod linked, Instruction instruction) {
        if (instruction.opcode() != LDC && instruction.opcode() != LDC_W) {
            return null;
        }
        ConstantPool pool = linked.owner().constantPool();
        return pool.isString(instruction.operandA()) ? pool.string(instruction.operandA()) : null;
    }

    private static boolean isValue(String type) {
        return type.equals(OBJECT) || isHandleType(type);
    }

    private static String munge(String symbol) {
        StringBuilder munged = new StringBuilder();
        for (int index = 0; index < symbol.length(); index++) {
            switch (symbol.charAt(index)) {
                case '-' -> munged.append('_');
                case '?' -> munged.append("_QMARK_");
                case '!' -> munged.append("_BANG_");
                case '*' -> munged.append("_STAR_");
                case '+' -> munged.append("_PLUS_");
                case '>' -> munged.append("_GT_");
                case '<' -> munged.append("_LT_");
                case '=' -> munged.append("_EQ_");
                default -> munged.append(symbol.charAt(index));
            }
        }
        return munged.toString();
    }
}
