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
   'def (sci/copy-var s/def sns)
   '* (sci/copy-var s/* sns)
   '? (sci/copy-var s/? sns)
   '+ (sci/copy-var s/+ sns)
   })

(def ins (sci/create-ns 'grasp.impl.spec nil))

(def impl-ns
  {'and-spec-impl (sci/copy-var s/and-spec-impl ins)
   'cat-impl (sci/copy-var s/cat-impl ins)
   'def-impl (sci/copy-var s/def-impl ins)
   'rep-impl (sci/copy-var s/rep-impl ins)
   'rep+impl (sci/copy-var s/rep+impl ins)
   'maybe-impl (sci/copy-var s/maybe-impl ins)})

(def gns (sci/create-ns 'grap.api nil))

(defonce opts (atom nil))

(defn set-opts! [m]
  (reset! opts m))

(def grasp-api-ns
  {'unwrap (sci/copy-var impl/unwrap gns)
   'set-opts! (sci/copy-var set-opts! gns)})

(defn eval-spec [spec-string]
   (sci/eval-string spec-string {:aliases {'s 'clojure.spec.alpha}
                                 :bindings grasp-api-ns
                                 :namespaces {'clojure.spec.alpha spec-ns
                                              'grasp.api grasp-api-ns
                                              'grasp.impl.spec impl-ns}}))

(def cli-options [["-p" "--path PATH" "Path with sources"]
                  ["-e" "--expr SPEC" "Eval spec from expr"]
                  ["-f" "--file SPEC" "Read spec from file"]
                  ["-w" "--wrap"]])

(defn -main [& args]
  (let [parsed (parse-opts args cli-options)
        args (:arguments parsed)
        arg-count (count args)
        options (:options parsed)
        [path-opt spec-opt] [(:path options) (or (:expr options)
                                                 (when-let [f (:file options)]
                                                   (slurp f)))]
        spec (or spec-opt (case arg-count
                            1 (first args) ;; when no spec-opt, this must be the
                                           ;; spec, since path is optional
                            2 (second args)
                            (throw (ex-info "No spec provided." parsed))))
        path (or path-opt (case arg-count
                            2 (first args)
                            1 (when spec-opt ;; spec was not provided via arg
                                (first args))
                            "."))
        spec (eval-spec spec)
        spec (or (:spec @opts) spec)
        wrap (or (:wrap options) (some-> @opts :opts :wrap))
        matches (impl/grasp path spec {:valid-fn s/valid?
                                                   :wrap wrap})
        matches (map (fn [m] (assoc (meta m) :sexpr m)) matches)
        batches (partition-by :url matches)]
    (doseq [batch batches
            :let [url (:url (first batch))
                  lines (when url
                          (-> (slurp url)
                              str/split-lines))]
            m batch]
      (let [{:keys [:line :end-line :column]} m]
        (when lines
          (let [snippet (subvec lines (dec line) end-line)
                snippet (str/join "\n" snippet)]
            (println (str url ":" line ":" column "\n" snippet))
            (println)))))))

