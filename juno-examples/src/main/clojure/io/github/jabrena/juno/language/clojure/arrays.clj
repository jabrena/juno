;; Demonstrates int arrays: creation, element access, length, and array parameters. An array parameter has no
;; known length, so each function takes the element count. The double, long and two-dimensional arrays of the Java
;; example are not ported yet.
(ns io.github.jabrena.juno.language.clojure.arrays
  (:gen-class)
  (:import [io.github.jabrena.juno.api Delay]
           [io.github.jabrena.juno.api.io.serial BaudRate Serial]))

(defn- sum-of ^long [^ints values ^long count]
  (loop [index 0
         total 0]
    (if (< index count)
      (recur (inc index) (+ total (aget values index)))
      total)))

(defn- max-of ^long [^ints values ^long count]
  (loop [index 1
         largest (long (aget values 0))]
    (if (< index count)
      (recur (inc index) (let [value (long (aget values index))]
                           (if (> value largest) value largest)))
      largest)))

(defn- reverse-in-place [^ints values ^long count]
  (loop [left 0
         right (dec count)]
    (when (< left right)
      (let [swap (aget values left)]
        (aset values left (aget values right))
        (aset values right swap))
      (recur (inc left) (dec right)))))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (Delay/millis 2000)
  (Serial/println "Arrays demo")
  (let [^ints numbers (int-array 5)
        count 5]
    (loop [index 0]
      (when (< index count)
        (aset numbers index (int (* (inc index) 10)))
        (recur (inc index))))
    (Serial/print "length: ") (Serial/println count)
    (Serial/print "first: ") (Serial/println (aget numbers 0))
    (Serial/print "last: ") (Serial/println (aget numbers (dec count)))
    (Serial/print "sum: ") (Serial/println (sum-of numbers count))
    (Serial/print "max: ") (Serial/println (max-of numbers count))
    (reverse-in-place numbers count)
    (Serial/print "after reverse, first: ") (Serial/println (aget numbers 0))))
