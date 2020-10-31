(ns grasp.impl
  {:no-doc true}
  (:refer-clojure :exclude [*file*])
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.walk :refer [postwalk]]
            [grasp.impl :as impl]
            [sci.core :as sci]
            [sci.impl.parser :as p]))

(defn decompose-clause [clause]
  (if (symbol? clause)
    {:ns clause}
    (when (seqable? clause)
      (let [clause (if (= 'quote (first clause))
                     (second clause)
                     clause)
            [ns & tail] clause]
        (loop [parsed {:ns ns}
               tail (seq tail)]
          (if tail
            (let [ftail (first tail)]
              (case ftail
                :as (recur (assoc parsed :as (second tail))
                           (nnext tail))
                (:refer :refer-macros)
                (let [refer (second tail)]
                  (if (seqable? refer)
                    (recur (assoc parsed :refer (second tail))
                           (nnext tail))
                    (recur parsed (nnext tail))))
                ;; default
                (recur parsed
                       (nnext tail))))
            parsed))))))

(defn recompose-clause [{:keys [:ns :as :refer]}]
  [ns :as as :refer refer])

(defn stub-refers [ctx {:keys [:ns :refer]}]
  (when (seq refer)
    (let [ns-obj (sci/create-ns ns nil)
          env (:env ctx)]
      (run! #(swap! env assoc-in [:namespaces ns %]
                    (sci/new-var % nil {:name %
                                        :ns ns-obj}))
            refer))))

(defn process-ns
  [ctx ns]
  (keep (fn [x]
          (if (seq? x)
            (let [fx (first x)]
              (when (or (identical? :require fx)
                        (identical? :require-macros fx))
                (let [decomposed (keep decompose-clause (rest x))
                      recomposed (map recompose-clause decomposed)]
                  (run! #(stub-refers ctx %) decomposed)
                  (list* :require recomposed))))
            x))
        ns))

(defn keep-quoted [clauses]
  (keep (fn [clause]
          (when (and (seq? clause) (= 'quote (first clause)))
            (second clause)))
        clauses))

(defn process-require [ctx req]
  (let [quoted (keep-quoted (rest req))
        decomposed (map decompose-clause quoted)]
    (run! #(stub-refers ctx %) decomposed)
    (list* 'require (map (fn [clause]
                           (list 'quote (recompose-clause clause)))
                         decomposed))))

(defn init []
  (sci/init {;; never load namespaces
             :load-fn (fn [_] "")
             :readers (fn [x]
                        ;; TODO: this doesn't seem to work
                        (prn :x x)
                        identity)
             :features #{:clj :cljs}}
            ))

(def ^:dynamic *ctx* nil)
(def ^:dynamic *file* nil)

(defn with-file [sexpr]
  (let [f *file*]
    (if (and f (instance? clojure.lang.IObj sexpr))
      (vary-meta sexpr assoc :file f)
      sexpr)))

(defrecord Wrapper [obj])

(defn match-sexprs
  [source-tree spec]
  (->> source-tree
       (tree-seq #(and (seqable? %)
                       (not (string? %))
                       (not (instance? Wrapper %)))
                 seq)
       (filter #(s/valid? spec %))
       (map with-file)))

(defn log-error [_ctx reader form cause]
  (binding [*out* *err*]
    (prn
     {:type :error
      :line (sci/get-line-number reader)
      :column (sci/get-column-number reader)
      :file *file*
      :form form
      :cause (when cause (.getMessage ^Throwable cause))})))

(defn source-name? [s]
  (let [ext (last (str/split s #"\."))]
    (contains? #{"clj" "cljc" "cljs"} ext)))

(defn source-file? [^java.io.File f]
  (and (.isFile f)
       (let [name (.getName f)]
         (or (source-name? name)
             (str/ends-with? name ".jar")))))

(defn iobj? [x]
  (instance? clojure.lang.IObj x))

(defn unwrap [node]
  (if (instance? Wrapper node)
    (:obj node)
    node))

(defn unwrap-all [node]
  (postwalk unwrap node))

(defn grasp-string
  [s spec opts]
  (let [ctx (init)
        reader (sci/reader s)]
    (binding [*ctx* ctx]
      (sci/with-bindings {sci/ns @sci/ns}
        (loop [matches []]
          (let [nexpr (try (sci/parse-next ctx reader
                                           (if (:wrap opts)
                                             {:postprocess
                                              (fn [{:keys [:obj :loc]}]
                                                (if (iobj? obj)
                                                  (vary-meta obj merge loc)
                                                  (with-meta (->Wrapper obj) loc)))}
                                             nil))
                           (catch Exception _
                             ;; (prn *file* (sci/get-line-number reader) (sci/get-column-number reader))
                             nil))]
            (if (= ::sci/eof nexpr)
              matches
              (let [form
                    (if (seq? nexpr)
                      (cond (= 'ns (first nexpr))
                            (let [ns-form (unwrap-all nexpr)
                                  ns-form (process-ns ctx ns-form)]
                              (try (sci/eval-form ctx ns-form)
                                   (catch Exception e
                                     (log-error ctx reader ns-form e)))
                              nexpr)
                            (= 'require (first nexpr))
                            (let [req-form (unwrap-all nexpr)
                                  req-form (process-require ctx req-form)]
                              (try (sci/eval-form ctx req-form)
                                   (catch Exception e
                                     (log-error ctx reader nexpr e)))
                              nexpr)
                            :else nexpr)
                      nexpr)
                    matched (match-sexprs form spec)]
                (recur (into matches matched))))))))))

(defn sources-from-jar
  [^java.io.File jar-file]
  (with-open [jar (java.util.jar.JarFile. jar-file)]
    (let [entries (enumeration-seq (.entries jar))
          entries (filter (fn [^java.util.jar.JarFile$JarFileEntry x]
                            (let [nm (.getName x)]
                              (and (not (.isDirectory x)) (source-name? nm)))) entries)]
      ;; Important that we close the `JarFile` so this has to be strict see GH
      ;; issue #542. Maybe it makes sense to refactor loading source using
      ;; transducers so we don't have to load the entire source of a jar file in
      ;; memory at once?
      (mapv (fn [^java.util.jar.JarFile$JarFileEntry entry]
              {:file (.getName entry)
               :source (slurp (.getInputStream jar entry))}) entries))))

(def path-separator (System/getProperty "path.separator"))

(defn classpath? [path]
  (str/includes? path path-separator))

(defn grasp [path spec opts]
  (cond (coll? path)
        (mapcat #(grasp % spec opts) path)
        (classpath? path)
        (mapcat #(grasp % spec opts) (str/split path (re-pattern path-separator)))
        :else
        (let [file (io/file path)]
          (cond (.isDirectory file)
                (mapcat #(grasp % spec opts)
                        (filter source-file? (file-seq file)))
                (str/ends-with? path ".jar")
                (mapcat #(binding [*file* (:file %)]
                           (grasp-string (:source %) spec opts))
                        (sources-from-jar file))
                :else ;; assume file
                (binding [*file* (.getPath file)]
                  (grasp-string (slurp file) spec opts))))))

(defn resolve-symbol [sym]
  (p/fully-qualify *ctx* sym))

;;;; QUERY

(declare expand-query)

(defn expand-cat [[_$cat & args]]
  (let [args (map expand-query args)]
    (s/cat-impl (take (count args) (repeat ':_)) args args)))

(defn expand-query [query]
  (cond (seq? query)
    (let [f (first query)]
      (case f
        $cat (expand-cat query)
        query))
    (symbol? query) #{query}
    :else query))

(defn query* [query]
  (expand-query query))

(defn query [query-string]
  (let [parsed (sci/parse-next (init) (sci/reader query-string))]
    (query* parsed)))



