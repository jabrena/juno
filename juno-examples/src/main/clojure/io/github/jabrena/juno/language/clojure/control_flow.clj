;; Demonstrates sequence, bifurcation, and iteration control-flow structures. Primitive `long` hints keep
;; the arithmetic on the JVM's primitive operations, and Serial prints each primitive directly.
(ns io.github.jabrena.juno.language.clojure.control-flow
  (:gen-class)
  (:import [io.github.jabrena.juno.api Delay]
           [io.github.jabrena.juno.api.io.serial BaudRate Serial]))

(defn- sequence-demo []
  (Serial/println "Sequence")
  (let [initial-value 6
        doubled-value (* initial-value 2)
        final-value (+ doubled-value 3)]
    (Serial/print "initial value: ")
    (Serial/println initial-value)
    (Serial/print "doubled value: ")
    (Serial/println doubled-value)
    (Serial/print "final value: ")
    (Serial/println final-value)))

(defn- bifurcation-demo []
  (Serial/println "Bifurcation")
  (let [final-value 15
        remainder (rem final-value 3)]
    (if (>= final-value 10)
      (Serial/println "final value is at least 10")
      (Serial/println "final value is below 10"))
    (if (== remainder 0)
      (Serial/println "divisible by 3")
      (if (== remainder 1)
        (Serial/println "remainder is 1")
        (Serial/println "remainder is 2")))))

(defn- iteration-demo []
  (Serial/println "Iteration with loop/recur")
  (loop [index 1]
    (when (<= index 3)
      (Serial/print "loop index: ")
      (Serial/println index)
      (recur (inc index)))))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (Delay/millis 2000)
  (Serial/println "Control flow demo")
  (sequence-demo)
  (bifurcation-demo)
  (iteration-demo)
  (Serial/println "Control flow complete"))
