(ns arg-vecs
  {:doc "This example searches for defns with more than 7 arguments"}
  (:require [clojure.pprint :as pprint]
            [clojure.spec.alpha :as s]
            [grasp.api :as grasp :refer [grasp]]))

(s/def ::args+body (s/cat :arg-vec vector? :exprs (s/* any?)))
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

(defn keep-fn [{:keys [spec expr uri]}]
  (let [conformed (s/conform spec expr)]
    (when-not (s/invalid? conformed)
      {:var-name (grasp/resolve-symbol (second expr))
       :expr expr
       :uri uri})))

(def matches
  (grasp (System/getProperty "java.class.path") ::defn-with-large-arg-vec {:keep-fn keep-fn}))

(defn table-row [{:keys [var-name expr]}]
  (let [m (meta expr)
        m (select-keys m [:file :line :column])]
    (assoc m :name var-name)))

(pprint/print-table (map table-row matches))
