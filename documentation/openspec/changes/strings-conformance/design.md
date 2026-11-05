## Context and decision

The conformance runners already compare serial output from the same source under OpenJDK, Thumb-2/QEMU,
AVR/simavr, and Xtensa/Espressif-QEMU. `StringSemantics` uses only existing string-concatenation lowering and the
supported `String.valueOf(int)` and `String.valueOf(double)` intrinsics, so it adds coverage without expanding the
supported bytecode subset.

The fixture includes each primitive concat shape supported by the lowering, a null `String` reference (whose
conversion is observable as `null`), and the two direct conversion intrinsics. More extensive `StringBuilder`,
collections, and JDK-library behavior remains outside this category.

All three backends are `REQUIRED`; a mismatch must remain visible as a conformance failure.
