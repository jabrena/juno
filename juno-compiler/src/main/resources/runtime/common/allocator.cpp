static uint8_t juno_arena[${JUNO_ARENA_CAPACITY}] __attribute__((aligned(8)));
static uint32_t juno_arena_used = 0;

// Conservative mark/sweep collector (Boehm-GC style: no type tags, no compaction —
// a conservative collector can't safely move objects since it can't tell a real
// pointer from an int that happens to match a heap address). Every arena block
// (allocated or free) has a 4-byte header immediately before the payload pointer
// callers see, so juno_alloc's return value never changes shape: bit 31 is the mark
// bit, bit 30 marks a free block, and the low 30 bits hold the block's padded
// payload size (always a multiple of 4, so every header stays 4-byte aligned and the
// block chain can be walked linearly). juno_gc_stack_top is written once, by the
// generated entry-point prologue, with the live sp at program start — see
// Thumb2AsmBackend.emitMethod; it bounds every future conservative stack scan. The
// generated static range is scanned too, so reference-valued static fields are roots.
extern "C" uintptr_t juno_gc_stack_top;
extern "C" uint8_t juno_gc_static_start;
extern "C" uint8_t juno_gc_static_end;
${JUNO_GC_THREAD_DECLARATION}
static constexpr uint32_t JUNO_GC_MARK_BIT = 0x80000000u;
static constexpr uint32_t JUNO_GC_FREE_BIT = 0x40000000u;
static constexpr uint32_t JUNO_GC_SIZE_MASK = 0x3FFFFFFFu;
static constexpr uint32_t JUNO_GC_NO_NEXT = 0xFFFFFFFFu;
static constexpr uint32_t JUNO_GC_MIN_PAYLOAD = 4u;
static constexpr uint32_t JUNO_GC_QUEUE_CAPACITY = 64u;

static uint32_t juno_gc_free_list_head = JUNO_GC_NO_NEXT;
static uint32_t juno_gc_queue[JUNO_GC_QUEUE_CAPACITY];
static uint32_t juno_gc_queue_count = 0;
static bool juno_gc_queue_overflowed = false;

static uint32_t juno_gc_round_up4(uint32_t value) {
  return (value + 3u) & ~3u;
}

static uint32_t* juno_gc_header_at(uint32_t offset) {
  return reinterpret_cast<uint32_t*>(&juno_arena[offset]);
}

static uint32_t juno_gc_block_size(uint32_t header) {
  return header & JUNO_GC_SIZE_MASK;
}

// A free block stores its free-list "next" offset in its own payload's first 4
// bytes — only possible when the payload is at least JUNO_GC_MIN_PAYLOAD bytes;
// smaller freed slivers are accepted v1 fragmentation and never linked.
static void juno_gc_push_free(uint32_t offset, uint32_t payload) {
  *juno_gc_header_at(offset) = payload | JUNO_GC_FREE_BIT;
  if (payload >= JUNO_GC_MIN_PAYLOAD) {
    *reinterpret_cast<uint32_t*>(&juno_arena[offset + 4u]) = juno_gc_free_list_head;
    juno_gc_free_list_head = offset;
  }
}

// First-fit search: unlinks and returns the offset of a free block whose payload is
// at least `padded` bytes, splitting off a remainder block when the leftover is worth
// keeping, or JUNO_GC_NO_NEXT if nothing free fits.
static uint32_t juno_gc_take_free(uint32_t padded) {
  uint32_t previous = JUNO_GC_NO_NEXT;
  uint32_t offset = juno_gc_free_list_head;
  while (offset != JUNO_GC_NO_NEXT) {
    uint32_t header = *juno_gc_header_at(offset);
    uint32_t payload = juno_gc_block_size(header);
    uint32_t next = *reinterpret_cast<uint32_t*>(&juno_arena[offset + 4u]);
    if (payload >= padded) {
      if (previous == JUNO_GC_NO_NEXT) {
        juno_gc_free_list_head = next;
      } else {
        *reinterpret_cast<uint32_t*>(&juno_arena[previous + 4u]) = next;
      }
      uint32_t leftover = payload - padded;
      if (leftover >= 4u + JUNO_GC_MIN_PAYLOAD) {
        *juno_gc_header_at(offset) = padded;
        juno_gc_push_free(offset + 4u + padded, leftover - 4u);
      } else {
        *juno_gc_header_at(offset) = payload;
      }
      return offset;
    }
    previous = offset;
    offset = next;
  }
  return JUNO_GC_NO_NEXT;
}

// Is `offset` really the start of a block in [0, juno_arena_used)? An O(n) linear
// chain walk rather than a precomputed bitmap: Juno's target programs (LED matrix
// animations, sensor loops) have small live-object counts, so this keeps the
// allocator simple — revisit only if real-hardware testing shows GC pauses are slow.
static bool juno_gc_is_block_start(uint32_t offset) {
  uint32_t cursor = 0;
  while (cursor < juno_arena_used) {
    if (cursor == offset) return true;
    if (cursor > offset) return false;
    cursor += 4u + juno_gc_block_size(*juno_gc_header_at(cursor));
  }
  return false;
}

static void juno_gc_enqueue(uint32_t offset) {
  if (juno_gc_queue_count < JUNO_GC_QUEUE_CAPACITY) {
    juno_gc_queue[juno_gc_queue_count++] = offset;
  } else {
    juno_gc_queue_overflowed = true;
  }
}

// Every word on the live stack, and every word of every marked object's payload, is
// a possible reference: with no type tags, the collector can't tell a real pointer
// from an int that happens to match a heap address, so it treats anything that lines
// up with a real, currently-allocated block's start as live.
static void juno_gc_mark_candidate(uintptr_t word) {
  uintptr_t base = reinterpret_cast<uintptr_t>(juno_arena);
  if (word < base + 4u || word >= base + juno_arena_used) return;
  uintptr_t relative = word - base;
  if ((relative & 3u) != 0u) return;
  uint32_t offset = static_cast<uint32_t>(relative) - 4u;
  if (!juno_gc_is_block_start(offset)) return;
  uint32_t header = *juno_gc_header_at(offset);
  if ((header & JUNO_GC_FREE_BIT) != 0u || (header & JUNO_GC_MARK_BIT) != 0u) return;
  *juno_gc_header_at(offset) = header | JUNO_GC_MARK_BIT;
  juno_gc_enqueue(offset);
}

// Reads `sizeof(uintptr_t)`-wide, natively-aligned words so this same collector code
// correctly recognizes real pointer values both on the 32-bit Cortex-M4 target and
// when this shim is compiled and executed natively (e.g. by host-side tests) on a
// 64-bit machine.
static void juno_gc_scan_range(const uint8_t* from, const uint8_t* to) {
  uintptr_t start = (reinterpret_cast<uintptr_t>(from) + sizeof(uintptr_t) - 1u)
      & ~(sizeof(uintptr_t) - 1u);
  uintptr_t end = reinterpret_cast<uintptr_t>(to);
  for (uintptr_t cursor = start; cursor + sizeof(uintptr_t) <= end;
       cursor += sizeof(uintptr_t)) {
    juno_gc_mark_candidate(*reinterpret_cast<const uintptr_t*>(cursor));
  }
}

static void juno_gc_scan_block_payload(uint32_t offset) {
  uint32_t payload = juno_gc_block_size(*juno_gc_header_at(offset));
  juno_gc_scan_range(&juno_arena[offset + 4u], &juno_arena[offset + 4u + payload]);
}

static void juno_gc_mark() {
  juno_gc_queue_count = 0;
  juno_gc_queue_overflowed = false;
  // The address of a local variable is a portable, widely-used stand-in for "the
  // current stack pointer" (the same technique Boehm-GC itself uses): it sits inside
  // this function's own frame, deeper than every live Java frame, so scanning from
  // here up to juno_gc_stack_top is always a safe superset of the true live stack.
  uint8_t stackMarker;
  juno_gc_scan_range(&juno_gc_static_start, &juno_gc_static_end);
  ${JUNO_GC_STACK_SCAN}
  uint32_t index = 0;
  while (index < juno_gc_queue_count) {
    juno_gc_scan_block_payload(juno_gc_queue[index]);
    index++;
  }
  // Bounded work-queue overflow is a safety net, not the common case: fall back to a
  // fixed-point rescan of the whole chain until a full pass finds nothing new.
  bool rescanning = juno_gc_queue_overflowed;
  while (rescanning) {
    juno_gc_queue_overflowed = false;
    bool changed = false;
    uint32_t cursor = 0;
    while (cursor < juno_arena_used) {
      uint32_t header = *juno_gc_header_at(cursor);
      uint32_t size = juno_gc_block_size(header);
      if ((header & JUNO_GC_FREE_BIT) == 0u && (header & JUNO_GC_MARK_BIT) != 0u) {
        uint32_t before = juno_gc_queue_count;
        juno_gc_scan_block_payload(cursor);
        while (before < juno_gc_queue_count) {
          juno_gc_scan_block_payload(juno_gc_queue[before]);
          before++;
        }
        if (juno_gc_queue_count > 0) changed = true;
        juno_gc_queue_count = 0;
      }
      cursor += 4u + size;
    }
    rescanning = juno_gc_queue_overflowed && changed;
  }
}

// Rebuilds the free list from scratch: walks the block chain in ascending address
// order, coalescing each run of free-or-unmarked blocks into one free-list node
// (adjacent dead blocks merge for free, since sweep already visits them in order),
// clearing mark bits on survivors, and handing a trailing dead run straight back to
// the bump allocator instead of free-listing it.
static void juno_gc_sweep() {
  juno_gc_free_list_head = JUNO_GC_NO_NEXT;
  uint32_t cursor = 0;
  uint32_t runStart = JUNO_GC_NO_NEXT;
  while (cursor < juno_arena_used) {
    uint32_t header = *juno_gc_header_at(cursor);
    uint32_t size = juno_gc_block_size(header);
    bool dead = (header & JUNO_GC_FREE_BIT) != 0u || (header & JUNO_GC_MARK_BIT) == 0u;
    if (dead) {
      if (runStart == JUNO_GC_NO_NEXT) runStart = cursor;
    } else {
      *juno_gc_header_at(cursor) = size;
      if (runStart != JUNO_GC_NO_NEXT) {
        juno_gc_push_free(runStart, cursor - runStart - 4u);
        runStart = JUNO_GC_NO_NEXT;
      }
    }
    cursor += 4u + size;
  }
  if (runStart != JUNO_GC_NO_NEXT) {
    juno_arena_used = runStart;
  }
}

static void juno_gc_collect() {
  ${JUNO_GC_LOG_BEFORE}
  juno_gc_mark();
  juno_gc_sweep();
  ${JUNO_GC_LOG_AFTER}
}

static void* juno_gc_try_allocate(uint32_t padded) {
  uint32_t offset = juno_gc_take_free(padded);
  if (offset == JUNO_GC_NO_NEXT) {
    if (juno_arena_used + 4u + padded > sizeof(juno_arena)) return nullptr;
    offset = juno_arena_used;
    *juno_gc_header_at(offset) = padded;
    juno_arena_used += 4u + padded;
  }
  return &juno_arena[offset + 4u];
}

extern "C" void* juno_alloc(uint32_t size, uint32_t alignment) {
  if (alignment > 4u) juno_panic();
  uint32_t padded = juno_gc_round_up4(size);
  void* memory = juno_gc_try_allocate(padded);
  if (memory == nullptr) {
    juno_gc_collect();
    memory = juno_gc_try_allocate(padded);
  }
  if (memory == nullptr) {
    // Unconditional (unlike --gc-log's per-collection lines): a panic is rare and
    // catastrophic, so the diagnostic cost is trivial next to the value of knowing
    // why the board just froze, even for programs that never opted into --gc-log.
    // A no-op if the program never called Serial.begin(), same as every other Serial
    // call site here.
    Serial.print("[juno-gc] OOM: need ");
    Serial.print(padded);
    Serial.print(" bytes, arena_used=");
    Serial.print(juno_arena_used);
    Serial.print("/");
    Serial.println(sizeof(juno_arena));
    juno_panic();
  }
  uint8_t* bytes = static_cast<uint8_t*>(memory);
  for (uint32_t index = 0; index < padded; index++) bytes[index] = 0;
  return memory;
}

