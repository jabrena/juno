// ---- The Portenta H7's two carriers: the block both cores share, and the hardware semaphores. ----
// The Cortex-M7 runs the program image; the Cortex-M4 runs Juno's small carrier image (generated next to the sketch),
// which executes the same Java code and runtime from the M7 image's flash. The two cores run separate Mbed OS
// kernels, so a core never calls the other's kernel: what one core needs from the other goes through this block.
// It sits at the start of D3 SRAM, below the Mbed core's OpenAMP region at 0x38000400, where both images find it
// without exchanging an address. The M7 does not cache D3 SRAM, so every word here is seen by both cores at once:
// it also holds the runtime state the two cores read without the runtime lock. The rest of RAM is write-through cached
// on the M7, which drops its cached copies wherever it may next read what the M4 wrote (juno_cache_acquire).
// The cores' exclusive monitors do not see each other's LDREX/STREX, so every lock between them is a hardware
// semaphore (HSEM), taken and released with a barrier on both sides.
static constexpr uint32_t JUNO_M4_LINK_MAGIC = 0x4A4D3404u;  // "JM4", layout version 4
static constexpr uint32_t JUNO_M4_SLOTS = ${JUNO_DUAL_CARRIER_SLOTS}u;
static constexpr uint32_t JUNO_M4_RING_SIZE = 32u;
static constexpr uint32_t JUNO_M4_OP_CREATE = 1u << 16;
static constexpr uint32_t JUNO_M4_OP_GIVE = 2u << 16;
static constexpr uint32_t JUNO_M4_OP_CALL = 3u << 16;       // To the M7: run the native call of an M4 thread.
static constexpr uint32_t JUNO_M4_OP_CALL_DONE = 4u << 16;  // To the M4: that call returned.
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

// A native call an M4 thread hands to the M7: the function and its AAPCS argument words, as the generated code set them
// up for a direct call, and its one- or two-word result. It lives on the calling thread's stack.
static constexpr uint32_t JUNO_NATIVE_STACK_WORDS = 16u;

struct JunoM4Call {
  uint32_t function;
  uint32_t registers[4];
  uint32_t stack[JUNO_NATIVE_STACK_WORDS];
  uint32_t result[2];
  uint32_t depth;             // The runtime lock depth the calling thread held, handed to the M7 for the call.
  volatile uint32_t killed;   // The thread was cancelled while its call ran.
  uint32_t padding;
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
  void (*volatile callWait)(uint32_t slot);  // Parks the calling M4 thread until its native call returns.
  volatile uint32_t calls[JUNO_M4_SLOTS];    // The JunoM4Call each M4 slot hands to the M7, as both cores see it.
  // Read by both cores without the runtime lock.
  volatile uint32_t gcInJava[2];             // carrier-gc.cpp: a carrier runs Java.
  volatile uint32_t gcParked[2];             // carrier-gc.cpp: a carrier is parked for a collection.
  volatile uint32_t gcStopping;              // carrier-gc.cpp: a collection stops the other carrier.
  volatile uint32_t runtimeOwner;            // The carrier holding the runtime lock, plus one; 0 when free.
  volatile uint32_t runtimeDepth;
  volatile uint32_t runtimeCore;             // The core holding the lock's hardware semaphore.
  volatile uint32_t runtimeLastCore;         // The core that last released it.
  volatile uint32_t slotCores[JUNO_M4_SLOTS];      // The carrier each slot's task is pinned to.
  volatile uint32_t slotCreated[JUNO_M4_SLOTS];
  volatile uint32_t slotKilled[JUNO_M4_SLOTS];
  volatile uint32_t slotDelegated[JUNO_M4_SLOTS];  // An M7 worker runs the slot's native call.
  volatile uint32_t killsPending;
  volatile uint32_t stacks[JUNO_M4_SLOTS];  // The stack of each slot's task, taken from the arena by the runtime.
  JunoM4Ring toM4;
  JunoM4Ring toM7;
};

static_assert(sizeof(JunoM4Link) <= 0x400u, "the link must stay below the Mbed core's OpenAMP region");

static JunoM4Link* const juno_m4_link = reinterpret_cast<JunoM4Link*>(0x38000000u);

// The core the calling code runs on: 0 is the Cortex-M7, 1 the Cortex-M4. The same code asks the core it runs on. A
// core's kernel, clock and hardware semaphores go by this; the runtime's carrier (juno_port_carrier_id) may differ.
static inline uint32_t juno_physical_core() {
  return ((SCB->CPUID >> SCB_CPUID_PARTNO_Pos) & 0xFFFu) == 0xC24u ? 1u : 0u;
}

// The HSEM's id of a core. Not the HAL's HSEM_CR_COREID_CURRENT: that is fixed when a file is compiled, and the M7
// image's code also runs on the M4.
static inline uint32_t juno_hsem_core(uint32_t core) {
  return core == 0u ? HSEM_CPU1_COREID : HSEM_CPU2_COREID;
}

// D2 SRAM is at 0x10000000 for the M4's own accesses and at 0x30000000 for every master: an address both cores can use.
static inline uint32_t juno_shared_address(const void* pointer) {
  uint32_t address = reinterpret_cast<uint32_t>(pointer);
  return address >= 0x10000000u && address < 0x10048000u ? address + 0x20000000u : address;
}

// One-step lock: reading RLR takes a free semaphore and returns its new owner.
static inline bool juno_hsem_try(uint32_t id, uint32_t core) {
  bool taken = HSEM->RLR[id] == (HSEM_RLR_LOCK | (juno_hsem_core(core) << HSEM_RLR_COREID_Pos));
  __DMB();
  return taken;
}

static inline void juno_hsem_release(uint32_t id, uint32_t core) {
  __DMB();
  HSEM->R[id] = juno_hsem_core(core) << HSEM_R_COREID_Pos;
}

// Interrupts the other core: releasing a semaphore raises the interrupt of every core that enabled it.
static void juno_hsem_notify(uint32_t id) {
  uint32_t core = juno_physical_core();
  while (!juno_hsem_try(id, core)) {
  }
  juno_hsem_release(id, core);
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
