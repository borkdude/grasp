(ns grasp.api
  (:refer-clojure :exclude [*file*])
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
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

(defn- init []
  (sci/init {;; never load namespaces
             :load-fn (fn [_] "")
             :readers (fn [x]
                        ;; TODO: this doesn't seem to work
                        (prn :x x)
                        identity)
             :features #{:clj :cljs}}
            ))

(def ^:private ^:dynamic *ctx* nil)
(def ^:private ^:dynamic *file* nil)

(defn- with-file [sexpr]
  (let [f *file*]
    (if (and f (instance? clojure.lang.IObj sexpr))
      (vary-meta sexpr assoc :file f)
      sexpr)))

(defn- match-sexprs
  [source-tree spec]
  (->> source-tree
       (tree-seq #(and (seqable? %)
                       (not (string? %)))
                 seq)
       (filter #(s/valid? spec %))
       (map with-file)))

(defn- log-error [_ctx reader form cause]
  (binding [*out* *err*]
    (prn
     {:type :error
      :line (sci/get-line-number reader)
      :column (sci/get-column-number reader)
      :file *file*
      :form form
      :cause (when cause (.getMessage ^Throwable cause))})))

(defn- source-name? [s]
  (let [ext (last (str/split s #"\."))]
    (contains? #{"clj" "cljc" "cljs"} ext)))

(defn- source-file? [^java.io.File f]
  (and (.isFile f)
       (let [name (.getName f)]
         (or (source-name? name)
             (str/ends-with? name ".jar")))))

;;;; Public API

(defn grasp-string [s spec]
  (let [ctx (init)
        reader (sci/reader s)]
    (binding [*ctx* ctx]
      (sci/with-bindings {sci/ns @sci/ns}
        (loop [matches []]
          (let [nexpr (try (sci/parse-next ctx reader)
                           (catch Exception _
                             ;; (prn *file* (sci/get-line-number reader) (sci/get-column-number reader))
                             nil))]
            (if (= ::sci/eof nexpr)
              matches
              (let [form
                    (if (seq? nexpr)
                      (cond (= 'ns (first nexpr))
                            (let [ns-form (process-ns ctx nexpr)]
                              (try (sci/eval-form ctx ns-form)
                                   (catch Exception e
                                     (log-error ctx reader ns-form e)))
                              nexpr)
                            (= 'require (first nexpr))
                            (let [req-form (process-require ctx nexpr)]
                              (try (sci/eval-form ctx req-form)
                                   (catch Exception e
                                     (log-error ctx reader nexpr e)))
                              nexpr)
                            :else nexpr)
                      nexpr)
                    matched (match-sexprs form spec)]
                (recur (into matches matched))))))))))

(def ^:private path-separator (System/getProperty "path.separator"))

(defn- classpath? [path]
  (str/includes? path path-separator))

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

(defn grasp [path spec]
  (cond (coll? path)
        (mapcat #(grasp % spec) path)
        (classpath? path)
        (mapcat #(grasp % spec) (str/split path (re-pattern path-separator)))
        :else
        (let [file (io/file path)]
          (cond (.isDirectory file)
                (mapcat #(grasp % spec)
                        (filter source-file? (file-seq file)))
                (str/ends-with? path ".jar")
                (mapcat #(binding [*file* (:file %)]
                           (grasp-string (:source %) spec))
                        (sources-from-jar file))
                :else ;; assume file
                (binding [*file* (.getPath file)]
                  (grasp-string (slurp file) spec))))))

(defn resolve-symbol [sym]
  (p/fully-qualify *ctx* sym))

;;;; Scratch

#_{:clj-kondo/ignore [:redefined-var]}
(comment
  (defn table-row [sexpr]
    (-> (meta sexpr)
        (select-keys [:line :column])
        (assoc :sexpr sexpr)))

  (defn sym-starting-with-a?
    [s] (and (symbol? s)
             (str/starts-with? (str s) "a")))

  (require '[clojure.pprint :as pprint])

  #_(->> (grasp (System/getProperty "java.class.path")
              #{'frequencies})
       (take 2)
       (map (comp #(select-keys % [:file :line]) meta)))
  

  
  (def clojure-core (slurp (io/resource "clojure/core.clj")))
  (s/def ::clause (s/cat :sym symbol? :lists (s/+ list?)))
  ;; find usages of reify with at least two interfaces
  (s/def ::pattern
    (s/cat :reify #{'reify}
           :clauses (s/cat :clause ::clause :clauses (s/+ ::clause))))
  (def matches (grasp-string clojure-core ::pattern))
  (map meta matches)

  (s/def ::pattern (s/cat :fn #{'f/dude}))
  (def code "(require '[foo :as f]) (f/dude)")
  (map meta (grasp-string code ::pattern))

  (s/def ::pattern (s/cat :fn (resolves-to? 'foo/dude)))
  (map meta (grasp-string code ::pattern))

  (grasp-string "(ns foo (:require [bar :refer [f1 f2]])) (f1)"
                (s/cat :fn (resolves-to? 'bar/f1)))

  (->>
   (grasp "/Users/borkdude/Dropbox/dev/clojure/clj-kondo/src"
               (s/cat :fn (resolves-to? 'clojure.set/difference) :rest (s/* any?)))
   (map (juxt identity meta)))

  (->>
   (grasp "/Users/borkdude/Dropbox/dev/clojure/clj-kondo/src"
               (resolves-to? 'clojure.core/juxt))
   (map (juxt identity meta)))

  (->>
   (grasp "/Users/borkdude/git/clojure"
               (resolves-to? 'clojure.set/difference))
   (mapv (juxt identity meta)))
  )
