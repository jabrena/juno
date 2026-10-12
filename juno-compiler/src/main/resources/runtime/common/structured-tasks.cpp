static constexpr uint32_t JUNO_JOINER_ALL_SUCCESSFUL = 1u;
static constexpr uint32_t JUNO_JOINER_ANY_SUCCESSFUL = 2u;
static constexpr uint32_t JUNO_JOINER_AWAIT_ALL_SUCCESSFUL = 3u;
static constexpr uint32_t JUNO_JOINER_AWAIT_ALL = 4u;
static constexpr uint32_t JUNO_SCOPE_JOINED = 1u;
static constexpr uint32_t JUNO_SCOPE_CLOSED = 2u;
static constexpr uint32_t JUNO_SCOPE_HAS_RESULT = 4u;
static constexpr uint32_t JUNO_SCOPE_SHUTDOWN = 8u;

static JunoTaskScope* juno_task_scope_of(int32_t handle) {
  if (handle == 0) juno_panic();
  return reinterpret_cast<JunoTaskScope*>(static_cast<intptr_t>(handle));
}

static int32_t juno_task_exception(uint32_t classId, const char* message) {
  auto* exception = static_cast<int32_t*>(juno_alloc(8u, 4u));
  exception[0] = static_cast<int32_t>(classId);
  exception[1] = static_cast<int32_t>(reinterpret_cast<intptr_t>(message));
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(exception));
}

static void juno_task_raise_illegal_state(const char* message) {
  juno_throw_raise(juno_task_exception(${JUNO_ILLEGAL_STATE_EXCEPTION_CLASS_ID}u, message));
}

static void juno_task_raise_failed(int32_t failure) {
  const char* message = "structured subtask failed";
  if (failure != 0) {
    const int32_t* header = reinterpret_cast<const int32_t*>(static_cast<intptr_t>(failure));
    const char* original = reinterpret_cast<const char*>(static_cast<intptr_t>(header[1]));
    if (original != nullptr) message = original;
  }
  juno_throw_raise(juno_task_exception(${JUNO_FAILED_EXCEPTION_CLASS_ID}u, message));
}

static void juno_task_remove(JunoTaskScope* scope, JunoThreadObject* task) {
  for (uint32_t index = 0u; index < JUNO_MAX_THREADS - 1u; index++) {
    if (scope->tasks[index] == task) scope->tasks[index] = nullptr;
  }
  if (scope->active != 0u) scope->active--;
}

static void juno_task_cancel_siblings(JunoTaskScope* scope, JunoThreadObject* completed);

// A cancelled slot never reaches its monitorexit / unlock, so its monitors are released here.
static void juno_task_release_monitors(uint32_t owner) {
  for (uint32_t index = 0u; index < JUNO_MAX_MONITORS; index++) {
    if (juno_monitors[index].key != 0u && juno_monitors[index].owner == owner) {
      juno_monitors[index] = {0u, 0u, 0u};
    }
  }
}

// Cancelling a task also cancels the subtasks of every scope it owns, so they cannot be orphaned
// holding scheduler slots. Each child cancel recurses through this same path.
static void juno_task_cancel_owned(uint32_t owner) {
  for (uint32_t slot = 1u; slot < JUNO_MAX_THREADS; slot++) {
    JunoThreadObject* child = juno_slots[slot].thread;
    if (child == nullptr || (child->flags & JUNO_THREAD_TASK) == 0u
            || child->scope == nullptr || child->scope->owner != owner) continue;
    JunoTaskScope* scope = child->scope;
    scope->flags |= JUNO_SCOPE_SHUTDOWN | JUNO_SCOPE_CLOSED;
    juno_task_cancel_siblings(scope, nullptr);
  }
}

static void juno_task_cancel(JunoThreadObject* task) {
  if (task == nullptr || (task->flags & JUNO_THREAD_FINISHED) != 0u) return;
  task->flags |= JUNO_THREAD_FINISHED | JUNO_TASK_CANCELLED;
  for (uint32_t slot = 1u; slot < JUNO_MAX_THREADS; slot++) {
    if (juno_slots[slot].thread != task) continue;
    juno_task_cancel_owned(slot + 1u);
    juno_task_release_monitors(slot + 1u);
    juno_io_release_owned(slot + 1u);
    bool draining = juno_slots[slot].state == JUNO_SLOT_WAITING_IO;
    juno_port_cancel(slot);
    juno_slots[slot] = {JUNO_SLOT_FREE, 0u, nullptr, nullptr, nullptr, 0};
    // A caller cancelled while the I/O worker runs its call: its arrays must outlive the call.
    if (draining) juno_slots[slot].state = JUNO_SLOT_DRAINING;
    if (juno_live_threads != 0u) juno_live_threads--;
    return;
  }
}

static void juno_task_cancel_siblings(JunoTaskScope* scope, JunoThreadObject* completed) {
  for (uint32_t index = 0u; index < JUNO_MAX_THREADS - 1u; index++) {
    JunoThreadObject* task = scope->tasks[index];
    if (task == nullptr || task == completed) continue;
    juno_task_cancel(task);
    scope->tasks[index] = nullptr;
    if (scope->active != 0u) scope->active--;
  }
}

static void juno_task_complete(JunoThreadObject* task, int32_t result) {
  JunoTaskScope* scope = task->scope;
  int32_t failure = juno_pending_exception;
  juno_pending_exception = 0;
  juno_slots[juno_current_slot].pending = 0;
  if (failure == 0) {
    task->result = result;
    task->flags |= JUNO_TASK_SUCCESS;
    if (scope->policy == JUNO_JOINER_ANY_SUCCESSFUL
            && (scope->flags & JUNO_SCOPE_HAS_RESULT) == 0u) {
      scope->result = result;
      scope->flags |= JUNO_SCOPE_HAS_RESULT | JUNO_SCOPE_SHUTDOWN;
    }
  } else {
    task->failure = failure;
    task->flags |= JUNO_TASK_FAILED;
    if (scope->firstFailure == 0) scope->firstFailure = failure;
    if (scope->policy == JUNO_JOINER_ALL_SUCCESSFUL
            || scope->policy == JUNO_JOINER_AWAIT_ALL_SUCCESSFUL) {
      scope->flags |= JUNO_SCOPE_SHUTDOWN;
    }
  }
  juno_task_remove(scope, task);
  if ((scope->flags & JUNO_SCOPE_SHUTDOWN) != 0u) juno_task_cancel_siblings(scope, task);
}

extern "C" int32_t juno_task_scope_open(int32_t policy) {
  if (policy < static_cast<int32_t>(JUNO_JOINER_ALL_SUCCESSFUL)
          || policy > static_cast<int32_t>(JUNO_JOINER_AWAIT_ALL)) {
    juno_task_raise_illegal_state("Unsupported StructuredTaskScope Joiner");
    return 0;
  }
  auto* scope = static_cast<JunoTaskScope*>(juno_alloc(sizeof(JunoTaskScope), 4u));
  scope->policy = static_cast<uint32_t>(policy);
  scope->owner = juno_current_slot + 1u;
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(scope));
}

extern "C" int32_t juno_task_scope_open_default() {
  return juno_task_scope_open(static_cast<int32_t>(JUNO_JOINER_AWAIT_ALL_SUCCESSFUL));
}

static bool juno_task_scope_check_owner(JunoTaskScope* scope) {
  if (scope->owner == juno_current_slot + 1u) return true;
  juno_task_raise_illegal_state("StructuredTaskScope used by a non-owner thread");
  return false;
}

static int32_t juno_task_scope_fork(int32_t scopeHandle, int32_t taskFunction, bool runnable) {
  JunoTaskScope* scope = juno_task_scope_of(scopeHandle);
  if (!juno_task_scope_check_owner(scope)) return 0;
  if ((scope->flags & (JUNO_SCOPE_CLOSED | JUNO_SCOPE_SHUTDOWN | JUNO_SCOPE_JOINED)) != 0u) {
    juno_task_raise_illegal_state("StructuredTaskScope cannot fork after join or cancellation");
    return 0;
  }
  uint32_t taskIndex = 0u;
  while (taskIndex < JUNO_MAX_THREADS - 1u && scope->tasks[taskIndex] != nullptr) taskIndex++;
  if (taskIndex == JUNO_MAX_THREADS - 1u) {
    juno_task_raise_illegal_state("StructuredTaskScope has too many active subtasks");
    return 0;
  }
  auto* task = static_cast<JunoThreadObject*>(juno_alloc(sizeof(JunoThreadObject), 4u));
  task->runnable = static_cast<uint32_t>(taskFunction);
  task->flags = JUNO_THREAD_TASK | (runnable ? JUNO_TASK_RUNNABLE : 0u);
  task->id = juno_threads_created++;
  task->scope = scope;
  ${JUNO_FORK_BINDINGS}
  scope->tasks[taskIndex] = task;
  scope->active++;
  juno_thread_start(static_cast<int32_t>(reinterpret_cast<intptr_t>(task)));
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(task));
}

extern "C" int32_t juno_task_scope_fork_callable(int32_t scopeHandle, int32_t callable) {
  return juno_task_scope_fork(scopeHandle, callable, false);
}

extern "C" int32_t juno_task_scope_fork_runnable(int32_t scopeHandle, int32_t runnable) {
  return juno_task_scope_fork(scopeHandle, runnable, true);
}

extern "C" int32_t juno_task_scope_join(int32_t scopeHandle) {
  JunoTaskScope* scope = juno_task_scope_of(scopeHandle);
  if (!juno_task_scope_check_owner(scope)) return 0;
  if ((scope->flags & (JUNO_SCOPE_JOINED | JUNO_SCOPE_CLOSED)) != 0u) {
    juno_task_raise_illegal_state("join() has already been attempted");
    return 0;
  }
  while (scope->active != 0u && (scope->flags & JUNO_SCOPE_SHUTDOWN) == 0u) {
    juno_slots[juno_current_slot].state = JUNO_SLOT_JOINING_SCOPE;
    juno_slots[juno_current_slot].scope = scope;
    juno_sched_block();
  }
  juno_slots[juno_current_slot].scope = nullptr;
  scope->flags |= JUNO_SCOPE_JOINED;
  if ((scope->policy == JUNO_JOINER_ALL_SUCCESSFUL
          || scope->policy == JUNO_JOINER_AWAIT_ALL_SUCCESSFUL)
          && scope->firstFailure != 0) {
    juno_task_raise_failed(scope->firstFailure);
    return 0;
  }
  if (scope->policy == JUNO_JOINER_ANY_SUCCESSFUL) {
    if ((scope->flags & JUNO_SCOPE_HAS_RESULT) == 0u) {
      juno_task_raise_failed(scope->firstFailure);
      return 0;
    }
    return scope->result;
  }
  // allSuccessfulOrThrow returns a non-null scope-backed token. Stream operations
  // remain outside Juno's deliberately small subset; both await policies return null.
  return scope->policy == JUNO_JOINER_ALL_SUCCESSFUL ? scopeHandle : 0;
}

extern "C" int32_t juno_task_scope_is_cancelled(int32_t scopeHandle) {
  JunoTaskScope* scope = juno_task_scope_of(scopeHandle);
  return (scope->flags & JUNO_SCOPE_SHUTDOWN) != 0u ? 1 : 0;
}

extern "C" int32_t juno_task_get(int32_t taskHandle) {
  JunoThreadObject* task = juno_thread_of(taskHandle);
  if ((task->flags & JUNO_TASK_SUCCESS) == 0u) {
    juno_task_raise_illegal_state("Subtask did not complete successfully");
    return 0;
  }
  return task->result;
}

extern "C" int32_t juno_task_state(int32_t taskHandle) {
  JunoThreadObject* task = juno_thread_of(taskHandle);
  if ((task->flags & JUNO_TASK_SUCCESS) != 0u) return 1;
  if ((task->flags & JUNO_TASK_FAILED) != 0u) return 2;
  return 0;
}

extern "C" int32_t juno_task_exception(int32_t taskHandle) {
  JunoThreadObject* task = juno_thread_of(taskHandle);
  if ((task->flags & JUNO_TASK_FAILED) == 0u) {
    juno_task_raise_illegal_state("Subtask did not complete with exception");
    return 0;
  }
  return task->failure;
}

extern "C" void juno_task_scope_close(int32_t scopeHandle) {
  JunoTaskScope* scope = juno_task_scope_of(scopeHandle);
  if (!juno_task_scope_check_owner(scope)) return;
  if ((scope->flags & JUNO_SCOPE_JOINED) == 0u) {
    scope->flags |= JUNO_SCOPE_SHUTDOWN;
    juno_task_cancel_siblings(scope, nullptr);
  }
  scope->flags |= JUNO_SCOPE_CLOSED;
}
