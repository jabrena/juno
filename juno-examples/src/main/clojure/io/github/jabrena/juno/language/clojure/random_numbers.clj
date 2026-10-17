;; Demonstrates seeding and generating pseudorandom values within bounded ranges.
(ns io.github.jabrena.juno.language.clojure.random-numbers
  (:gen-class)
  (:import [io.github.jabrena.juno.api Clock Delay Random]
           [io.github.jabrena.juno.api.io.serial BaudRate Serial]))

(defn- dice-rolls []
  (Serial/println "Five dice rolls")
  (loop [roll 1]
    (when (<= roll 5)
      (Serial/print "roll: ")
      (Serial/println (Random/nextInt (int 1) (int 7)))
      (recur (inc roll)))))

(defn- percentage []
  (Serial/print "value from 0 to 99: ")
  (Serial/println (Random/nextInt (int 100))))

(defn- temperature []
  (Serial/print "value from -10 to 40: ")
  (Serial/println (Random/nextInt (int -10) (int 41))))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (Delay/millis 2000)
  (Random/seed (Clock/micros))
  (Serial/println "Random numbers demo")
  (dice-rolls)
  (percentage)
  (temperature))
