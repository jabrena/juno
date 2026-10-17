;; Prints the size and range of the integer primitive types. The Java example reads Byte.MIN_VALUE and the like, which
;; javac folds into constants; Clojure has no such folding, so the same text is printed as literals.
(ns io.github.jabrena.juno.language.clojure.primitive-types
  (:gen-class)
  (:import [io.github.jabrena.juno.api.io.serial BaudRate Serial]))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (Serial/println "Primitive types demo")
  (Serial/println "byte: 1 bytes, range: -128 to 127")
  (Serial/println "short: 2 bytes, range: -32768 to 32767")
  (Serial/println "int: 4 bytes, range: -2147483648 to 2147483647"))
