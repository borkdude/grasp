(ns grasp.native
  (:gen-class)
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [grasp.impl :as impl]
   [grasp.impl.spec :as s]
   [sci.core :as sci]))

(def sns (sci/create-ns 'clojure.spec.alpha nil))

(def spec-ns
  {'cat (sci/copy-var s/cat sns)
   '* (sci/copy-var s/* sns)
   })

(def ins (sci/create-ns 'grasp.impl.spec nil))

(def impl-ns
  {'cat-impl (sci/copy-var s/cat-impl sns)
   'rep-impl (sci/copy-var s/rep-impl ins)})

(defn eval-spec [spec-string]
  (sci/eval-string spec-string {:aliases {'s 'clojure.spec.alpha}
                                :namespaces {'clojure.spec.alpha spec-ns
                                             'grasp.impl.spec impl-ns}})
  )

(defn -main [& [path spec opts]]
  (let [matches (impl/grasp path (eval-spec spec) {:valid-fn s/valid?})]
    ;; (run! prn matches #_(map meta matches))
    (doseq [m matches]
      (let [{:keys [:file :line :column]} (meta m)]
        (when (and file line)
          (with-open [rdr (io/reader file)]
            (let [s (nth (line-seq rdr) (dec line))]
              (println (str file ":" line ":" column) (str/triml s)))))))))

