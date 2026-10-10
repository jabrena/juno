/* mps2-an386: code RAM at 0x0, data RAM at 0x20000000. QEMU's -kernel loader places both at their addresses. */
MEMORY
{
  CODE (rx)  : ORIGIN = 0x00000000, LENGTH = 2M
  RAM  (rwx) : ORIGIN = 0x20000000, LENGTH = 2M
}

ENTRY(Reset_Handler)

SECTIONS
{
  .text : {
    KEEP(*(.isr_vector))
    *(.text*)
    *(.rodata*)
    . = ALIGN(4);
  } > CODE

  .ARM.exidx : { *(.ARM.exidx*) } > CODE

  .data : { *(.data*) . = ALIGN(4); } > RAM

  .bss : {
    _sbss = .;
    *(.bss*)
    *(COMMON)
    . = ALIGN(4);
    _ebss = .;
  } > RAM

  _estack = ORIGIN(RAM) + LENGTH(RAM);
  PROVIDE(end = _ebss);
}
