(ns grasp.native-test
  (:require
   [grasp.native :as sut]
   [clojure.test :as t :refer [deftest is testing]]
   [clojure.string :as str]))


(deftest file-input-path-is-accepted
  (testing "explicit file path"
    (let [baos (java.io.ByteArrayOutputStream.)
          w (java.io.OutputStreamWriter. baos)]
      (binding [*out* w]
        (sut/-main "src/grasp/native.clj" "-e" "#{'defn}"))
      (let [lines (partition-all 3 (str/split-lines (str baos)))
            lines (for [[fp found _] lines]
                    [(last (str/split fp (re-pattern (System/getProperty "file.separator"))))
                     found])]
        (is (= [["native.clj:52:2" "(defn set-opts! [m]"]
                ["native.clj:59:2" "(defn default-keep-fn"]
                ["native.clj:64:2" "(defn grasp"]
                ["native.clj:71:2" "(defn grasp-string"]
                ["native.clj:78:2" "(defn rsym"]
                ["native.clj:102:2" "(defn eval-spec [spec-string cli-path]"]
                ["native.clj:118:2" "(defn -main [& args]"]]
               lines)))))
  (testing "-p flag"
    (let [baos (java.io.ByteArrayOutputStream.)
          w (java.io.OutputStreamWriter. baos)]
      (binding [*out* w]
        (sut/-main "-p" "src/grasp/native.clj" "-e" "#{'defn}"))
      (let [lines (partition-all 3 (str/split-lines (str baos)))
            lines (for [[fp found _] lines]
                    [(last (str/split fp (re-pattern (System/getProperty "file.separator"))))
                     found])]
        (is (= [["native.clj:52:2" "(defn set-opts! [m]"]
                ["native.clj:59:2" "(defn default-keep-fn"]
                ["native.clj:64:2" "(defn grasp"]
                ["native.clj:71:2" "(defn grasp-string"]
                ["native.clj:78:2" "(defn rsym"]
                ["native.clj:102:2" "(defn eval-spec [spec-string cli-path]"]
                ["native.clj:118:2" "(defn -main [& args]"]]
               lines))))))
