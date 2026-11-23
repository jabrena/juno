// ---- The Portenta H7's two carriers: the block both cores share, and the hardware semaphores. ----
// The Cortex-M7 runs the program image; the Cortex-M4 runs Juno's small carrier image (generated next to the sketch),
// which executes the same Java code and runtime from the M7 image's flash. The two cores run separate Mbed OS
// kernels, so a core never calls the other's kernel: what one core needs from the other goes through this block.
// It sits at the start of D3 SRAM, below the Mbed core's OpenAMP region at 0x38000400, where both images find it
// without exchanging an address. The M7 runs with its data cache off, so every shared word is seen by both cores.
// The cores' exclusive monitors do not see each other's LDREX/STREX, so every lock between them is a hardware
// semaphore (HSEM), taken and released with a barrier on both sides.
static constexpr uint32_t JUNO_M4_LINK_MAGIC = 0x4A4D3401u;  // "JM4", layout version 1
static constexpr uint32_t JUNO_M4_SLOTS = ${JUNO_DUAL_CARRIER_SLOTS}u;
static constexpr uint32_t JUNO_M4_RING_SIZE = 32u;
static constexpr uint32_t JUNO_M4_OP_CREATE = 1u << 16;
static constexpr uint32_t JUNO_M4_OP_GIVE = 2u << 16;
static constexpr uint32_t JUNO_M4_OP_SLOT = 0xFFFFu;
// Hardware semaphores Juno owns; the HAL and the Mbed core use the low ones.
static constexpr uint32_t JUNO_HSEM_NOTIFY_M4 = 29u;  // Released by the M7 to interrupt the M4.
static constexpr uint32_t JUNO_HSEM_NOTIFY_M7 = 30u;  // Released by the M4 to interrupt the M7.
static constexpr uint32_t JUNO_HSEM_RUNTIME = 31u;    // The runtime lock both carriers share.

// One direction of requests: written only by Juno threads of one core, read only by the other core's service thread.
struct JunoM4Ring {
  volatile uint32_t head;
  volatile uint32_t tail;
  volatile uint32_t items[JUNO_M4_RING_SIZE];
};

struct JunoM4Link {
  volatile uint32_t magic;              // Written by the M7 once the block is initialized.
  volatile uint32_t m4Ready;            // Written by the M4 (JUNO_M4_LINK_MAGIC) once its services are in place.
  volatile uint32_t m7MillisAtStart;    // The M7's clock when it started the M4, so both clocks agree.
  volatile uint32_t m4CoreHz;           // The M4's core clock, for its slice counter.
  volatile uint32_t stackBytes;         // The stack of every carrier task.
  void (*volatile taskEntry)(uint32_t slot);  // M7 image: what every carrier task runs, on either core.
  // M4 image: the M4's own services, called only by code running on the M4.
  void (*volatile batonWait)(uint32_t slot);
  bool (*volatile batonTryTake)(uint32_t slot);
  void (*volatile batonGive)(uint32_t slot);
  void (*volatile createTask)(uint32_t slot);
  uint32_t (*volatile taskSlot)();
  unsigned long (*volatile millis)();
  void (*volatile sleepMillis)(unsigned long millisToSleep);
  void (*volatile yield)();
  volatile uint32_t stacks[JUNO_M4_SLOTS];  // The stack of each slot's task, taken from the arena by the runtime.
  JunoM4Ring toM4;
  JunoM4Ring toM7;
};

static JunoM4Link* const juno_m4_link = reinterpret_cast<JunoM4Link*>(0x38000000u);

// Carrier 0 is the Cortex-M7, carrier 1 the Cortex-M4: the same code asks the core it runs on.
extern "C" uint32_t juno_port_carrier_id() {
  return ((SCB->CPUID >> SCB_CPUID_PARTNO_Pos) & 0xFFFu) == 0xC24u ? 1u : 0u;
}

// The HSEM's id of the core running a carrier. Not the HAL's HSEM_CR_COREID_CURRENT: that is fixed when a file is
// compiled, and the M7 image's code also runs on the M4.
static inline uint32_t juno_hsem_core(uint32_t carrier) {
  return carrier == 0u ? HSEM_CPU1_COREID : HSEM_CPU2_COREID;
}

// One-step lock: reading RLR takes a free semaphore and returns its new owner.
static inline bool juno_hsem_try(uint32_t id, uint32_t carrier) {
  bool taken = HSEM->RLR[id] == (HSEM_RLR_LOCK | (juno_hsem_core(carrier) << HSEM_RLR_COREID_Pos));
  __DMB();
  return taken;
}

static inline void juno_hsem_release(uint32_t id, uint32_t carrier) {
  __DMB();
  HSEM->R[id] = juno_hsem_core(carrier) << HSEM_R_COREID_Pos;
}

// Interrupts the other core: releasing a semaphore raises the interrupt of every core that enabled it.
static void juno_hsem_notify(uint32_t id) {
  uint32_t carrier = juno_port_carrier_id();
  while (!juno_hsem_try(id, carrier)) {
  }
  juno_hsem_release(id, carrier);
}

static void juno_m4_ring_push(JunoM4Ring& ring, uint32_t item) {
  uint32_t head = ring.head;
  while (head - ring.tail >= JUNO_M4_RING_SIZE) {
    __DMB();
  }
  ring.items[head % JUNO_M4_RING_SIZE] = item;
  __DMB();
  ring.head = head + 1u;
  __DMB();
}

// Takes the next request, or returns false once the ring is empty.
static bool juno_m4_ring_pop(JunoM4Ring& ring, uint32_t* item) {
  uint32_t tail = ring.tail;
  __DMB();
  if (tail == ring.head) return false;
  __DMB();
  *item = ring.items[tail % JUNO_M4_RING_SIZE];
  __DMB();
  ring.tail = tail + 1u;
  return true;
}
