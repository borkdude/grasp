(ns grasp.impl-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :as t :refer [deftest is]]
            [grasp.impl :as impl]))

(deftest query-test
  (is (s/valid? (impl/query "($cat foo bar)") '(foo bar))))
