;; Ahead-of-time compiles every Clojure namespace under a source root into a class directory, with direct linking so
;; the examples' calls become static methods Juno can lower. Run by juno-examples/pom.xml through exec-maven-plugin:
;;   clojure.main compile-namespaces.clj <source-root> <class-output-dir>
;; A namespace is the file's path under the source root, with '/' read as '.' and '_' in the file name as '-'.
(require 'clojure.string)

(let [[source-root output-dir] *command-line-args*
      root (java.io.File. ^String source-root)
      base (.toPath root)
      sources (->> (file-seq root)
                   (filter #(.endsWith (.getName ^java.io.File %) ".clj")))]
  (binding [*compile-path* output-dir
            *compiler-options* {:direct-linking true}]
    (doseq [^java.io.File file sources
            :let [relative (str (.relativize base (.toPath file)))
                  without-extension (subs relative 0 (- (count relative) 4))
                  namespace (-> without-extension
                                (clojure.string/replace "/" ".")
                                (clojure.string/replace "_" "-"))]]
      (compile (symbol namespace)))))
