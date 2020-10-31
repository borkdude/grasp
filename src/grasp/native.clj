(ns grasp.native
  (:gen-class)
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.cli :refer [parse-opts]]
   [grasp.impl :as impl]
   [grasp.impl.spec :as s]
   [sci.core :as sci]))

(def sns (sci/create-ns 'clojure.spec.alpha nil))

(def spec-ns
  {'and (sci/copy-var s/and sns)
   'cat (sci/copy-var s/cat sns)
   '* (sci/copy-var s/* sns)
   '? (sci/copy-var s/? sns)
   })

(def ins (sci/create-ns 'grasp.impl.spec nil))

(def impl-ns
  {'and-spec-impl (sci/copy-var s/and-spec-impl ins)
   'cat-impl (sci/copy-var s/cat-impl ins)
   'rep-impl (sci/copy-var s/rep-impl ins)
   'maybe-impl (sci/copy-var s/maybe-impl ins)})

(def gns (sci/create-ns 'grap.api nil))

(defn eval-spec [spec-string]
   (sci/eval-string spec-string {:aliases {'s 'clojure.spec.alpha}
                                 :bindings {'unwrap (sci/copy-var impl/unwrap gns)}
                                 :namespaces {'clojure.spec.alpha spec-ns
                                             'grasp.impl.spec impl-ns}}))

(def cli-options [["-p" "--path PATH" "The search path"]
                  ["-e" "--spec SPEC" "The spec"]
                  ["-w" "--wrap"]])

(defn -main [& args]
  (let [parsed (parse-opts args cli-options)
        args (:arguments parsed)
        options (:options parsed)
        [path-opt spec-opt] [(:path options) (:spec options)]
        path (or path-opt (when spec-opt (first args)) (second args) ".")
        spec (or spec-opt (when path-opt (first args) (second args)))
        wrap (:wrap options)
        matches (impl/grasp path (eval-spec spec) {:valid-fn s/valid?
                                                   :wrap wrap})]
    (doseq [m matches]
      (let [{:keys [:file :line :column]} (meta m)]
        (when (and file line (.exists (io/file file)))
          (with-open [rdr (io/reader file)]
            (let [s (nth (line-seq rdr) (dec line))]
              (println (str file ":" line ":" column) (str/triml s)))))))))

