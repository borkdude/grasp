(ns case-symbols
  {:doc "This example searches for usages of case with symbol constants"}
  (:require [clojure.pprint :as pprint]
            [clojure.spec.alpha :as s]
            [grasp.api :as grasp :refer [grasp]]))

(s/def ::case (s/cat :case #{'case} :test any? :body (s/* any?)))

(defn case-body-with-symbols [expr case-body]
  (when (seq (filter symbol? (take-nth 2 case-body)))
    (meta expr) #_(assoc (meta expr) :expr expr)))

(defn keep-fn [{:keys [spec expr uri]}]
  (let [conformed (s/conform spec expr)]
    (when-not (s/invalid? conformed)
      (some-> (case-body-with-symbols expr (:body conformed))
              (assoc :uri uri)))))

(def matches
  (grasp (System/getProperty "java.class.path") ::case {:keep-fn keep-fn}))

(pprint/print-table matches)
