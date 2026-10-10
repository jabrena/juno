package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.BootstrapMethod;
import io.github.jabrena.juno.classfile.ConstantPool;
import io.github.jabrena.juno.classfile.InvokeDynamicRef;
import io.github.jabrena.juno.classfile.MethodHandleRef;
import io.github.jabrena.juno.classfile.MethodRef;

import java.util.ArrayList;
import java.util.List;

/** Validates and decodes the bounded subset of javac's string-concatenation bootstrap protocol. */
public final class StringConcatResolver {
    private static final MethodRef MAKE_CONCAT_WITH_CONSTANTS = new MethodRef(
            "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;");

    public boolean supports(LinkedMethod linked, Instruction instruction) {
        return bootstrapHandle(linked, instruction).method().equals(MAKE_CONCAT_WITH_CONSTANTS);
    }

    public StringConcatSite resolve(LinkedMethod linked, Instruction instruction) {
        ConstantPool pool = linked.owner().constantPool();
        InvokeDynamicRef dynamic = pool.invokeDynamic(instruction.operandA());
        MethodHandleRef bootstrapHandle = bootstrapHandle(linked, instruction);
        if (!bootstrapHandle.method().equals(MAKE_CONCAT_WITH_CONSTANTS)) {
            throw error(linked, instruction, "unsupported invokedynamic bootstrap "
                    + bootstrapHandle.method().displayName());
        }
        if (bootstrapHandle.referenceKind() != MethodHandleRef.REF_INVOKE_STATIC) {
            throw error(linked, instruction, "StringConcatFactory bootstrap must be invokestatic");
        }

        Descriptor descriptor = Descriptor.parse(dynamic.descriptor());
        if (!Descriptor.isString(descriptor.returnType())) {
            throw error(linked, instruction, "string-concat call site must return java.lang.String");
        }
        for (String type : descriptor.parameters()) {
            if (!isSupportedType(type)) {
                throw error(linked, instruction, "string concatenation does not support operand type " + type);
            }
        }

        BootstrapMethod bootstrap = linked.owner().bootstrapMethod(dynamic.bootstrapMethodIndex());
        if (bootstrap.argumentIndexes().isEmpty()) {
            throw error(linked, instruction, "makeConcatWithConstants requires a recipe argument");
        }
        String recipe = pool.string(bootstrap.argumentIndexes().getFirst());
        List<String> constants = new ArrayList<>();
        for (int index = 1; index < bootstrap.argumentIndexes().size(); index++) {
            constants.add(pool.string(bootstrap.argumentIndexes().get(index)));
        }
        return new StringConcatSite(descriptor.parameters(), parseRecipe(linked, instruction, recipe,
                descriptor.parameters(), constants));
    }

    private List<StringConcatSite.Part> parseRecipe(LinkedMethod linked, Instruction instruction, String recipe,
                                                     List<String> argumentTypes, List<String> constants) {
        List<StringConcatSite.Part> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int argumentIndex = 0;
        int constantIndex = 0;
        for (int index = 0; index < recipe.length(); index++) {
            char character = recipe.charAt(index);
            if (character == '\u0001') {
                flushLiteral(parts, literal);
                if (argumentIndex >= argumentTypes.size()) {
                    throw error(linked, instruction, "string-concat recipe has too many argument placeholders");
                }
                parts.add(new StringConcatSite.ArgumentPart(argumentIndex,
                        argumentTypes.get(argumentIndex)));
                argumentIndex++;
            } else if (character == '\u0002') {
                if (constantIndex >= constants.size()) {
                    throw error(linked, instruction, "string-concat recipe has too many constant placeholders");
                }
                literal.append(constants.get(constantIndex++));
            } else {
                literal.append(character);
            }
        }
        flushLiteral(parts, literal);
        if (argumentIndex != argumentTypes.size()) {
            throw error(linked, instruction, "string-concat recipe uses " + argumentIndex + " of "
                    + argumentTypes.size() + " dynamic arguments");
        }
        if (constantIndex != constants.size()) {
            throw error(linked, instruction, "string-concat recipe uses " + constantIndex + " of "
                    + constants.size() + " constants");
        }
        return List.copyOf(parts);
    }

    private void flushLiteral(List<StringConcatSite.Part> parts, StringBuilder literal) {
        if (!literal.isEmpty()) {
            parts.add(new StringConcatSite.LiteralPart(literal.toString()));
            literal.setLength(0);
        }
    }

    private boolean isSupportedType(String type) {
        return Descriptor.isString(type) || type.equals("Z") || type.equals("B") || type.equals("C")
                || type.equals("S") || type.equals("I") || Descriptor.isLong(type)
                || Descriptor.isFloat(type) || Descriptor.isDouble(type);
    }

    private MethodHandleRef bootstrapHandle(LinkedMethod linked, Instruction instruction) {
        ConstantPool pool = linked.owner().constantPool();
        InvokeDynamicRef dynamic = pool.invokeDynamic(instruction.operandA());
        BootstrapMethod bootstrap = linked.owner().bootstrapMethod(dynamic.bootstrapMethodIndex());
        return pool.methodHandle(bootstrap.methodHandleIndex());
    }

    private CompileException error(LinkedMethod linked, Instruction instruction, String detail) {
        return new CompileException(linked.method().reference().displayName() + " at bytecode offset "
                + instruction.offset() + ": " + detail);
    }
}
