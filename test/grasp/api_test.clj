(ns grasp.api-test
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.test :as t :refer [deftest is]]
            [grasp.api :as g :refer [grasp grasp-string unwrap]]))

(def clojure-core (slurp (io/resource "clojure/core.clj")))

(s/def ::clause (s/cat :sym symbol? :lists (s/+ list?)))

(s/def ::reify
  (s/cat :reify #{'reify}
         :clauses (s/cat :clause ::clause :clauses (s/+ ::clause))))

(deftest reify-test
  (let [matches (g/grasp-string clojure-core ::reify)
        locs (map meta matches)
        lines (map :line locs)]
    (is (= 2 (count lines)))
    (is (every? pos? lines))))

(deftest resolve-test
  (let [prog "
(require '[clojure.set :as set :refer [difference]])
(difference #{1 2 3} #{2 3 4})
(set/difference #{1 2 3} #{2 3 4})
(clojure.set/difference #{1 2 3} #{2 3 4})"]
    (is (= [{:line 2, :column 40, :end-line 2, :end-column 50}
            {:line 3, :column 2, :end-line 3, :end-column 12}
            {:line 4, :column 2, :end-line 4, :end-column 16}
            {:line 5, :column 2, :end-line 5, :end-column 24}]
             (->> (grasp-string prog
                                (fn [sym]
                                  (when (symbol? sym)
                                    (= 'clojure.set/difference (g/resolve-symbol sym)))))
                  (map meta))))))

(deftest classpath-test
  (->> (grasp (System/getProperty "java.class.path") #{'frequencies})
       (first)
       (is)))

(deftest keyword-test
  (is  (= '({:line 6, :column 13, :end-line 6, :end-column 27}
            {:line 7, :column 13, :end-line 7, :end-column 38})
          (map meta (grasp-string "
(ns my.cljs.app.views
  (:require [my.cljs.app.subs :as subs]
            [re-frame.core :refer [subscribe]]))

(subscribe [::subs/my-data])
(subscribe [:my.cljs.app.subs/my-data])
"
                                  (fn [x]
                                    (identical? :my.cljs.app.subs/my-data (unwrap x)))
                                  {:wrap true})))))

(deftest nil-test
  (is  (= '({:line 1, :column 1, :end-line 1, :end-column 12})
          (map meta (grasp-string "(merge nil)"
                                  (s/cat :_ #{'merge}
                                         :_ (fn [x]
                                              (nil? (unwrap x))))
                                  {:wrap true})))))
(deftest macro-test
  (is  (= '({:line 1, :column 1, :end-line 1, :end-column 12})
          (map meta (grasp-string "(merge nil)"
                                  (g/cat 'merge
                                         (fn [x]
                                           (nil? (unwrap x))))
                                  {:wrap true})))))
