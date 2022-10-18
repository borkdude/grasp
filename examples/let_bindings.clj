(ns let-bindings
  {:doc "This example counts how many let bindings are typically used"}
  (:require [clojure.pprint :as pprint]
            [clojure.spec.alpha :as s]
            [grasp.api :as grasp :refer [grasp]]))

(s/def ::let (s/cat :let #{'let} :bindings vector? :body (s/* any?)))

(defn keep-fn [{:keys [spec expr uri]}]
  (let [conformed (s/conform spec expr)]
    (when-not (s/invalid? conformed)
      {:expr expr
       :uri uri})))

(def matches
  (grasp
   #_"/Users/borkdude/.m2/repository"
   (System/getProperty "java.class.path") ::let {:keep-fn keep-fn}))

(defn table-row [{:keys [expr uri]}]
  (let [m (meta expr)
        m (select-keys m [:line :column])]
    (assoc m :count (/ (count (second expr)) 2) :uri uri)))

(def rows (map table-row matches))

(def counts (sort-by second > (frequencies (map :count rows))))

;; weird, but these are oddities:
#_(some #(when (= 1/2 (:count %)) %) rows)

(pprint/print-table rows)

