// Runs an AVR ELF under simavr (ATmega2560, 16 MHz) and copies everything the program writes to USART0 to stdout,
// byte for byte; simavr's own messages go to stderr. Exits when the program sleeps with interrupts off.
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <simavr/sim_avr.h>
#include <simavr/sim_elf.h>
#include <simavr/avr_uart.h>
static FILE* output;

static void uart_out(struct avr_irq_t* irq, uint32_t value, void* param) {
  (void) irq;
  (void) param;
  fputc((int) value, output);
}
int main(int argc, char** argv) {
  output = fdopen(dup(1), "w");
  dup2(2, 1);
  elf_firmware_t f = {0};
  if (elf_read_firmware(argv[1], &f)) { fprintf(stderr, "cannot load %s\n", argv[1]); return 2; }
  avr_t* avr = avr_make_mcu_by_name("atmega2560");
  avr_init(avr);
  avr->frequency = 16000000;
  avr_load_firmware(avr, &f);
  avr->log = 0;
  uint32_t flags = 0;
  avr_ioctl(avr, AVR_IOCTL_UART_GET_FLAGS('0'), &flags);
  flags &= ~AVR_UART_FLAG_STDIO;
  avr_ioctl(avr, AVR_IOCTL_UART_SET_FLAGS('0'), &flags);
  avr_irq_register_notify(avr_io_getirq(avr, AVR_IOCTL_UART_GETIRQ('0'), UART_IRQ_OUTPUT), uart_out, NULL);
  unsigned long long limit = argc > 2 ? strtoull(argv[2], 0, 10) : 4000000000ULL;
  for (;;) {
    int state = avr_run(avr);
    if (state == cpu_Done || state == cpu_Crashed) break;
    if (avr->cycle > limit) { fprintf(stderr, "[runner] cycle limit\n"); fflush(output); return 3; }
  }
  fflush(output);
  return avr->state == cpu_Crashed ? 4 : 0;
}
