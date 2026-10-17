;; A structured task scope owns three prime-counting tasks. Each task writes one array slot, and the owner reads the
;; results only after join and closes the scope before returning.
(ns io.github.jabrena.juno.language.clojure.structured-concurrency
  (:gen-class :name ^{io.github.jabrena.juno.annotations.Board [io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
                                                               io.github.jabrena.juno.annotations.ArduinoUnoQ]}
              io.github.jabrena.juno.language.clojure.structured_concurrency)
  (:import [io.github.jabrena.juno.api.io.serial BaudRate Serial]
           [java.util.concurrent StructuredTaskScope]))

(definterface StructuredPrimeState
  (^long primeFlag [^long value])
  (^void countSlice [^long slot ^long from ^long to])
  (^void report []))

(deftype StructuredPrimeCounts [^ints counts]
  StructuredPrimeState
  (^long primeFlag [_ ^long value]
    (if (< value 2)
      0
      (loop [divisor 2]
        (if (> (* divisor divisor) value)
          1
          (if (== (rem value divisor) 0)
            0
            (recur (inc divisor)))))))
  (^void countSlice [this ^long slot ^long from ^long to]
    (loop [candidate from
           found 0]
      (if (< candidate to)
        (recur (inc candidate) (if (== (.primeFlag this candidate) 1) (inc found) found))
        (do
          (aset counts slot (int found))
          nil))))
  (^void report [_]
    (loop [index 0
           total 0]
      (if (< index 3)
        (let [count (aget counts index)]
          (Serial/print "slice ")
          (Serial/print index)
          (Serial/print ": ")
          (Serial/println count)
          (recur (inc index) (+ total count)))
        (do
          (Serial/print "primes below 300: ")
          (Serial/println total))))))

(deftype StructuredPrimeTask [^StructuredPrimeState state ^long slot ^long from ^long to]
  Runnable
  (run [_] (.countSlice state slot from to)))

(defn- count-primes [^StructuredPrimeState state]
  (let [^StructuredTaskScope scope (StructuredTaskScope/open)]
    ;; Keeping try/finally in tail position lets Clojure emit a direct exception table instead of an IFn adapter.
    (try
      (.fork scope (StructuredPrimeTask. state 0 2 100))
      (.fork scope (StructuredPrimeTask. state 1 100 200))
      (.fork scope (StructuredPrimeTask. state 2 200 300))
      (.join scope)
      (finally
        (.close scope)))))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (Serial/println "Counting primes below 300")
  (let [state (StructuredPrimeCounts. (int-array 3))]
    (count-primes state)
    (Serial/println "scope closed")
    (.report state)))
