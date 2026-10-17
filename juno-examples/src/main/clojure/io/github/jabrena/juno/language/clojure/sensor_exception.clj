;; A custom exception class: a RuntimeException subclass with a message constructor.
(ns io.github.jabrena.juno.language.clojure.sensor-exception
  (:gen-class
   :name io.github.jabrena.juno.language.clojure.SensorException
   :extends java.lang.RuntimeException
   :constructors {[String] [String]}
   ;; No main: this class is an exception type, not an example entry point.
   :main false))
