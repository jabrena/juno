static const char* const juno_throwable_names[] = {
${JUNO_THROWABLE_NAMES}};

// threadId is -1 for the main thread, else the number in "Thread-N".
static void juno_throw_report(int32_t exception, int32_t threadId) {
  Serial.print("Exception in thread \"");
  if (threadId < 0) {
    Serial.print("main");
  } else {
    Serial.print("Thread-");
    Serial.print(threadId);
  }
  Serial.print("\" ");
  if (exception == 0) {
    Serial.println("java.lang.NullPointerException");
    return;
  }
  const int32_t* header = reinterpret_cast<const int32_t*>(static_cast<intptr_t>(exception));
  Serial.print(juno_throwable_names[header[${JUNO_CLASS_ID_WORD}]]);
  const char* message = reinterpret_cast<const char*>(static_cast<intptr_t>(header[${JUNO_MESSAGE_WORD}]));
  if (message != nullptr) {
    Serial.print(": ");
    Serial.print(message);
  }
  Serial.println();
}

extern "C" [[noreturn]] void juno_throw_uncaught(int32_t exception) {
  juno_throw_report(exception, -1);
  juno_panic();
}

// The exception unwinding through generated frames: set by athrow, left set while frames
// return, cleared when a handler takes it. Callers poll it after calls that can throw.
static int32_t juno_pending_exception = 0;

extern "C" void juno_throw_raise(int32_t exception) {
  if (exception == 0) juno_throw_uncaught(exception);
  juno_pending_exception = exception;
}

extern "C" int32_t juno_throw_pending() {
  return juno_pending_exception;
}

// Class id of the pending exception if caughtMask has its bit (and clears it), else -1.
extern "C" int32_t juno_throw_catch(int32_t caughtMask) {
  int32_t classId = reinterpret_cast<const int32_t*>(
      static_cast<intptr_t>(juno_pending_exception))[${JUNO_CLASS_ID_WORD}];
  if ((static_cast<uint32_t>(caughtMask) >> classId & 1u) == 0u) return -1;
  juno_pending_exception = 0;
  return classId;
}

extern "C" int32_t juno_throw_dispatch(int32_t exception, int32_t caughtMask) {
  juno_throw_raise(exception);
  return juno_throw_catch(caughtMask);
}

// Called where the entry point returns: an exception still pending has no handler left.
extern "C" void juno_throw_check_escape() {
  if (juno_pending_exception != 0) juno_throw_uncaught(juno_pending_exception);
}
