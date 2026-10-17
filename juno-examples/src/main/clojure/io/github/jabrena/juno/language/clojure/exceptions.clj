;; Demonstrates throwing and catching exceptions, and finally blocks, with built-in exception types.
(ns io.github.jabrena.juno.language.clojure.exceptions
  (:gen-class)
  (:import [io.github.jabrena.juno.api Delay]
           [io.github.jabrena.juno.api.io.serial BaudRate Serial]))

(defn- safe-divide ^long [^long numerator ^long denominator]
  (if (== denominator 0)
    ;; The throw branch ends in a long literal: an if whose branch is a bare throw would box its result.
    (do (throw (ArithmeticException. "denominator is zero")) 0)
    (quot numerator denominator)))

(defn- catch-demo []
  (Serial/println "Catching a built-in exception")
  (try
    (Serial/print "10 / 2 = ") (Serial/println (safe-divide 10 2))
    (Serial/print "10 / 0 = ") (Serial/println (safe-divide 10 0))
    (catch ArithmeticException e
      (Serial/print "caught: ") (Serial/println (.getMessage e)))
    (finally
      (Serial/println "finally ran"))))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (Delay/millis 2000)
  (Serial/println "Exceptions demo")
  (catch-demo)
  (Serial/println "Exceptions complete"))
