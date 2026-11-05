## Context

The existing conformance runners already compile the fixtures with OpenJDK and Juno, then compare serial output on
Thumb-2/QEMU, AVR/simavr, and Xtensa/Espressif-QEMU. This change adds three independent semantic categories using
that infrastructure; it does not broaden the bytecode subset.

## Decisions

- **Inheritance:** exercise constructor chaining, inherited state, overriding with `super`, and invocation through
  an interface. This isolates normal class/interface dispatch from the records, sealed, and lambda categories.
- **Generics:** exercise generic classes and generic methods using array values. javac emits erased descriptors and
  safe `checkcast` instructions at the use sites; Juno's existing reference-cast handling remains in scope, without
  adding collections or boxing.
- **Arrays:** exercise literals, primitive stores/loads, dimensions and length, then catch both low and high
  `ArrayIndexOutOfBoundsException` accesses. The exception catch is only the observable form of the array-bounds
  contract; broader exception unwinding stays owned by `exceptions/`.
- **Backend status:** every area is `REQUIRED` on the three established backends. Any mismatch remains a failure;
  this change does not silently reduce a claim.

## Risks

- Generic source uses erased `Object`-typed fields and compiler-inserted casts; unsupported library types and
  boxing are deliberately avoided.
- Array bounds checks pass through the optimizer and backend-specific memory lowering, so a failure may occur only
  on a particular backend; the shared transcript establishes the expected behavior.
