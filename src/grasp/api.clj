(ns grasp.api
  (:refer-clojure :exclude [cat or seq vec * +])
  (:require [clojure.spec.alpha :as s]
            [grasp.impl :as impl]))

(defn grasp
  ([path-or-paths spec] (grasp path-or-paths spec nil))
  ([path-or-paths spec opts]
   (impl/grasp path-or-paths spec (assoc opts :valid-fn s/valid?))))

(defn grasp-string
  ([string spec] (grasp-string string spec nil))
  ([string spec opts]
   (impl/grasp-string string spec (assoc opts :valid-fn s/valid?))))

(defn resolve-symbol [sym]
  (impl/resolve-symbol sym))

(defn unwrap [wrapped]
  (impl/unwrap wrapped))

(defn rsym 
  "Spec for a symbol equal to the provided fully-qualified `sym`
  so that you don't need to run it through [[resolve-symbol]]."
  [sym]
  (s/and symbol? (comp #{sym} grasp.api/resolve-symbol)))

(def ^:macro cat @#'impl/cat)
(def ^:macro or  @#'impl/or)
(def ^:macro seq @#'impl/seq)
(def ^:macro vec @#'impl/vec)

(def * (s/* any?))
(def ? (s/? any?))
(def + (s/+ any?))
