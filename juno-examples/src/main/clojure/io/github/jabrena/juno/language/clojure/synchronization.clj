;; Demonstrates volatile publication, intrinsic monitor blocks, atomic counters, and Juno's restricted
;; ReentrantLock subset. State and task objects are allocated once before the work starts; the counting loop
;; mutates primitive fields and does not allocate.
(ns io.github.jabrena.juno.language.clojure.synchronization
  (:gen-class :name ^{io.github.jabrena.juno.annotations.Board [io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
                                                               io.github.jabrena.juno.annotations.ArduinoUnoQ]}
              io.github.jabrena.juno.language.clojure.synchronization)
  (:import [io.github.jabrena.juno.api.io.serial BaudRate Serial]
           [java.util.concurrent StructuredTaskScope]
           [java.util.concurrent.atomic AtomicInteger]
           [java.util.concurrent.locks ReentrantLock]))

(definterface SynchronizationState
  (^void publish [])
  (^void countValues [])
  (^void incrementValues [])
  (^void incrementSynchronized [])
  (^void incrementLocked [])
  (^long readyValue [])
  (^long publishedValue [])
  (^long synchronizedCount [])
  (^long lockedCount [])
  (^long atomicCount []))

(deftype Guard [])

(defn- increment-under-monitor [^Object guard ^SynchronizationState state]
  (monitor-enter guard)
  ;; Keeping try/finally in tail position lets Clojure emit a direct exception table instead of an IFn adapter.
  (try
    (.incrementSynchronized state)
    (finally
      (monitor-exit guard))))

(defn- increment-under-lock [^ReentrantLock lock ^SynchronizationState state]
  (.lock lock)
  (try
    (.incrementLocked state)
    (when (.tryLock lock)
      (.unlock lock))
    (finally
      (.unlock lock))))

;; Clojure emits the three reference fields below as java.lang.Object. Juno specializes this exact deftype
;; constructor shape and validates the concrete casts at each use, without admitting Object in ordinary methods.
(deftype State [^Object guard
                ^ReentrantLock lock
                ^AtomicInteger atomic-counter
                ^{:tag long :volatile-mutable true} ready
                ^{:tag long :unsynchronized-mutable true} published-value
                ^{:tag long :unsynchronized-mutable true} synchronized-counter
                ^{:tag long :unsynchronized-mutable true} locked-counter]
  SynchronizationState
  (^void publish [_]
    (set! published-value 42)
    (set! ready 1)
    nil)
  (^void countValues [this]
    (loop [index 0]
      (when (< index 50)
        (.incrementValues this)
        (recur (inc index)))))
  (^void incrementValues [this]
    (.incrementAndGet atomic-counter)
    (increment-under-monitor guard this)
    (increment-under-lock lock this))
  (^void incrementSynchronized [_]
    (set! synchronized-counter (inc synchronized-counter))
    nil)
  (^void incrementLocked [_]
    (set! locked-counter (inc locked-counter))
    nil)
  (^long readyValue [_] ready)
  (^long publishedValue [_] published-value)
  (^long synchronizedCount [_] synchronized-counter)
  (^long lockedCount [_] locked-counter)
  (^long atomicCount [_] (.get atomic-counter)))

(deftype PublishTask [^SynchronizationState state]
  Runnable
  (run [_] (.publish state)))

(deftype CountTask [^SynchronizationState state]
  Runnable
  (run [_] (.countValues state)))

(defn- run-publisher [^SynchronizationState state ^Runnable publisher]
  (let [^StructuredTaskScope scope (StructuredTaskScope/open)]
    (try
      (.fork scope publisher)
      (while (== (.readyValue state) 0))
      (.join scope)
      (finally
        (.close scope)))))

(defn- run-counters [^Runnable counter]
  (let [^StructuredTaskScope scope (StructuredTaskScope/open)]
    (try
      (.fork scope counter)
      (.fork scope counter)
      (.join scope)
      (finally
        (.close scope)))))

(defn- volatile-publication [^SynchronizationState state ^Runnable publisher]
  (run-publisher state publisher)
  (Serial/print "published ")
  (Serial/println (.publishedValue state)))

(defn- monitor-and-lock [^SynchronizationState state ^Runnable counter]
  (run-counters counter)
  (Serial/print "synchronized ")
  (Serial/println (.synchronizedCount state))
  (Serial/print "locked ")
  (Serial/println (.lockedCount state))
  (Serial/print "atomic ")
  (Serial/println (.atomicCount state)))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (let [state (State. (Guard.) (ReentrantLock.) (AtomicInteger.) 0 0 0 0)
        publisher (PublishTask. state)
        counter (CountTask. state)]
    (volatile-publication state publisher)
    (monitor-and-lock state counter)))
