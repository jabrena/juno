;; Demonstrates defining functions with parameters and return values, reporting each result with Serial. Clojure
;; return hints are long or double, so the even test returns 1 or 0 rather than a boolean.
(ns io.github.jabrena.juno.language.clojure.methods
  (:gen-class)
  (:import [io.github.jabrena.juno.api Delay]
           [io.github.jabrena.juno.api.io.serial BaudRate Serial]))

(defn- add ^long [^long first ^long second]
  (+ first second))

(defn- square ^long [^long value]
  (* value value))

(defn- is-even ^long [^long value]
  (if (== (rem value 2) 0) 1 0))

(defn- multiply ^long [^long first ^long second]
  (* first second))

(defn- average ^double [^double first ^double second]
  (/ (+ first second) 2.0))

(defn- double-value ^long [^long value]
  (* value 2))

(defn- add-then-double ^long [^long first ^long second]
  (double-value (add first second)))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (Delay/millis 2000)
  (Serial/println "Defining methods demo")
  (let [left 12
        right 5
        sum (add left right)
        squared (square sum)]
    (Serial/print left) (Serial/print " + ") (Serial/print right) (Serial/print " = ") (Serial/println sum)
    (Serial/print sum) (Serial/print " squared = ") (Serial/println squared)
    (Serial/print sum) (Serial/print " is even: ") (Serial/println (if (== (is-even sum) 1) "true" "false"))
    (Serial/print "long product: ") (Serial/println (multiply 123456 1000))
    (Serial/print "average: ") (Serial/println (average 12.5 7.5))
    (Serial/print "add then double: ") (Serial/println (add-then-double left right))))
