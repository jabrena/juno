;; Demonstrates the same operations as the Java lambda example through explicit functional-interface adapters.
;; Each operation is a deftype of IntOperation, and a captured value becomes a constructor field. Plain Clojure
;; functions require runtime adapter shapes outside Juno's closed-world model; reify also attaches a source-location
;; map that Juno does not model.
(ns io.github.jabrena.juno.language.clojure.lambdas
  (:gen-class)
  (:import [io.github.jabrena.juno.api.io.serial BaudRate Serial]))

(definterface IntOperation
  (^long applyOp [^long value]))

(defn- twice ^long [^long value]
  (* value 2))

(deftype Square []
  IntOperation
  (^long applyOp [_ ^long value] (* value value)))

(deftype AddCaptured [^long captured-amount]
  IntOperation
  (^long applyOp [_ ^long value] (+ value captured-amount)))

(deftype TwiceReference []
  IntOperation
  (^long applyOp [_ ^long value] (twice value)))

(deftype BoundOffset [^long offset]
  IntOperation
  (^long applyOp [_ ^long value] (+ value offset)))

(defn- apply-operation ^long [^IntOperation operation ^long value]
  (.applyOp operation value))

(defn -main [& _args]
  (Serial/begin BaudRate/BAUD_115200)
  (let [result (+ (apply-operation (Square.) 2)
                  (apply-operation (AddCaptured. 2) 1)
                  (apply-operation (TwiceReference.) 2)
                  (apply-operation (BoundOffset. 3) 3))]
    (Serial/print "Lambda result: ")
    (Serial/println result)))
