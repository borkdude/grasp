(defproject io.github.borkdude/grasp "0.1.4"
  :description "Grep Clojure code using clojure.spec regexes."
  :url "https://github.com/borkdude/grasp"
  :scm {:name "git"
        :url "https://github.com/borkdude/grasp"}
  :license {:name "Eclipse Public License 1.0"
            :url "http://opensource.org/licenses/eclipse-1.0.php"}
  :source-paths ["src"]
  :dependencies [[org.clojure/clojure "1.11.0-rc1"]
                 [org.babashka/sci "0.3.2"]]
  :plugins [[lein-codox "0.10.7"]
            [lein-cloverage "1.2.2"]]
  :codox {:output-path "gh-pages"}
  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass
                                    :sign-releases false}]])
