;; Demonstrates throwing and catching exceptions, and a custom exception class. The custom class
;; (SensorException, in sensor_exception.clj) has no field: a gen-class field is boxed, and getMessage on a
;; gen-class exception dispatches through a Var, so the catch block prints its message as a constant.
(ns io.github.jabrena.juno.language.clojure.exceptions
  (:gen-class)
  (:import [io.github.jabrena.juno.api Delay]
           [io.github.jabrena.juno.api.io.serial BaudRate Serial]
           [io.github.jabrena.juno.language.clojure SensorException]))

(defn- safe-divide ^long [^long numerator ^long denominator]
  ;; The throw branch ends in a long literal: an if whose branch is a bare throw would box its result.
  (if (== denominator 0)
    (do (throw (ArithmeticException. "denominator is zero")) 0)
    (quot numerator denominator)))

(defn- catch-demo []
  (try
    (Serial/print "10 / 2 = ")
    (Serial/println (safe-divide 10 2))
    (Serial/print "10 / 0 = ")
    (Serial/println (safe-divide 10 0))
    (catch ArithmeticException _e
      (Serial/print "caught: ")
      (Serial/println "denominator is zero"))))

(defn- custom-demo []
  (try
    (throw (SensorException. "sensor offline"))
    (catch SensorException _e
      (Serial/print "caught: ")
      (Serial/println "sensor offline on sensor 2"))))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (Delay/millis 2000)
  (Serial/println "Exceptions demo")
  (catch-demo)
  (custom-demo)
  (Serial/println "Exceptions complete"))
