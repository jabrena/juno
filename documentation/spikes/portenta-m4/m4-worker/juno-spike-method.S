@ spike.SpikeMethod.compute(int), copied verbatim from Juno's Thumb-2 output for the Portenta H7 (juno_fn2),
@ with only its symbol renamed to juno_spike_method and made global so the M4 sketch can call it.
    .syntax unified
    .thumb
    .text
    .global juno_spike_method
    .type juno_spike_method, %function
juno_spike_method:
    push {r4-r11, lr}
    movs r12, #36
    sub sp, sp, r12
    str r0, [sp, #16]
.Ljuno_spike_methodblock0:
    movs r0, #0
    str r0, [sp, #20]
    movs r0, #1
    str r0, [sp, #24]
    b .Ljuno_spike_methodblock4
    .ltorg
.Ljuno_spike_methodblock4:
    ldr r0, [sp, #24]
    str r0, [sp, #4]
    ldr r0, [sp, #16]
    str r0, [sp, #8]
    ldr r0, [sp, #4]
    ldr r1, [sp, #8]
    cmp r0, r1
    ble .Lbranchfalse1
    b .Ljuno_spike_methodblock31
.Lbranchfalse1:
    b .Ljuno_spike_methodblock9
    .ltorg
.Ljuno_spike_methodblock9:
    ldr r0, [sp, #20]
    str r0, [sp, #0]
    ldr r0, [sp, #24]
    str r0, [sp, #4]
    movs r1, #3
    sdiv r2, r0, r1
    mls r0, r2, r1, r0
    str r0, [sp, #8]
    cmp r0, #0
    beq .Lbranchfalse2
    b .Ljuno_spike_methodblock22
.Lbranchfalse2:
    b .Ljuno_spike_methodblock16
    .ltorg
.Ljuno_spike_methodblock16:
    ldr r0, [sp, #24]
    str r0, [sp, #4]
    lsl r0, r0, #1
    str r0, [sp, #8]
    str r0, [sp, #28]
    b .Ljuno_spike_methodblock23
    .ltorg
.Ljuno_spike_methodblock22:
    ldr r0, [sp, #24]
    str r0, [sp, #4]
    str r0, [sp, #28]
    b .Ljuno_spike_methodblock23
    .ltorg
.Ljuno_spike_methodblock23:
    ldr r0, [sp, #28]
    str r0, [sp, #4]
    ldr r0, [sp, #0]
    ldr r1, [sp, #4]
    add r0, r0, r1
    str r0, [sp, #8]
    str r0, [sp, #20]
    ldr r0, [sp, #24]
    str r0, [sp, #4]
    add r0, r0, #1
    str r0, [sp, #8]
    str r0, [sp, #24]
    bl yield
    b .Ljuno_spike_methodblock4
    .ltorg
.Ljuno_spike_methodblock31:
    ldr r0, [sp, #20]
    str r0, [sp, #4]
    movs r12, #36
    add sp, sp, r12
    pop {r4-r11, pc}
    .ltorg
