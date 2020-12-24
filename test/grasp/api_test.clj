(ns grasp.api-test
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
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
    (is (= [{:line 2, :column 40}
            {:line 3, :column 2}
            {:line 4, :column 2}
            {:line 5, :column 2}]
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
  (is  (= '({:line 6, :column 13}
            {:line 7, :column 13})
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
  (is  (= '({:line 1, :column 1})
          (map meta (grasp-string "(merge nil)"
                                  (s/cat :_ #{'merge}
                                         :_ (fn [x]
                                              (nil? (unwrap x))))
                                  {:wrap true})))))
(deftest macro-test
  (is  (= '({:line 1, :column 1})
          (map meta (grasp-string "(merge nil)"
                                  (g/cat 'merge
                                         (fn [x]
                                           (nil? (unwrap x))))
                                  {:wrap true})))))
(deftest *-test
  (is  (= '({:line 1, :column 1})
          (map meta (grasp-string "[foo 1 2 3]"
                                  (g/vec 'foo
                                         g/*))))))

(deftest source-test
  (let [matches (g/grasp-string "#(+ % %2)"
                                (fn [x] (and (seq? x) (= 'fn* (first x)) (> (count (second x)) 1)
                                             (str/starts-with? (:source (meta x)) "#(")))
                                {:source true})]
    (is (= "#(+ % %2)" (:source (meta (first matches)))))))

(deftest in-ns-test
  (let [matches (g/grasp-string "(in-ns 'foo) ::foo"
                                (fn [x] (= :foo/foo (unwrap x)))
                                {:wrap true
                                 :source true})]
    (is (= '({:line 1, :column 14, :source "::foo"})
           (map meta matches)))))
