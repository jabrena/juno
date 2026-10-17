;; Demonstrates java.lang.Math on float values. Every call here builds on every board: it avoids the
;; double-precision libm functions that math_functions_double_precision uses.
(ns io.github.jabrena.juno.language.clojure.math-functions-single-precision
  (:gen-class)
  (:import [io.github.jabrena.juno.api Delay]
           [io.github.jabrena.juno.api.io.serial BaudRate Serial]))

(defn- float-helpers []
  (Serial/println "Float helpers")
  (let [reading (float -2.5)]
    (Serial/print "abs(-2.5): ")
    (Serial/println (Math/abs reading))
    (Serial/print "min(-2.5, 1.5): ")
    (Serial/println (Math/min reading (float 1.5)))
    (Serial/print "max(-2.5, 1.5): ")
    (Serial/println (Math/max reading (float 1.5)))
    (Serial/print "clamp(-2.5, 0, 1): ")
    (Serial/println (Math/clamp reading (float 0) (float 1)))
    (Serial/print "signum(-2.5): ")
    (Serial/println (Math/signum reading))))

(defn- rounding []
  (Serial/println "Rounding")
  (let [reading (float -2.5)]
    (Serial/print "round(-2.5): ")
    (Serial/println (Math/round reading))
    (Serial/print "floor(-2.5): ")
    (Serial/println (Math/floor (double reading)))
    (Serial/print "ceil(-2.5): ")
    (Serial/println (Math/ceil (double reading)))
    (Serial/print "sqrt(2): ")
    (Serial/println (float (Math/sqrt 2.0)))))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (Delay/millis 2000)
  (Serial/println "Math functions, single precision demo")
  (float-helpers)
  (rounding))
