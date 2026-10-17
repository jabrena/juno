;; Demonstrates closed-world interface dispatch with two reachable implementations. Clojure has no boolean
;; primitive hint, so the choice is a long flag: 1 selects Half, 0 selects Doubled.
(ns io.github.jabrena.juno.language.clojure.interfaces
  (:gen-class)
  (:import [io.github.jabrena.juno.api Delay]
           [io.github.jabrena.juno.api.io Gpio]
           [io.github.jabrena.juno.api.io.serial BaudRate Serial]))

(definterface Scale
  (^long applyScale [^long value]))

(deftype Half []
  Scale
  (^long applyScale [_ ^long value] (quot value 2)))

(deftype Doubled []
  Scale
  (^long applyScale [_ ^long value] (* value 2)))

(defn- choose [^long high-input]
  (if (== high-input 1) (Half.) (Doubled.)))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (let [^Scale scale (choose (if (> (Gpio/analogRead (int 0)) 512) 1 0))
        result (.applyScale scale 100)]
    (loop []
      (Serial/print "Interface dispatch result: ")
      (Serial/println result)
      (Delay/millis 1000)
      (recur))))
