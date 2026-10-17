;; Demonstrates strings through direct JDK interop. Clojure's str and sequence helpers need runtime classes that
;; are not present on the board, so concatenation and reversal use java.lang.StringBuilder explicitly. The serial
;; output is deliberately compact because Clojure's generated entry-point support and runtime strings must share the
;; classic UNO R3's 2 KiB SRAM.
(ns io.github.jabrena.juno.language.clojure.strings
  (:gen-class)
  (:import [io.github.jabrena.juno.api Delay]
           [io.github.jabrena.juno.api.io.serial BaudRate Serial]))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (Delay/millis 2000)
  (Serial/println "Strings")
  (let [^String name "Juno"
        count 42
        ^String text (String/valueOf (int count))
        ^StringBuilder characters (StringBuilder. 2)]
    (.append characters (.charAt name 0))
    (.append characters (.charAt name (int (dec (.length name)))))
    (Serial/println name)
    (Serial/println (.length name))
    (Serial/println (.toString characters))
    (Serial/println (.equals name "Juno"))
    (Serial/println (.equals name "Uno"))
    (Serial/println text)
    (let [^StringBuilder greeting (StringBuilder. 8)]
      (.append greeting name)
      (.append greeting ":")
      (loop [index 0]
        (when (< index (.length text))
          (.append greeting (.charAt text (int index)))
          (recur (inc index))))
      (Serial/println (.toString greeting)))
    (let [^StringBuilder reversed (StringBuilder. (.length name))]
      (loop [index (dec (.length name))]
        (when (>= index 0)
          (.append reversed (.charAt name (int index)))
          (recur (dec index))))
      (Serial/println (.toString reversed)))))
