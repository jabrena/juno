;; Demonstrates final classes with constructors and instance fields, and short-lived objects. Clojure's deftype
;; gives the classes: Counter has a mutable field, and Point implements a definterface of long accessors. Moving
;; returns the new coordinates as two longs, because a method cannot return an Object in Juno, and each step builds
;; a fresh Point. Direction is a long constant (0 north, 1 east, 2 south, 3 west), since an enum or record would
;; need Clojure's keyword and map runtime.
(ns io.github.jabrena.juno.language.clojure.objects
  (:gen-class)
  (:import [io.github.jabrena.juno.api Delay]
           [io.github.jabrena.juno.api.io.serial BaudRate Serial]))

(definterface Positioned
  (^long x [])
  (^long y [])
  (^long distanceFromOrigin []))

(deftype Point [^long px ^long py]
  Positioned
  (^long x [_] px)
  (^long y [_] py)
  (^long distanceFromOrigin [_] (+ (Math/abs px) (Math/abs py))))

(defn- moved-x ^long [^long x ^long direction ^long steps]
  (if (== direction 1)
    (+ x steps)
    (if (== direction 3) (- x steps) x)))

(defn- moved-y ^long [^long y ^long direction ^long steps]
  (if (== direction 0)
    (+ y steps)
    (if (== direction 2) (- y steps) y)))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (Delay/millis 2000)
  (Serial/println "Objects demo")
  (let [^Positioned start (Point. 0 0)
        ^Positioned north (Point. (moved-x (.x start) 0 4) (moved-y (.y start) 0 4))
        ^Positioned east (Point. (moved-x (.x north) 1 3) (moved-y (.y north) 1 3))
        ^Positioned position (Point. (moved-x (.x east) 2 6) (moved-y (.y east) 2 6))]
    (Serial/print "x: ") (Serial/println (.x position))
    (Serial/print "y: ") (Serial/println (.y position))
    (Serial/print "distance: ") (Serial/println (.distanceFromOrigin position))
    (loop [index 0
           walker-x 0]
      (if (< index 2000)
        (recur (inc index) (moved-x walker-x 1 1))
        (do
          (Serial/print "walker x: ") (Serial/println walker-x))))))
