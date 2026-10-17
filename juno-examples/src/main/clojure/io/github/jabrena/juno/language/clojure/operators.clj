;; Demonstrates arithmetic, bitwise, shift, and unary operators. Values are longs;
;; the unsigned shift masks to 32 bits first, so it prints the int result the Java example prints.
(ns io.github.jabrena.juno.language.clojure.operators
  (:gen-class)
  (:import [io.github.jabrena.juno.api Delay]
           [io.github.jabrena.juno.api.io.serial BaudRate Serial]))

(defn- arithmetic [^long left ^long right]
  (Serial/println "Arithmetic operators")
  (Serial/print "12 + 5 = ")
  (Serial/println (+ left right))
  (Serial/print "12 - 5 = ")
  (Serial/println (- left right))
  (Serial/print "12 * 5 = ")
  (Serial/println (* left right))
  (Serial/print "12 / 5 = ")
  (Serial/println (quot left right))
  (Serial/print "12 % 5 = ")
  (Serial/println (rem left right)))

(defn- bitwise [^long left ^long right]
  (Serial/println "Bitwise operators")
  (Serial/print "12 & 5 = ")
  (Serial/println (bit-and left right))
  (Serial/print "12 | 5 = ")
  (Serial/println (bit-or left right))
  (Serial/print "12 ^ 5 = ")
  (Serial/println (bit-xor left right)))

(defn- shifts [^long left]
  (Serial/println "Shift operators")
  (Serial/print "12 << 2 = ")
  (Serial/println (bit-shift-left left 2))
  (Serial/print "12 >> 1 = ")
  (Serial/println (bit-shift-right left 1))
  (Serial/print "-16 >>> 2 = ")
  (Serial/println (unsigned-bit-shift-right (bit-and -16 0xFFFFFFFF) 2)))

(defn- unary [^long left]
  (Serial/println "Unary operators")
  (let [value (inc left)]
    (Serial/print "inc 12 = ")
    (Serial/println value)
    (let [value-after (dec value)]
      (Serial/print "dec 13 = ")
      (Serial/println value-after)
      (Serial/print "-12 = ")
      (Serial/println (- left)))))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (Delay/millis 2000)
  (Serial/println "Using operators demo")
  (arithmetic 12 5)
  (bitwise 12 5)
  (shifts 12)
  (unary 12))
