(ns arg-vecs
  {:doc "This example searches for defns with more than 7 arguments"}
  (:require [clojure.pprint :as pprint]
            [clojure.spec.alpha :as s]
            [grasp.api :as grasp :refer [grasp]]))

(s/def ::args+body (s/cat :arg-vec vector? :exprs (s/+ any?)))
(s/def ::fn-body (s/alt :single ::args+body :multi (s/+ (s/spec ::args+body))))
(s/def ::defn (s/cat :defn #{'defn} :name symbol? :some-stuff (s/* any?) :fn-body ::fn-body))

(defn arg-vecs [fn-body]
  (case (first fn-body)
    :single [(:arg-vec (second fn-body))]
    :multi (mapv :arg-vec (second fn-body))))

(s/def ::defn-with-large-arg-vec
  (s/and ::defn (fn [m]
                  (let [avs (arg-vecs (:fn-body m))]
                    (some #(> (count %) 7) avs)))))

(def matches (grasp (System/getProperty "java.class.path") ::defn-with-large-arg-vec))

(defn table-row [sexpr]
  (let [conformed (s/conform ::defn sexpr)
        m (meta sexpr)
        m (select-keys m [:file :line :column])]
    (assoc m :name (:name conformed))))

(pprint/print-table (map table-row matches))
