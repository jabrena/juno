;; Demonstrates the double-precision java.lang.Math functions: powers and roots, exponentials and logarithms, and
;; trigonometry. Double-precision libm is only on the UNO R4 WiFi and UNO Q toolchains. Math/E and Math/PI are written
;; as their literal values: Juno does not read JDK static fields, which javac would have folded into constants.
(ns io.github.jabrena.juno.language.clojure.math-functions-double-precision
  ;; @Board on the class: the :name metadata is written as a class annotation by gen-class.
  (:gen-class :name ^{io.github.jabrena.juno.annotations.Board [io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi
                                                               io.github.jabrena.juno.annotations.ArduinoUnoQ]}
              io.github.jabrena.juno.language.clojure.math_functions_double_precision)
  (:import [io.github.jabrena.juno.api Delay]
           [io.github.jabrena.juno.api.io.serial BaudRate Serial]))

(defn- powers-and-roots []
  (Serial/println "Powers and roots")
  (Serial/print "pow(2, 10): ")
  (Serial/println (Math/pow 2.0 10.0))
  (Serial/print "sqrt(2): ")
  (Serial/println (Math/sqrt 2.0))
  (Serial/print "cbrt(27): ")
  (Serial/println (Math/cbrt 27.0))
  (Serial/print "hypot(3, 4): ")
  (Serial/println (Math/hypot 3.0 4.0))
  (Serial/print "exp(1): ")
  (Serial/println (Math/exp 1.0))
  (Serial/print "log(E): ")
  (Serial/println (Math/log 2.718281828459045))
  (Serial/print "log10(1000): ")
  (Serial/println (Math/log10 1000.0)))

(defn- trigonometry []
  (Serial/println "Trigonometry every 45 degrees")
  (loop [degrees 0]
    (when (<= degrees 180)
      (let [radians (Math/toRadians (double degrees))]
        (Serial/print "sin(")
        (Serial/print degrees)
        (Serial/print "): ")
        (Serial/println (Math/sin radians))
        (Serial/print "cos(")
        (Serial/print degrees)
        (Serial/print "): ")
        (Serial/println (Math/cos radians)))
      (recur (+ degrees 45))))
  (Serial/print "atan2(1, 1) deg: ")
  (Serial/println (Math/toDegrees (Math/atan2 1.0 1.0)))
  (Serial/print "PI: ")
  (Serial/println 3.141592653589793))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (Delay/millis 2000)
  (Serial/println "Math functions, double precision demo")
  (powers-and-roots)
  (trigonometry))
