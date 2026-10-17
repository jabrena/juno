;; Scoped values carry one precision policy and one step-count array into three structured subtasks. Nested bindings
;; may lower the precision but cap requests that exceed the owner's 40 digits.
(ns io.github.jabrena.juno.language.clojure.scoped-values-precision
  (:gen-class :name ^{io.github.jabrena.juno.annotations.Board [io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
                                                               io.github.jabrena.juno.annotations.ArduinoUnoQ]}
              io.github.jabrena.juno.language.clojure.scoped_values_precision)
  (:import [io.github.jabrena.juno.api.io.serial BaudRate Serial]
           [java.lang ScopedValue ScopedValue$Carrier]
           [java.math BigDecimal MathContext]
           [java.util.concurrent StructuredTaskScope]))

(definterface PrecisionProgram
  (^void runScenarios [])
  (^void report [^String title])
  (^void calculate [^long kind])
  (^long digits []))

(deftype PrecisionCalculation [^PrecisionProgram program ^long kind]
  Runnable
  (run [_] (.calculate program kind)))

(deftype PrecisionReport [^PrecisionProgram program ^String title]
  Runnable
  (run [_] (.report program title)))

(deftype PrecisionScenarios [^PrecisionProgram program]
  Runnable
  (run [_] (.runScenarios program)))

(defn- calculate-all [^PrecisionProgram program]
  (let [^StructuredTaskScope scope (StructuredTaskScope/open)]
    ;; Keeping try/finally in tail position lets Clojure emit a direct exception table instead of an IFn adapter.
    (try
      (.fork scope (PrecisionCalculation. program 0))
      (.fork scope (PrecisionCalculation. program 1))
      (.fork scope (PrecisionCalculation. program 2))
      (.join scope)
      (finally
        (.close scope)))))

(defn- precision-context ^MathContext [^ScopedValue precision]
  (let [^MathContext context (.get precision)] context))

(defn- tolerance ^BigDecimal [^ScopedValue precision]
  (.movePointLeft BigDecimal/ONE (int (+ (.getPrecision (precision-context precision)) 2))))

(defn- sqrt-two ^BigDecimal [^ScopedValue precision ^ScopedValue steps-key]
  (let [^MathContext context (precision-context precision)
        ^BigDecimal two (BigDecimal. (int 2))
        ^BigDecimal limit (tolerance precision)
        ^ints steps (.get steps-key)]
    (loop [^BigDecimal x BigDecimal/ONE
           step-count 0]
      (let [^BigDecimal next (.divide (.add x (.divide two x context) context) two context)
            ^BigDecimal delta (.abs (.subtract next x))
            next-count (inc step-count)]
        (if (>= (.compareTo delta limit) 0)
          (recur next next-count)
          (do
            (aset steps 0 (int next-count))
            next))))))

(defn- arctan-inverse ^BigDecimal [^ScopedValue precision ^ScopedValue steps-key ^long n]
  (let [^MathContext context (precision-context precision)
        ^BigDecimal limit (tolerance precision)
        ^BigDecimal divisor (BigDecimal/valueOf n)
        ^BigDecimal square (BigDecimal/valueOf (* n n))
        ^BigDecimal initial (.divide BigDecimal/ONE divisor context)
        ^ints steps (.get steps-key)]
    (loop [^BigDecimal power initial
           ^BigDecimal sum initial
           k 1]
      (if (>= (.compareTo power limit) 0)
        (let [^BigDecimal next-power (.divide power square context)
              ^BigDecimal term (.divide next-power (BigDecimal/valueOf (+ (* 2 k) 1)) context)
              ^BigDecimal next-sum (if (== (rem k 2) 1)
                                     (.subtract sum term context)
                                     (.add sum term context))]
          (aset steps 1 (int (inc (aget steps 1))))
          (recur next-power next-sum (inc k)))
        sum))))

(defn- pi ^BigDecimal [^ScopedValue precision ^ScopedValue steps-key]
  (let [^MathContext context (precision-context precision)
        ^BigDecimal first (.multiply (arctan-inverse precision steps-key 5)
                                     (BigDecimal/valueOf 16) context)
        ^BigDecimal second (.multiply (arctan-inverse precision steps-key 239)
                                      (BigDecimal/valueOf 4) context)]
    (.subtract first second context)))

(defn- euler ^BigDecimal [^ScopedValue precision ^ScopedValue steps-key]
  (let [^MathContext context (precision-context precision)
        ^BigDecimal limit (tolerance precision)
        ^ints steps (.get steps-key)]
    (loop [^BigDecimal sum BigDecimal/ONE
           ^BigDecimal term BigDecimal/ONE
           step-count 0]
      (let [next-count (inc step-count)
            ^BigDecimal next-term (.divide term (BigDecimal/valueOf next-count) context)
            ^BigDecimal next-sum (.add sum next-term context)]
        (if (>= (.compareTo next-term limit) 0)
          (recur next-sum next-term next-count)
          (do
            (aset steps 2 (int next-count))
            next-sum))))))

(deftype PrecisionState [^ScopedValue precision
                         ^ScopedValue steps-key
                         ^{:unsynchronized-mutable true} sqrt2
                         ^{:unsynchronized-mutable true} pi-value
                         ^{:unsynchronized-mutable true} e-value]
  PrecisionProgram
  (^long digits [_]
    (.getPrecision (precision-context precision)))
  (^void calculate [_ ^long kind]
    (if (== kind 0)
      (set! sqrt2 (sqrt-two precision steps-key))
      (if (== kind 1)
        (set! pi-value (pi precision steps-key))
        (set! e-value (euler precision steps-key))))
    nil)
  (^void report [this ^String title]
    (Serial/print "== ")
    (Serial/println title)
    (calculate-all this)
    (let [^MathContext context (precision-context precision)
          ^ints steps (.get steps-key)
          ^BigDecimal sqrt-result sqrt2
          ^BigDecimal pi-result pi-value
          ^BigDecimal e-result e-value]
      (Serial/print "sqrt2 = ")
      (Serial/print (.toString (.round sqrt-result context)))
      (Serial/print " (")
      (Serial/print (aget steps 0))
      (Serial/println " steps)")
      (Serial/print "pi = ")
      (Serial/print (.toString (.round pi-result context)))
      (Serial/print " (")
      (Serial/print (aget steps 1))
      (Serial/println " steps)")
      (Serial/print "e = ")
      (Serial/print (.toString (.round e-result context)))
      (Serial/print " (")
      (Serial/print (aget steps 2))
      (Serial/println " steps)")
      (Serial/print "sqrt2^2 = ")
      (Serial/println (.toString (.multiply sqrt-result sqrt-result context)))
      (Serial/print "pi - e = ")
      (Serial/println (.toString (.subtract pi-result e-result context)))))
  (^void runScenarios [this]
    (.report this "owner binds 40 digits")
    (let [^ScopedValue$Carrier lower (ScopedValue/where precision
                                                        (MathContext. (int (Math/min 5 (.digits this)))))]
      (.run lower (PrecisionReport. this "nested asks for 5")))
    (let [^ScopedValue$Carrier capped (ScopedValue/where precision
                                                         (MathContext. (int (Math/min 60 (.digits this)))))]
      (.run capped (PrecisionReport. this "nested asks for 60, capped")))
    (.report this "back in the owner")))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (let [^ScopedValue precision (ScopedValue/newInstance)
        ^ScopedValue steps-key (ScopedValue/newInstance)
        ^ints steps (int-array 3)
        state (PrecisionState. precision steps-key nil nil nil)
        ^ScopedValue$Carrier owner (ScopedValue/where precision (MathContext. (int 40)))]
    (.run (.where owner steps-key steps) (PrecisionScenarios. state))))
