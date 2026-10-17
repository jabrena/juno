;; Demonstrates immutable local bindings, primitive values, and nested lexical scope.
(ns io.github.jabrena.juno.language.clojure.variables
  (:gen-class)
  (:import [io.github.jabrena.juno.api Delay]
           [io.github.jabrena.juno.api.io.serial BaudRate Serial]))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (Delay/millis 2000)

  (let [step 5
        board "UNO R4 WiFi"
        active (< 1 2)
        original-counter 10
        distance 123456789012
        temperature 23.5
        voltage 3.3]
    (Serial/println "Variables demo")
    (Serial/print "board: ")
    (Serial/println board)
    (Serial/print "active: ")
    (Serial/println (if active "true" "false"))
    (Serial/print "original counter: ")
    (Serial/println original-counter)

    (let [updated-counter (+ original-counter step)]
      (Serial/print "updated counter: ")
      (Serial/println updated-counter)
      (Serial/print "distance: ")
      (Serial/println distance)
      (Serial/print "temperature: ")
      (Serial/println temperature)
      (Serial/print "voltage: ")
      (Serial/println voltage)
      (when active
        (let [doubled-counter (* updated-counter 2)]
          (Serial/print "block-scoped value: ")
          (Serial/println doubled-counter))))))
