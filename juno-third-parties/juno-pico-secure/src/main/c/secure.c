// Juno secure firmware for the Raspberry Pi Pico 2 W (RP2350, Arm cores).
//
// The boot ROM starts it in the secure state. It fences off its own flash, the secret-store sector and the top 8 KB of
// SRAM with the SAU, keeps the OTP fuses, the flash controller and the QSPI pins to itself, hands everything else to the
// non-secure world, and starts the arduino-pico image at 0x10003000 non-secure. From then on the image reaches the
// store only through the gateway at 0x10002F00: juno_secure_call(op, a, b, c).
//
// Core 1 is started too, into a secure relay that gives it the same fence and then waits on the non-secure
// inter-core FIFO for the launch handshake the boot ROM would answer: the image's own multicore_launch_core1 then
// starts its second core non-secure, as it would without this firmware.
//
// The board is never locked: no OTP fuse is written, secure boot stays off and debug stays on. The fence keeps the
// secrets from the program running on the board, not from someone holding the board and a USB cable.
#include <arm_cmse.h>
#include <stdint.h>

#define REG(address) (*(volatile uint32_t *)(address))

// Flash and SRAM layout, shared with the non-secure image's build.
#define APP_VECTORS        0x10003000u
#define STORE_SECTOR       0x103FE000u
#define STORE_OFFSET       0x003FE000u
#define EEPROM_SECTOR      0x103FF000u
#define FLASH_END          0x10400000u
#define NOCACHE_ALIAS      0x04000000u  // XIP_NOCACHE_NOALLOC_BASE - XIP_BASE
#define SECURE_RAM         0x2007E000u
#define SECTOR_SIZE        4096u
#define FLASH_PAGE         256u

// The store: 16 slots of 64 bytes at the start of the sector, a length byte and up to 63 value bytes each. An erased
// slot reads 0xFF, which is what "missing" looks like.
#define SLOT_COUNT         16u
#define SLOT_SIZE          64u
#define MAX_VALUE_BYTES    63u
#define STORE_BYTES        (SLOT_COUNT * SLOT_SIZE)

// Gateway operations and answers.
#define OP_VERSION         0u
#define OP_READ            1u
#define OP_WRITE           2u
#define OP_CLEAR           3u
#define OP_CLEAR_ALL       4u
#define OP_FLASH_ERASE     5u
#define OP_FLASH_PROGRAM   6u
#define FIRMWARE_MAGIC     0x4A554E4F  // "JUNO"

// Core registers.
#define SAU_CTRL           0xE000EDD0u
#define SAU_RNR            0xE000EDD8u
#define SAU_RBAR           0xE000EDDCu
#define SAU_RLAR           0xE000EDE0u
#define SAU_SFSR           0xE000EDE4u
#define SAU_SFAR           0xE000EDE8u
#define SCB_AIRCR          0xE000ED0Cu
#define SCB_ICSR           0xE000ED04u
#define SCB_SHCSR          0xE000ED24u
#define SCB_NSACR          0xE000ED8Cu
#define SCB_VTOR_NS        0xE002ED08u
#define NVIC_ITNS0         0xE000E380u

// Peripheral registers.
#define ACCESSCTRL         0x40060000u
#define ACCESSCTRL_PASS    0xACCE0000u
#define ACCESS_ALL         0xFFu        // both worlds, every bus master
#define ACCESS_SECURE      0xFCu        // secure only, every bus master
#define RESETS_CLR         0x40023000u
#define RESETS_DONE        0x40020008u
#define RESET_DMA          (1u << 2)
#define RESET_JTAG         (1u << 8)
#define DMA_BASE           0x50000000u
#define PADS_QSPI_IO       0x40040004u
#define QMI_M1             0x400D0020u
#define BOOTRAM            0x400E0000u
#define WATCHDOG_CTRL      0x400D8000u
#define WATCHDOG_SCRATCH   0x400D800Cu
#define PSM_FRCE_OFF       0x40018004u
#define PSM_PROC1          (1u << 24)
#define SIO_SECURE         0xD0000000u  // this world's SIO bank: the FIFO core 1's boot ROM listens on
#define SIO_NONSECURE      0xD0020000u  // the image's SIO bank, as the secure world reaches it
#define SIO_FIFO_ST        0x050u
#define SIO_FIFO_WR        0x054u
#define SIO_FIFO_RD        0x058u
#define FIFO_VLD           0x1u
#define FIFO_RDY           0x2u

extern uint32_t __data_load, __data_start, __data_end, __bss_start, __bss_end;
extern const uint32_t secure_vectors[];

typedef void *(*rom_table_lookup_fn)(uint32_t code, uint32_t mask);

static void *rom_lookup(char a, char b) {
    const volatile uint16_t *entry;
    __asm volatile("" : "=r"(entry) : "0"(0x16u));  // the ROM's table-lookup pointer lives at address 0x16
    rom_table_lookup_fn lookup = (rom_table_lookup_fn)(uintptr_t)*entry;
    return lookup((uint32_t)a | ((uint32_t)b << 8), 0x0004u /* RT_FLAG_FUNC_ARM_SEC */);
}

// ---- Fences ---------------------------------------------------------------------------------------------------

static void sau_region(uint32_t number, uint32_t first, uint32_t last, uint32_t callable) {
    REG(SAU_RNR) = number;
    REG(SAU_RBAR) = first & ~0x1Fu;
    REG(SAU_RLAR) = (last & ~0x1Fu) | (callable ? 2u : 0u) | 1u;
}

static void dma_mpu_secure(uint32_t number, uint32_t first, uint32_t last) {
    REG(DMA_BASE + 0x504u + 8u * number) = first & ~0x1Fu;
    REG(DMA_BASE + 0x508u + 8u * number) = (last & ~0x1Fu) | 0x4u /* S */ | 0x1u /* EN */;
}

static void set_access(uint32_t offset, uint32_t bits) {
    REG(ACCESSCTRL + offset) = ACCESSCTRL_PASS | bits;
}

// What each core holds of its own: its SAU, which interrupts and coprocessors are the image's, and which faults stay
// here. Core 0 sets it in fence(), core 1 in its relay, before either runs any of the image.
static void fence_core(void) {
    // The CPU's view: only what is listed here is non-secure; everything else, this firmware's flash, the store
    // sector, every flash alias above 4 MB, the maintenance aliases and the top 8 KB of SRAM, stays secure.
    sau_region(0, APP_VECTORS, STORE_SECTOR - 1u, 0);
    sau_region(1, EEPROM_SECTOR, FLASH_END - 1u, 0);
    sau_region(2, APP_VECTORS + NOCACHE_ALIAS, STORE_SECTOR + NOCACHE_ALIAS - 1u, 0);
    sau_region(3, EEPROM_SECTOR + NOCACHE_ALIAS, FLASH_END + NOCACHE_ALIAS - 1u, 0);
    sau_region(4, 0x20000000u, SECURE_RAM - 1u, 0);
    sau_region(5, 0x20080000u, 0x20082000u - 1u, 0);
    sau_region(6, 0x10002F00u, APP_VECTORS - 1u, 1);
    REG(SAU_CTRL) = 1u;

    // Interrupts and coprocessors (GPIO, double, FPU) belong to the image. HardFault, BusFault and NMI stay with this
    // firmware (AIRCR.BFHFNMINS clear), so a crash in the image reboots into BOOTSEL and the board stays reflashable
    // without pressing its button.
    REG(NVIC_ITNS0) = 0xFFFFFFFFu;
    REG(NVIC_ITNS0 + 4u) = 0xFFFFFFFFu;
    REG(SCB_NSACR) = 0x00000CFFu;
    REG(SCB_SHCSR) |= (1u << 19) | (1u << 18) | (1u << 16);              // SecureFault, UsageFault, MemManage
}

static void fence(void) {
    fence_core();

    // The peripherals: all to both worlds except the OTP fuses, and the flash controller, the XIP cache and stream,
    // the QSPI pads and pins, any of which would let code reach the flash past the SAU.
    for (uint32_t offset = 0x14u; offset <= 0xE8u; offset += 4u) {
        set_access(offset, ACCESS_ALL);
    }
    set_access(0xA8u, ACCESS_SECURE);  // OTP
    set_access(0xE0u, ACCESS_SECURE);  // XIP_CTRL
    set_access(0xE4u, ACCESS_SECURE);  // XIP_QMI
    set_access(0xE8u, ACCESS_SECURE);  // XIP_AUX
    set_access(0x6Cu, ACCESS_SECURE);  // IO_BANK1, the QSPI pins
    set_access(0x74u, ACCESS_SECURE);  // PADS_QSPI
    REG(ACCESSCTRL + 0x0Cu) = 0xFFFFFFFFu;  // GPIO_NSMASK0: GPIO 0-31 through SIO
    REG(ACCESSCTRL + 0x10u) = 0x0300FFFFu;  // GPIO_NSMASK1: GPIO 32-47 and USB, not the QSPI pins

    // DMA: out of reset, every channel and IRQ handed over, and its MPU keeping DMA away from the same secure areas.
    // JTAG's reset only this world can release; the non-secure image's start-up skips both, as their reset is ours.
    REG(RESETS_CLR) = RESET_DMA | RESET_JTAG;
    while ((REG(RESETS_DONE) & (RESET_DMA | RESET_JTAG)) != (RESET_DMA | RESET_JTAG)) {
    }
    for (uint32_t channel = 0; channel < 16u; channel++) {
        REG(DMA_BASE + 0x480u + 4u * channel) = 0u;
    }
    for (uint32_t irq = 0; irq < 4u; irq++) {
        REG(DMA_BASE + 0x4C0u + 4u * irq) = 0u;
    }
    REG(DMA_BASE + 0x4D0u) = 0u;
    dma_mpu_secure(0, 0x10000000u, APP_VECTORS - 1u);
    dma_mpu_secure(1, STORE_SECTOR, EEPROM_SECTOR - 1u);
    dma_mpu_secure(2, FLASH_END, 0x13FFFFFFu);
    dma_mpu_secure(3, 0x10000000u + NOCACHE_ALIAS, APP_VECTORS + NOCACHE_ALIAS - 1u);
    dma_mpu_secure(4, STORE_SECTOR + NOCACHE_ALIAS, EEPROM_SECTOR + NOCACHE_ALIAS - 1u);
    dma_mpu_secure(5, FLASH_END + NOCACHE_ALIAS, 0x1FFFFFFFu);
    dma_mpu_secure(6, SECURE_RAM, 0x20080000u - 1u);
    dma_mpu_secure(7, BOOTRAM, BOOTRAM + 0xFFFu);
    REG(DMA_BASE + 0x500u) = 0x8u;  // NS_HIDE_ADDR; addresses outside the regions are non-secure
}

static void hand_over_rom(void) {
    typedef int (*state_reset_fn)(uint32_t flags);
    typedef int (*set_ns_api_permission_fn)(uint32_t api, int allowed);
    // The reset the image's own runtime would do, done here, where the ROM allows it.
    ((state_reset_fn)rom_lookup('S', 'R'))(0x5u /* CURRENT_CORE | GLOBAL_STATE */);
    set_ns_api_permission_fn permit = (set_ns_api_permission_fn)rom_lookup('S', 'P');
    for (uint32_t api = 0; api < 8u; api++) {
        // Everything but otp_access (5) and checked_flash_op (1), which would reach the store sector.
        permit(api, api != 1u && api != 5u);
    }
}

// ---- Flash ------------------------------------------------------------------------------------------------------

typedef struct {
    void (*connect_internal_flash)(void);
    void (*flash_exit_xip)(void);
    void (*flash_range_erase)(uint32_t offset, uint32_t count, uint32_t block_size, uint8_t block_command);
    void (*flash_range_program)(uint32_t offset, const uint8_t *data, uint32_t count);
    void (*flash_flush_cache)(void);
} flash_rom_t;

static uint32_t boot2_copy[64];
static uint8_t image[STORE_BYTES];
static uint8_t page[FLASH_PAGE];

// Runs from RAM: flash is out of XIP mode for the whole call. The sequence is the Pico SDK's for the RP2350: save the
// QSPI pads and the CS1 window, erase or program through the ROM, flush, restore, and re-enter XIP through the copy of
// the boot ROM's XIP set-up taken before the flash went away. A null `data` erases [offset, offset + count).
static void __attribute__((section(".ramfunc"), noinline)) flash_op(const flash_rom_t *rom, uint32_t offset,
                                                                   const uint8_t *data, uint32_t count) {
    uint32_t pads[6];
    for (uint32_t i = 0; i < 6u; i++) {
        pads[i] = REG(PADS_QSPI_IO + 4u * i);
    }
    uint32_t timing = REG(QMI_M1);
    uint32_t rfmt = REG(QMI_M1 + 4u);
    uint32_t rcmd = REG(QMI_M1 + 8u);
    rom->connect_internal_flash();
    rom->flash_exit_xip();
    if (data == 0) {
        rom->flash_range_erase(offset, count, 1u << 16, 0xD8u);
    } else {
        rom->flash_range_program(offset, data, count);
    }
    rom->flash_flush_cache();
    for (uint32_t i = 0; i < 6u; i++) {
        REG(PADS_QSPI_IO + 4u * i) = pads[i];
    }
    REG(QMI_M1) = timing;
    REG(QMI_M1 + 4u) = rfmt;
    REG(QMI_M1 + 8u) = rcmd;
    ((void (*)(void))((uintptr_t)boot2_copy + 1u))();
}

/** Erases (data null) or programs flash at offset, with both worlds' interrupts masked for the duration. */
static void flash(uint32_t offset, const uint8_t *data, uint32_t count) {
    flash_rom_t rom = {
            (void (*)(void))rom_lookup('I', 'F'),
            (void (*)(void))rom_lookup('E', 'X'),
            (void (*)(uint32_t, uint32_t, uint32_t, uint8_t))rom_lookup('R', 'E'),
            (void (*)(uint32_t, const uint8_t *, uint32_t))rom_lookup('R', 'P'),
            (void (*)(void))rom_lookup('F', 'C'),
    };
    for (uint32_t i = 0; i < 64u; i++) {
        boot2_copy[i] = REG(BOOTRAM + 4u * i);
    }
    uint32_t primask;
    __asm volatile("mrs %0, primask\n cpsid i" : "=r"(primask) : : "memory");
    flash_op(&rom, offset, data, count);
    __asm volatile("msr primask, %0" : : "r"(primask) : "memory");
}

static void store_image(uint32_t length) {
    flash(STORE_OFFSET, 0, SECTOR_SIZE);
    if (length != 0u) {
        flash(STORE_OFFSET, image, length);
    }
}

static const volatile uint8_t *slot_in_flash(uint32_t slot) {
    return (const volatile uint8_t *)(STORE_SECTOR + SLOT_SIZE * slot);
}

/** Rewrites the sector from image[], unless it already holds exactly that; an unchanged write costs no erase. */
static void commit(void) {
    const volatile uint8_t *flash = slot_in_flash(0);
    int blank = 1;
    int same = 1;
    for (uint32_t i = 0; i < STORE_BYTES; i++) {
        same &= flash[i] == image[i];
        blank &= image[i] == 0xFFu;
    }
    if (!same) {
        store_image(blank ? 0u : STORE_BYTES);
    }
}

static void load_image(void) {
    const volatile uint8_t *flash = slot_in_flash(0);
    for (uint32_t i = 0; i < STORE_BYTES; i++) {
        image[i] = flash[i];
    }
}

// ---- Gateway ----------------------------------------------------------------------------------------------------

/** Whether the non-secure caller may itself read (or write) all of [address, address + length). */
static int caller_may_access(uint32_t address, uint32_t length, int write) {
    if (length == 0u) {
        return 1;
    }
    if (address + length < address) {
        return 0;
    }
    for (uint32_t probe = address & ~0x1Fu; probe < address + length; probe += 32u) {
        cmse_address_info_t info = cmse_TTA((void *)probe);
        if (!(write ? info.flags.nonsecure_readwrite_ok : info.flags.nonsecure_read_ok)) {
            return 0;
        }
    }
    cmse_address_info_t last = cmse_TTA((void *)(address + length - 1u));
    return write ? last.flags.nonsecure_readwrite_ok : last.flags.nonsecure_read_ok;
}

static int read_slot(uint32_t slot, uint32_t buffer, uint32_t capacity) {
    if (slot >= SLOT_COUNT) {
        return -1;
    }
    const volatile uint8_t *stored = slot_in_flash(slot);
    uint32_t length = stored[0];
    if (length == 0u || length > MAX_VALUE_BYTES || length > capacity
            || !caller_may_access(buffer, length, 1)) {
        return -1;
    }
    volatile uint8_t *target = (volatile uint8_t *)buffer;
    for (uint32_t i = 0; i < length; i++) {
        target[i] = stored[1u + i];
    }
    return (int)length;
}

static int write_slot(uint32_t slot, uint32_t value, uint32_t length) {
    if (slot >= SLOT_COUNT || length > MAX_VALUE_BYTES || !caller_may_access(value, length, 0)) {
        return -1;
    }
    load_image();
    uint8_t *record = image + SLOT_SIZE * slot;
    const volatile uint8_t *source = (const volatile uint8_t *)value;
    record[0] = length == 0u ? 0xFFu : (uint8_t)length;
    for (uint32_t i = 0; i < MAX_VALUE_BYTES; i++) {
        record[1u + i] = i < length ? source[i] : 0xFFu;
    }
    commit();
    return (int)length;
}

static int clear_all(void) {
    for (uint32_t i = 0; i < STORE_BYTES; i++) {
        image[i] = 0xFFu;
    }
    commit();
    return 0;
}

// ---- Flash service --------------------------------------------------------------------------------------------
// The image cannot erase or program flash itself: that needs the flash controller, the boot RAM and boot-ROM calls
// only this world has. It asks here instead (Juno's runtime wraps the SDK's flash_range_erase and flash_range_program,
// which BTstack's bond storage and the EEPROM library use), and only for flash it owns: its own image area and the
// EEPROM sector, never this firmware's slot or the store.

/** Whether [offset, offset + count) lies wholly in flash the non-secure image owns. */
static int image_owns(uint32_t offset, uint32_t count) {
    uint32_t start = offset;
    uint32_t end = offset + count;
    if (count == 0u || end < start) {
        return 0;
    }
    uint32_t app_start = APP_VECTORS - 0x10000000u;
    uint32_t store_start = STORE_OFFSET;
    uint32_t eeprom_start = EEPROM_SECTOR - 0x10000000u;
    uint32_t flash_end = FLASH_END - 0x10000000u;
    return (start >= app_start && end <= store_start) || (start >= eeprom_start && end <= flash_end);
}

static int image_flash_erase(uint32_t offset, uint32_t count) {
    if (offset % SECTOR_SIZE != 0u || count % SECTOR_SIZE != 0u || !image_owns(offset, count)) {
        return -1;
    }
    flash(offset, 0, count);
    return 0;
}

/** Programs page by page from a secure copy, so the source may be anywhere the caller can read, flash included. */
static int image_flash_program(uint32_t offset, uint32_t data, uint32_t count) {
    if (offset % FLASH_PAGE != 0u || count % FLASH_PAGE != 0u || !image_owns(offset, count)
            || !caller_may_access(data, count, 0)) {
        return -1;
    }
    for (uint32_t done = 0; done < count; done += FLASH_PAGE) {
        const volatile uint8_t *source = (const volatile uint8_t *)(data + done);
        for (uint32_t i = 0; i < FLASH_PAGE; i++) {
            page[i] = source[i];
        }
        flash(offset + done, page, FLASH_PAGE);
    }
    return 0;
}

// Both cores may call the gateway: one call at a time, since the store image, the page buffer and the flash controller
// are shared. A word in secure SRAM taken with LDAEX/STREX, as the SDK's own RP2350 spin locks are.
static volatile uint32_t gateway_lock;

static int dispatch(uint32_t op, uint32_t a, uint32_t b, uint32_t c);

int juno_secure_call(uint32_t op, uint32_t a, uint32_t b, uint32_t c) {
    while (__atomic_exchange_n(&gateway_lock, 1u, __ATOMIC_ACQUIRE) != 0u) {
    }
    int result = dispatch(op, a, b, c);
    __atomic_store_n(&gateway_lock, 0u, __ATOMIC_RELEASE);
    return result;
}

static int dispatch(uint32_t op, uint32_t a, uint32_t b, uint32_t c) {
    switch (op) {
        case OP_VERSION:
            return FIRMWARE_MAGIC;
        case OP_READ:
            return read_slot(a, b, c);
        case OP_WRITE:
            return write_slot(a, b, c);
        case OP_CLEAR:
            return write_slot(a, 0u, 0u) == 0 ? 0 : -1;
        case OP_CLEAR_ALL:
            return clear_all();
        case OP_FLASH_ERASE:
            return image_flash_erase(a, b);
        case OP_FLASH_PROGRAM:
            return image_flash_program(a, b, c);
        default:
            return -1;
    }
}

// ---- Start-up and faults ----------------------------------------------------------------------------------------

/**
 * A crash in the image, a TrustZone violation, or a fault in this firmware. The record goes to the watchdog scratch registers (kind and
 * exception, faulting world, stacked PC, SFAR or CFSR) and the board restarts into BOOTSEL, so it can be reflashed;
 * it never falls back to running the image secure, which would be a way around the fence.
 */
void secure_fault_c(uint32_t *frame, uint32_t exc_return) {
    uint32_t sfsr = REG(SAU_SFSR);
    REG(WATCHDOG_SCRATCH + 0u) = 0x5EC00000u | ((sfsr & 0x7Fu) << 8) | (REG(SCB_ICSR) & 0xFFu);
    REG(WATCHDOG_SCRATCH + 4u) = (REG(WATCHDOG_SCRATCH + 4u) & 0xF0000000u) | ((exc_return & 0x40u) == 0u ? 1u : 0u);
    REG(WATCHDOG_SCRATCH + 8u) = frame[6];
    REG(WATCHDOG_SCRATCH + 12u) = (sfsr & 0x40u) != 0u ? REG(SAU_SFAR)
            : REG((exc_return & 0x40u) == 0u ? 0xE002ED28u : 0xE000ED28u);
#ifdef JUNO_SECURE_DIAGNOSTICS
    REG(WATCHDOG_SCRATCH + 0u) = frame[6];
    REG(WATCHDOG_SCRATCH + 4u) = 0xA0000000u | ((sfsr & 0xFFu) << 8) | (REG(SCB_ICSR) & 0xFFu);
    REG(WATCHDOG_CTRL) |= 0x80000000u;
#endif
#ifdef JUNO_SECURE_FAULT_PC
    // Development only: the faulting PC and which core took the fault, in the two scratch registers that survive.
    REG(WATCHDOG_SCRATCH + 0u) = frame[6];
    REG(WATCHDOG_SCRATCH + 4u) = (REG(SIO_SECURE) << 31) | ((exc_return & 0xFFu) << 16) | ((sfsr & 0xFFu) << 8)
            | (REG(SCB_ICSR) & 0xFFu);
#endif
    typedef int (*reboot_fn)(uint32_t flags, uint32_t delay_ms, uint32_t p0, uint32_t p1);
    ((reboot_fn)rom_lookup('R', 'B'))(0x2u /* REBOOT2_FLAG_REBOOT_TYPE_BOOTSEL */, 10u, 0u, 0u);
    for (;;) {
    }
}

#ifdef JUNO_SECURE_DIAGNOSTICS
// Development only, never in the shipped image: a watchdog that reboots a hung start into an ordinary secure start of
// the image, with the fault or hang recorded and the non-secure PC sampled from SysTick, so a probe sketch can print
// where it stopped. Running the image secure is exactly what the fence must not allow, hence the separate build.
static void diagnostics_arm(void) {
    REG(WATCHDOG_SCRATCH + 0u) = 0x5EC20000u;
    REG(WATCHDOG_SCRATCH + 4u) = 0xA0000000u;
    REG(0x40018008u) = 0x01FFFFFFu & ~0xCu;  // PSM_WDSEL: everything but the oscillators
#ifndef JUNO_DIAG_NO_WATCHDOG
    REG(0x40108034u) = 12u;                  // TICKS watchdog cycles
    REG(0x40108030u) = 1u;
    REG(WATCHDOG_CTRL + 4u) = 0x00FFFFFFu;
    REG(WATCHDOG_CTRL) |= 0x40000000u;
#endif
#ifndef JUNO_DIAG_NO_SYSTICK
    REG(0xE000E014u) = 0x000FFFFFu;          // SysTick reload
    REG(0xE000E018u) = 0u;
    REG(0xE000E010u) = 0x7u;
#endif
}

void diagnostics_sample(uint32_t *frame) {
    REG(WATCHDOG_SCRATCH + 0u) = frame[6];  // scratch2 and 3 do not survive the reboot; 0 and 1 do
}

static int diagnostics_fallback(void) {
    // scratch1 holds 0xA0000000 while a non-secure start is under way; a reboot that still finds it means that start
    // hung or faulted, so this boot falls back once (scratch1 cleared) and the record in scratch0/2/3 is kept.
    if ((REG(WATCHDOG_SCRATCH + 4u) & 0xF0000000u) == 0xA0000000u) {
        REG(WATCHDOG_SCRATCH + 4u) &= 0x0FFFFFFFu;
        const uint32_t *vectors = (const uint32_t *)APP_VECTORS;
        REG(0xE000ED08u) = APP_VECTORS;
        __asm volatile("msr msp, %0\n bx %1" : : "r"(vectors[0]), "r"(vectors[1]));
    }
    return 0;
}
#endif

// ---- Core 1 -----------------------------------------------------------------------------------------------------

// Core 1's secure stack, for the relay, a gateway call made from core 1 and a fault taken there.
static uint32_t core1_stack[256] __attribute__((aligned(8)));
static volatile uint32_t core1_fenced;

static uint32_t __attribute__((section(".ramfunc"), noinline)) fifo_pop(uint32_t sio) {
    while ((REG(sio + SIO_FIFO_ST) & FIFO_VLD) == 0u) {
        __asm volatile("wfe");
    }
    return REG(sio + SIO_FIFO_RD);
}

static void __attribute__((section(".ramfunc"), noinline)) fifo_push(uint32_t sio, uint32_t value) {
    while ((REG(sio + SIO_FIFO_ST) & FIFO_RDY) == 0u) {
    }
    REG(sio + SIO_FIFO_WR) = value;
    __asm volatile("sev");
}

/**
 * Core 1 answers the image's launch handshake as the boot ROM would: it echoes every word of 0, 0, 1, vector table,
 * stack pointer, entry, starting over on a 0, and then enters the entry non-secure. It waits from RAM, so the image
 * erasing or programming flash through the gateway never pulls code from under it. Values the image could not itself
 * use (a secure entry, stack or vector table) are refused and the handshake starts over.
 */
static void __attribute__((section(".ramfunc"), noreturn, noinline)) core1_relay_wait(void) {
    uint32_t values[3] = {0u, 0u, 0u};
    uint32_t seq = 0u;
    for (;;) {
        uint32_t word = fifo_pop(SIO_NONSECURE);
        fifo_push(SIO_NONSECURE, word);
        if (word == 0u) {
            seq = seq == 1u || seq == 2u ? 2u : 1u;
        } else if (seq == 2u) {
            seq = word == 1u ? 3u : 0u;
        } else if (seq >= 3u) {
            values[seq - 3u] = word;
            seq++;
        } else {
            seq = 0u;
        }
        if (seq == 6u) {
            uint32_t vectors = values[0];
            uint32_t stack = values[1];
            uint32_t entry = values[2] & ~1u;
            if ((vectors & 0x7Fu) == 0u && caller_may_access(vectors, 64u, 0) && caller_may_access(stack - 8u, 8u, 1)
                    && caller_may_access(entry, 2u, 0)) {
                REG(SCB_VTOR_NS) = vectors;
                // BXNS to an address with bit 0 clear switches to the non-secure state.
                __asm volatile("msr msp_ns, %0\n bxns %1" : : "r"(stack), "r"(entry));
            }
            seq = 0u;
        }
    }
}

static void __attribute__((noreturn)) core1_relay(void) {
    fence_core();
    typedef int (*state_reset_fn)(uint32_t flags);
    ((state_reset_fn)rom_lookup('S', 'R'))(0x1u /* CURRENT_CORE */);
    core1_fenced = 1u;
    __asm volatile("sev");
    core1_relay_wait();
}

/** Starts core 1 from its boot ROM into core1_relay, on this world's FIFO, and waits until it is fenced. */
static void start_core1(void) {
    REG(PSM_FRCE_OFF + 0x2000u) = PSM_PROC1;  // set: hold core 1 off
    while ((REG(PSM_FRCE_OFF) & PSM_PROC1) == 0u) {
    }
    REG(PSM_FRCE_OFF + 0x3000u) = PSM_PROC1;  // clear: back into its boot ROM, which drains its FIFO and sends a 0
    (void)fifo_pop(SIO_SECURE);
    const uint32_t sequence[6] = {0u, 0u, 1u, (uint32_t)secure_vectors,
                                  (uint32_t)&core1_stack[sizeof core1_stack / sizeof core1_stack[0]],
                                  (uint32_t)&core1_relay | 1u};
    uint32_t seq = 0u;
    while (seq < 6u) {
        uint32_t word = sequence[seq];
        if (word == 0u) {
            while ((REG(SIO_SECURE + SIO_FIFO_ST) & FIFO_VLD) != 0u) {
                (void)REG(SIO_SECURE + SIO_FIFO_RD);
            }
            __asm volatile("sev");
        }
        fifo_push(SIO_SECURE, word);
        seq = fifo_pop(SIO_SECURE) == word ? seq + 1u : 0u;
    }
    while (core1_fenced == 0u) {
        __asm volatile("wfe");
    }
}

static void __attribute__((noreturn)) start_image(void) {
    const uint32_t *vectors = (const uint32_t *)APP_VECTORS;
    REG(SCB_VTOR_NS) = APP_VECTORS;
    // BXNS to an address with bit 0 clear switches to the non-secure state.
    __asm volatile("msr msp_ns, %0\n bxns %1" : : "r"(vectors[0]), "r"(vectors[1] & ~1u));
    for (;;) {
    }
}

void secure_reset(void) {
    uint32_t *source = &__data_load;
    for (uint32_t *target = &__data_start; target < &__data_end; target++) {
        *target = *source++;
    }
    for (uint32_t *target = &__bss_start; target < &__bss_end; target++) {
        *target = 0u;
    }
#ifdef JUNO_SECURE_DIAGNOSTICS
    diagnostics_fallback();
#endif
    fence();
    hand_over_rom();
    start_core1();
#ifdef JUNO_SECURE_DIAGNOSTICS
    diagnostics_arm();
#endif
    start_image();
}
