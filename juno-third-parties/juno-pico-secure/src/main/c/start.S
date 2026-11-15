    .syntax unified
    .cpu cortex-m33
    .thumb

    /* Secure vector table. AIRCR.BFHFNMINS is set at start-up, so HardFault, BusFault and NMI go to the non-secure
       image's own handlers; what reaches these is a TrustZone violation or a fault in this firmware itself. */
    .section .vectors, "a"
    .align 2
    .global secure_vectors
secure_vectors:
    .word __stack_top
    .word secure_reset
    .word secure_fault          /* NMI */
    .word secure_fault          /* HardFault */
    .word secure_fault          /* MemManage */
    .word secure_fault          /* BusFault */
    .word secure_fault          /* UsageFault */
    .word secure_fault          /* SecureFault */
    .word 0, 0, 0
    .word secure_fault          /* SVCall */
    .word secure_fault          /* DebugMonitor */
    .word 0
    .word secure_fault          /* PendSV */
    .word secure_systick        /* SysTick */

    /* IMAGE_DEF block: a secure Arm executable for the RP2350, entered through the vector table above. */
    .section .image_def, "a"
    .align 2
image_def:
    .word 0xffffded3            /* PICOBIN_BLOCK_MARKER_START */
    .byte 0x42                  /* PICOBIN_BLOCK_ITEM_1BS_IMAGE_TYPE */
    .byte 0x01
    .hword 0x1021               /* EXE | SECURITY_S | CPU_ARM | CHIP_RP2350 */
    .byte 0xff                  /* PICOBIN_BLOCK_ITEM_2BS_LAST */
    .hword 0x0001
    .byte 0
    .word 0                     /* a single-block loop */
    .word 0xab123579            /* PICOBIN_BLOCK_MARKER_END */

    .text
    .thumb_func
    .global secure_fault
secure_fault:
    /* r0 = the frame the fault was stacked on, r1 = EXC_RETURN. */
    mov r1, lr
    tst r1, #0x40               /* EXC_RETURN.S: clear when the faulting code was non-secure */
    bne 1f
    tst r1, #0x04
    ite eq
    mrseq r0, msp_ns
    mrsne r0, psp_ns
    b secure_fault_c
1:
    tst r1, #0x04
    ite eq
    mrseq r0, msp
    mrsne r0, psp
    b secure_fault_c

    .thumb_func
    .global secure_systick
secure_systick:
#ifdef JUNO_SECURE_DIAGNOSTICS
    tst lr, #0x40
    bne 2f
    tst lr, #0x04
    ite eq
    mrseq r0, msp_ns
    mrsne r0, psp_ns
    b diagnostics_sample
2:
#endif
    bx lr

    /* The one non-secure-callable entry, at 0x10002F00. SG enters the secure state; the dispatcher runs on the secure
       stack; the scratch registers and flags are wiped so nothing secure leaks back; BXNS returns to the caller. */
    .section .gateway, "ax"
    .thumb_func
    .global juno_secure_gateway
juno_secure_gateway:
    sg
    push {r4, lr}
    bl juno_secure_call
    pop {r4, lr}
    movs r1, #0
    movs r2, #0
    movs r3, #0
    mov r12, r1
    msr APSR_nzcvq, r1
    bxns lr
