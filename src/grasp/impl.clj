(ns grasp.impl
  {:no-doc true}
  (:refer-clojure :exclude [cat or seq vec])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :refer [postwalk]]
            [edamame.core :as e]
            [grasp.impl :as impl]
            [sci.core :as sci]
            [sci.impl.parser :as p]))

(set! *warn-on-reflection* true)

(defn decompose-clause [clause]
  (if (symbol? clause)
    {:ns clause}
    (when (seqable? clause)
      (let [clause (if (= 'quote (first clause))
                     (second clause)
                     clause)
            [ns & tail] clause]
        (loop [parsed {:ns ns}
               tail (clojure.core/seq tail)]
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
  (when (clojure.core/seq refer)
    (let [ns-obj (sci/create-ns ns nil)
          env (:env ctx)]
      (run! #(swap! env assoc-in [:namespaces ns %]
                    (sci/new-var % nil {:name %
                                        :ns ns-obj}))
            refer))))

(defn process-ns
  [ctx ns]
  (keep (fn [x]
          (if (seqable? x) ;; for some reason pathom has [:require-macros com.wsscode.pathom.connect] in a vector...
            (let [fx (first x)]
              (when (clojure.core/or
                     (identical? :require fx)
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

(defn process-in-ns [_ctx req]
  (let [quoted (keep-quoted (rest req))
        quoted (map (fn [ns]
                      (list 'quote ns))
                    quoted)]
    (when (clojure.core/seq quoted)
      (list* 'in-ns quoted))))

(defn init []
  (sci/init {;; never load namespaces
             :load-fn (fn [_] "")
             :readers (fn [_x]
                        ;; TODO: this doesn't seem to work
                        ;; (prn :x x)
                        identity)
             :features #{:clj :cljs}}
            ))

(def ^:dynamic *ctx* nil)

(defn with-url [url sexpr]
  (if (and url (instance? clojure.lang.IObj sexpr))
    (vary-meta sexpr assoc :url (str url))
    sexpr))

(defrecord Wrapper [obj])

(defn match-sexprs
  [source-tree spec keep-fn url]
  (->> source-tree
       (tree-seq #(and ;; (do (prn (meta %)) true)
                       (seqable? %)
                       (not (string? %))
                       (not (instance? Wrapper %)))
                 clojure.core/seq)
       (keep #(keep-fn {:spec spec :expr %}))
       (map #(with-url url %))))

(defn log-error [_ctx url reader form cause]
  (binding [*out* *err*]
    (prn
     {:type :error
      :line (sci/get-line-number reader)
      :column (sci/get-column-number reader)
      :url url
      :form form
      :cause (when cause (.getMessage ^Throwable cause))})))

(defn source-name? [s]
  (let [ext (last (str/split s #"\."))]
    (contains? #{"clj" "cljc" "cljs"} ext)))

(defn source-file? [^java.io.File f]
  (and (.isFile f)
       (let [nm (.getName f)]
         (clojure.core/or (source-name? nm)
                          (str/ends-with? nm ".jar")))))

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
  (let [source? (:source opts)
        ctx (init)
        reader (if source?
                 (e/source-reader s)
                 (e/reader s))]
    (binding [*ctx* ctx] ;; grasp-string returns a strict seqable (vector) of
      ;; matches to ensure *ctx* is still bound
      (sci/with-bindings {sci/ns @sci/ns}
        (loop [matches []]
          (let [url (:url opts)
                nexpr (try (sci/parse-next ctx reader
                                           (cond-> {:location? (fn [_] true)}
                                             (:wrap opts)
                                             (assoc :postprocess
                                                    (fn [{:keys [:obj :loc] :as m}]
                                                      (if (iobj? obj)
                                                        (vary-meta obj merge loc)
                                                        (with-meta (->Wrapper obj)
                                                          (cond-> loc
                                                            source? (assoc :source (:source m)))))))
                                             (:source opts)
                                             (assoc :source true)
                                             (:end-location opts)
                                             (assoc :end-location true
                                                    :end-row-key :end-line
                                                    :end-col-key :end-column)))
                           (catch Exception _
                             nil))]
            (if (= ::sci/eof nexpr)
              matches
              (let [form
                    (if (seq? nexpr)
                      (let [fexpr (first nexpr)]
                        (cond (= 'ns fexpr)
                              (let [ns-form (unwrap-all nexpr)
                                    ns-form (process-ns ctx ns-form)]
                                (try (sci/eval-form ctx ns-form)
                                     (catch Exception e
                                       (log-error ctx url reader ns-form e)))
                                nexpr)
                              (= 'require fexpr)
                              (let [req-form (unwrap-all nexpr)
                                    req-form (process-require ctx req-form)]
                                (try (sci/eval-form ctx req-form)
                                     (catch Exception e
                                       (log-error url ctx reader nexpr e)))
                                nexpr)
                              (= 'in-ns fexpr)
                              (let [form (unwrap-all nexpr)
                                    form (process-in-ns ctx form)]
                                (try (sci/eval-form ctx form)
                                     (catch Exception e
                                       (log-error url ctx reader nexpr e)))
                                nexpr)
                              :else nexpr))
                      nexpr)
                    matched (match-sexprs form spec (:keep-fn opts) url)]
                (recur (into matches matched))))))))))

(defn sources-from-jar
  [^java.io.File jar-file]
  (try
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
                {:url (java.net.URL. (str "jar:file:" (.getPath jar-file) "!/" (.getName entry)))
                 :source (slurp (.getInputStream jar entry))}) entries)))
    (catch java.util.zip.ZipException _e
      ;; skip invalid jar files
      [])))

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
                (mapcat #(grasp-string (:source %) spec (assoc opts :url (:url %)))
                        (sources-from-jar file))
                (= "-" path)
                (grasp-string (slurp *in*) spec (assoc opts :url "stdin"))
                (.exists file) ;; assume file
                (grasp-string (slurp file) spec (assoc opts :url (.toURL file)))))))

(defn resolve-symbol [sym]
  (p/fully-qualify *ctx* sym))

;;;; QUERY

(declare expand-query)

(defn quoted? [x]
  (and (seq? x)
       (= 'quote (first x))))

(defn expand-query [x]
  (if (quoted? x)
    #{x}
    x))

(def kws (map keyword (repeatedly gensym)))

(defmacro or [& preds]
  `(clojure.spec.alpha/or ~@(interleave kws
                                        (map expand-query preds))))
(defmacro cat [& preds]
  `(clojure.spec.alpha/cat ~@(interleave kws
                                         (map expand-query preds))))
(defmacro seq [& preds]
  `(clojure.spec.alpha/and seq? (cat ~@preds)))

(defmacro vec [& preds]
  `(clojure.spec.alpha/and vector? (cat ~@preds)))
