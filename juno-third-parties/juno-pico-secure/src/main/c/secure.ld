/* Juno's Pico 2 W secure firmware: the first 12 KB of flash, the slot arduino-pico's OTA stage normally takes, so the
   non-secure image still starts at 0x10003000; and the top 8 KB of SRAM, which the image is linked to leave alone
   (build.ram_length=504k). The one non-secure-callable gateway sits at a fixed address at the end of the slot. */
MEMORY
{
    FLASH(rx)    : ORIGIN = 0x10000000, LENGTH = 0x2F00
    GATEWAY(rx)  : ORIGIN = 0x10002F00, LENGTH = 0x100
    RAM(rwx)     : ORIGIN = 0x2007E000, LENGTH = 0x2000
}

ENTRY(secure_reset)

SECTIONS
{
    .text : {
        KEEP(*(.vectors))
        KEEP(*(.image_def))
        *(.text*)
        *(.rodata*)
        . = ALIGN(4);
        __data_load = .;
    } > FLASH

    /* Code that runs while flash is out of XIP mode is copied to RAM with the data. */
    .data : AT(__data_load) {
        __data_start = .;
        *(.ramfunc*)
        *(.data*)
        . = ALIGN(4);
        __data_end = .;
    } > RAM

    .bss (NOLOAD) : {
        __bss_start = .;
        *(.bss*)
        *(COMMON)
        . = ALIGN(4);
        __bss_end = .;
    } > RAM

    .gateway : {
        KEEP(*(.gateway))
    } > GATEWAY

    __stack_top = ORIGIN(RAM) + LENGTH(RAM);
}
