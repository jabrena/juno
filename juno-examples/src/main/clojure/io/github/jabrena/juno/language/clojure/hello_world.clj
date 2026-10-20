(ns io.github.jabrena.juno.language.clojure.hello-world
  (:gen-class)
  (:import [io.github.jabrena.juno.hardware.serial BaudRate Serial]))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (Serial/println "Hello, World!"))
