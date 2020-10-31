(ns grasp.native
  (:gen-class)
  (:require
   [clojure.string :as str]
   [clojure.tools.cli :refer [parse-opts]]
   [grasp.impl :as impl]
   [grasp.impl.spec :as s]
   [sci.core :as sci]))

(set! *warn-on-reflection* true)

(def sns (sci/create-ns 'clojure.spec.alpha nil))

(def spec-ns
  {'and (sci/copy-var s/and sns)
   'cat (sci/copy-var s/cat sns)
   '* (sci/copy-var s/* sns)
   '? (sci/copy-var s/? sns)
   '+ (sci/copy-var s/+ sns)
   })

(def ins (sci/create-ns 'grasp.impl.spec nil))

(def impl-ns
  {'and-spec-impl (sci/copy-var s/and-spec-impl ins)
   'cat-impl (sci/copy-var s/cat-impl ins)
   'rep-impl (sci/copy-var s/rep-impl ins)
   'rep+impl (sci/copy-var s/rep+impl ins)
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
        arg-count (count args)
        options (:options parsed)
        [path-opt spec-opt] [(:path options) (:spec options)]
        spec (or spec-opt (case arg-count
                            1 (first args) ;; when no spec-opt, this must be the
                                           ;; spec, since path is optional
                            2 (second args)
                            (throw (ex-info "No spec provided." parsed))))
        path (or path-opt (case arg-count
                            2 (first args)
                            1 (when-not spec-opt ;; spec was provided via arg
                                (first args))
                            "."))
        wrap (:wrap options)
        matches (impl/grasp path (eval-spec spec) {:valid-fn s/valid?
                                                   :wrap wrap})
        matches (map (fn [m] (assoc (meta m) :sexpr m)) matches)
        batches (partition-by :uri matches)]
    (doseq [batch batches
            :let [uri (:url (first batch))
                  lines (when uri
                          (-> (slurp uri)
                              str/split-lines))]
            m batch]
      (let [{:keys [:line :end-line :column]} m]
        (when lines
          (let [snippet (subvec lines (dec line) end-line)
                snippet (str/join "\n" snippet)]
            (println (str uri ":" line ":" column "\n" snippet))
            (println)))))))

