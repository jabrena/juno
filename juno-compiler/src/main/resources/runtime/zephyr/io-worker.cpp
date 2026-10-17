
// ---- The I/O worker: a Zephyr thread that runs a waiting device call while its caller is parked ----
// A bridge call to Linux waits inside Arduino_RouterBridge (k_msleep/k_yield polling), and a Juno
// thread waiting there would keep the baton. The caller hands the call to this worker instead and
// parks in the scheduler until it is done. The worker runs above the Juno threads, so it gets the CPU
// whenever its call can make progress, and it never touches the scheduler, the arena or another
// thread's state.
static constexpr uint32_t JUNO_IO_WORKER_COUNT = 2u;
static constexpr uint32_t JUNO_IO_WORKER_STACK_BYTES = 4096u;
K_THREAD_STACK_DEFINE(juno_io_worker_stack_first, JUNO_IO_WORKER_STACK_BYTES);
K_THREAD_STACK_DEFINE(juno_io_worker_stack_second, JUNO_IO_WORKER_STACK_BYTES);

struct JunoIoWorker {
  struct k_thread thread;
  struct k_sem ready;
  k_tid_t tid;
  void (*volatile job)(void*);
  void* volatile argument;
  volatile bool busy;
};

static JunoIoWorker juno_io_workers[JUNO_IO_WORKER_COUNT];
static bool juno_io_workers_started = false;

static void juno_io_worker_main(void* argument, void*, void*) {
  JunoIoWorker* worker = static_cast<JunoIoWorker*>(argument);
  for (;;) {
    k_sem_take(&worker->ready, K_FOREVER);
    worker->job(worker->argument);
    __atomic_store_n(&worker->busy, false, __ATOMIC_RELEASE);
  }
}

static void juno_port_io_start_workers() {
  if (juno_io_workers_started) return;
  juno_io_workers_started = true;
  for (uint32_t index = 0u; index < JUNO_IO_WORKER_COUNT; index++) {
    k_sem_init(&juno_io_workers[index].ready, 0, 1);
  }
  juno_io_workers[0].tid = k_thread_create(&juno_io_workers[0].thread, juno_io_worker_stack_first,
      K_THREAD_STACK_SIZEOF(juno_io_worker_stack_first), juno_io_worker_main, &juno_io_workers[0], nullptr, nullptr,
      K_PRIO_PREEMPT(4), 0, K_NO_WAIT);
  juno_io_workers[1].tid = k_thread_create(&juno_io_workers[1].thread, juno_io_worker_stack_second,
      K_THREAD_STACK_SIZEOF(juno_io_worker_stack_second), juno_io_worker_main, &juno_io_workers[1], nullptr, nullptr,
      K_PRIO_PREEMPT(4), 0, K_NO_WAIT);
}

static uint32_t juno_port_io_dispatch(void (*job)(void*), void* argument) {
  juno_port_io_start_workers();
  for (uint32_t index = 0u; index < JUNO_IO_WORKER_COUNT; index++) {
    bool expected = false;
    if (!__atomic_compare_exchange_n(&juno_io_workers[index].busy, &expected, true, false,
            __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE)) continue;
    juno_io_workers[index].job = job;
    juno_io_workers[index].argument = argument;
    k_sem_give(&juno_io_workers[index].ready);
    return index;
  }
  return JUNO_IO_WORKER_COUNT;
}

static bool juno_port_io_busy(uint32_t worker) {
  return worker < JUNO_IO_WORKER_COUNT
      && __atomic_load_n(&juno_io_workers[worker].busy, __ATOMIC_ACQUIRE);
}

static bool juno_port_io_busy() {
  for (uint32_t index = 0u; index < JUNO_IO_WORKER_COUNT; index++) {
    if (juno_port_io_busy(index)) return true;
  }
  return false;
}

extern "C" bool juno_io_on_worker() {
  for (uint32_t index = 0u; index < JUNO_IO_WORKER_COUNT; index++) {
    if (juno_io_workers[index].tid != nullptr && k_current_get() == juno_io_workers[index].tid) return true;
  }
  return false;
}
