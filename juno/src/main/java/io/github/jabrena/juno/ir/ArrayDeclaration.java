package io.github.jabrena.juno.ir;

/** One local array a method declares via {@link IrInstruction.NewArray}, hoisted to the top of the function. */
public record ArrayDeclaration(Value handle, ArrayElementType elementType, int length) {
}
