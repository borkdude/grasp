(ns grasp.api
  (:refer-clojure :exclude [*file*])
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [sci.core :as sci]
            [sci.impl.parser :as p]))

(defn stub-refers [ctx clause]
  (when (seqable? clause)
    (let [clause (if (= 'quote (first clause)) (second clause) clause)
          ns (first clause)
          ns-obj (sci/create-ns ns nil)
          env (:env ctx)
          clause* (drop-while #(not= :refer %) clause)
          refers (when (= :refer (first clause*))
                   (let [refers (second clause*)]
                     (if (seqable? refers)
                       refers
                       nil #_(prn clause))))]
      (run! #(swap! env assoc-in [:namespaces ns %]
                    (sci/new-var % nil {:name %
                                        :ns ns-obj}))
            refers))))

(defn process-require-macros [ctx clause]
  (let [rclause (rest clause)]
    (run! #(stub-refers ctx %) rclause)
    (cons :require rclause)))

(defn process-ns
  [ctx ns]
  (keep (fn [x]
          (if (seq? x)
            (cond (= :require-macros (first x)) (process-require-macros ctx x)
                  (= :require (first x))
                  (do (run! #(stub-refers ctx %) (rest x))
                      x)
                  ;; ignore all the rest
                  )
            x))
        ns))

(defn process-require
  [ctx req-form]
  (let [quoted (filter (fn [x]
                         (and (seq? x)
                              (= 'quote (first x))))
                       (rest req-form))]
    (run! #(stub-refers ctx %) quoted)
    (cons (first req-form) quoted)))

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

(defn- eval-error [_ctx reader form cause]
  (throw (ex-info (str form)
                  {:line (sci/get-line-number reader)
                   :column (sci/get-column-number reader)
                   :file *file*}
                  cause)))

(defn- source-name? [s]
  (let [ext (last (str/split s #"\."))]
    (contains? #{"clj" "cljc" "cljs"} ext)))

(defn- source-file? [^java.io.File f]
  (and (.isFile f)
       (source-name? (.getName f))))

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
                                     (eval-error ctx reader ns-form e)))
                              nexpr)
                            (= 'require (first nexpr))
                            (let [req-form (process-require ctx nexpr)]
                              (try (sci/eval-form ctx req-form)
                                   (catch Exception e
                                     (eval-error ctx reader nexpr e)))
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

(defn resolves-to? [fqs]
  (fn [sym]
    (when (symbol? sym)
      (= fqs (p/fully-qualify *ctx* sym)))))

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
