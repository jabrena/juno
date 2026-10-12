
// ---- The I/O worker: a Zephyr thread that runs a waiting device call while its caller is parked ----
// A bridge call to Linux waits inside Arduino_RouterBridge (k_msleep/k_yield polling), and a Juno
// thread waiting there would keep the baton. The caller hands the call to this worker instead and
// parks in the scheduler until it is done. The worker runs above the Juno threads, so it gets the CPU
// whenever its call can make progress, and it never touches the scheduler, the arena or another
// thread's state.
static constexpr uint32_t JUNO_IO_WORKER_STACK_BYTES = 4096u;
K_THREAD_STACK_DEFINE(juno_io_worker_stack, JUNO_IO_WORKER_STACK_BYTES);
static struct k_thread juno_io_worker_thread;
static struct k_sem juno_io_job_ready;
static k_tid_t juno_io_worker_tid = nullptr;
static void (*volatile juno_io_job)(void*) = nullptr;
static void* volatile juno_io_job_argument = nullptr;
static volatile bool juno_io_job_busy = false;

static void juno_io_worker_main(void*, void*, void*) {
  for (;;) {
    k_sem_take(&juno_io_job_ready, K_FOREVER);
    juno_io_job(juno_io_job_argument);
    __atomic_store_n(&juno_io_job_busy, false, __ATOMIC_RELEASE);
  }
}

static void juno_port_io_dispatch(void (*job)(void*), void* argument) {
  if (juno_io_worker_tid == nullptr) {
    k_sem_init(&juno_io_job_ready, 0, 1);
    juno_io_worker_tid = k_thread_create(&juno_io_worker_thread, juno_io_worker_stack,
        K_THREAD_STACK_SIZEOF(juno_io_worker_stack), juno_io_worker_main, nullptr, nullptr, nullptr,
        K_PRIO_PREEMPT(4), 0, K_NO_WAIT);
  }
  juno_io_job = job;
  juno_io_job_argument = argument;
  __atomic_store_n(&juno_io_job_busy, true, __ATOMIC_RELEASE);
  k_sem_give(&juno_io_job_ready);
}

static bool juno_port_io_busy() {
  return __atomic_load_n(&juno_io_job_busy, __ATOMIC_ACQUIRE);
}

extern "C" bool juno_io_on_worker() {
  return juno_io_worker_tid != nullptr && k_current_get() == juno_io_worker_tid;
}
